package com.tripcompanion.app.data.pdf

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Inflater

/**
 * Pulls the positioned text out of a PDF, with no PDF library.
 *
 * This implements the narrow slice of the format that a generated e-ticket actually uses:
 * Flate-compressed content streams, a ToUnicode CMap mapping glyph codes back to characters,
 * and the text-positioning and text-showing operators. It is not a PDF reader — it cannot
 * render, it ignores clipping and graphics state, it does not resolve the page tree, and it
 * makes no attempt at a scanned document. What it does is answer "what characters are on this
 * page, and where", which is all a ticket parser needs.
 *
 * Hand-rolled rather than taken from a library because the alternative is a multi-megabyte
 * dependency for one screen, and because everything here runs on a plain JVM — the whole
 * extractor is unit-testable against real files without a device.
 *
 * ### Why the text is not simply readable
 *
 * These files embed a subsetted font and address its glyphs by two-byte code, so the bytes in
 * the content stream are not the characters. `00 28` is not `NUL(`; it is the code whose
 * ToUnicode entry says `E`. Two consequences shape this code. Codes must be decoded in pairs
 * through the CMap, and — because the low byte of a code is frequently `0x28` or `0x29`, the
 * ASCII parentheses — the string delimiters appear *inside* strings and are escaped there. A
 * regex over the stream would mistake an escaped delimiter for a real one on the first name
 * containing an `E`, so the stream is tokenized properly instead.
 */
object PdfTextExtractor {

    /**
     * A stream must show text at least twice to be treated as a content stream.
     *
     * Embedded font programs are Flate-compressed too, and they inflate to binary that a
     * tokenizer will happily walk, emitting nonsense. Requiring more than one text block is
     * enough to skip them; a stream that slips through produces text no ticket parser will
     * recognise, so the cost of a false positive is nil.
     */
    private const val MIN_TEXT_BLOCKS = 2

    /** Refuse to inflate more than this from one stream, so a malformed file cannot exhaust memory. */
    private const val MAX_INFLATED_BYTES = 16 * 1024 * 1024

    /**
     * Reads every content stream in [bytes].
     *
     * Never throws for a file it cannot understand: an encrypted PDF, a scan with no text
     * layer, or a JPEG renamed `.pdf` all come back as an empty [PdfText]. Callers decide what
     * to tell the user, and "this ticket has no text in it" is a different message from "this
     * file is broken".
     */
    fun extract(bytes: ByteArray): PdfText {
        val raw = String(bytes, StandardCharsets.ISO_8859_1)
        val decoded = inflateStreams(bytes, raw)

        val cmap = HashMap<Int, String>()
        for (stream in decoded) {
            if (stream.contains("beginbfrange") || stream.contains("beginbfchar")) {
                cmap.putAll(parseToUnicodeCMap(stream))
            }
        }

        val runs = mutableListOf<PdfTextRun>()
        decoded.forEachIndexed { index, stream ->
            if (countOccurrences(stream, "BT") >= MIN_TEXT_BLOCKS) {
                runs += readContentStream(stream, index, cmap)
            }
        }
        return PdfText(runs)
    }

    // ── Streams ──

    /**
     * Every stream in the file, inflated, indexed by position in the file.
     *
     * The list is positional and includes an empty entry for each stream that could not be
     * inflated, so a stream's index is stable no matter what else is in the document. A ticket
     * parser reports which stream it read, and that number has to mean something.
     *
     * The end of a stream is found by letting the inflater stop where the compressed data ends,
     * rather than by searching for the `endstream` keyword: compressed bytes can spell
     * `endstream` by chance, and a truncated stream inflates to nothing.
     */
    private fun inflateStreams(bytes: ByteArray, raw: String): List<String> {
        val out = mutableListOf<String>()
        var searchFrom = 0
        while (true) {
            val keyword = raw.indexOf(STREAM, searchFrom)
            if (keyword < 0) break
            searchFrom = keyword + STREAM.length

            // `endstream` ends in `stream`, so every closing keyword matches too.
            if (keyword >= 3 && raw.startsWith(END_STREAM, keyword - 3)) continue

            var start = keyword + STREAM.length
            if (start < raw.length && raw[start] == '\r') start++
            // The keyword is followed by a newline; anything else is a longer word ending in it.
            if (start >= raw.length || raw[start] != '\n') continue
            start++

            out += inflate(bytes, start) ?: uncompressed(raw, start)

            // Resume after this stream's data rather than inside it: compressed bytes can spell
            // the keyword by chance, and scanning them would invent a stream that is really the
            // middle of another one.
            val close = raw.indexOf(END_STREAM, start)
            if (close > 0) searchFrom = close + END_STREAM.length
        }
        return out
    }

    private const val STREAM = "stream"
    private const val END_STREAM = "endstream"

    private fun inflate(bytes: ByteArray, offset: Int): String? {
        if (offset >= bytes.size) return null
        val inflater = Inflater()
        return try {
            inflater.setInput(bytes, offset, bytes.size - offset)
            val sink = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (!inflater.finished() && sink.size() < MAX_INFLATED_BYTES) {
                val produced = inflater.inflate(buffer)
                if (produced == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    sink.write(buffer, 0, produced)
                }
            }
            sink.takeIf { it.size() > 0 }?.toByteArray()?.toString(StandardCharsets.ISO_8859_1)
        } catch (_: Exception) {
            null
        } finally {
            inflater.end()
        }
    }

    /**
     * A stream stored with no filter, which the spec allows and some generators emit.
     *
     * Only accepted when it looks like drawing instructions, because this is also the path a
     * failed inflate falls down — and handing an embedded JPEG to the tokenizer is pointless.
     */
    private fun uncompressed(raw: String, start: Int): String {
        val end = raw.indexOf("endstream", start)
        if (end < 0) return ""
        val body = raw.substring(start, end)
        return if (countOccurrences(body, "BT") >= MIN_TEXT_BLOCKS) body else ""
    }

    // ── ToUnicode ──

    /**
     * Builds the glyph-code to character map from a ToUnicode CMap stream.
     *
     * Handles both range forms — `<lo> <hi> <dst>`, where the destination increments across the
     * range, and `<lo> <hi> [<d1> <d2> …]`, where each code is listed — plus the single-code
     * `beginbfchar` form. The tickets seen so far use only the first, but the second is common
     * enough in generated PDFs that leaving it out would mean a ticket from a different issuer
     * silently extracting to nothing.
     *
     * Each block is scanned separately: the `<hex> <hex>` pair pattern of a `bfchar` entry also
     * matches the first two thirds of a `bfrange` entry, so scanning the whole stream at once
     * would map every range's low code to its high code.
     */
    private fun parseToUnicodeCMap(stream: String): Map<Int, String> {
        val map = HashMap<Int, String>()

        for (block in Regex("beginbfrange(.*?)endbfrange", RegexOption.DOT_MATCHES_ALL).findAll(stream)) {
            val body = block.groupValues[1]
            for (entry in BF_RANGE.findAll(body)) {
                val low = entry.groupValues[1].toIntOrNull(16) ?: continue
                val high = entry.groupValues[2].toIntOrNull(16) ?: continue
                if (high < low || high - low > MAX_RANGE) continue

                val single = entry.groupValues[3]
                val array = entry.groupValues[4]
                if (single.isNotEmpty()) {
                    val destination = hexToString(single)
                    for (code in low..high) map[code] = shift(destination, code - low)
                } else if (array.isNotEmpty()) {
                    val items = Regex("<([0-9a-fA-F]+)>").findAll(array).map { it.groupValues[1] }.toList()
                    items.forEachIndexed { offset, hex ->
                        if (low + offset <= high) map[low + offset] = hexToString(hex)
                    }
                }
            }
        }

        for (block in Regex("beginbfchar(.*?)endbfchar", RegexOption.DOT_MATCHES_ALL).findAll(stream)) {
            for (entry in BF_CHAR.findAll(block.groupValues[1])) {
                val code = entry.groupValues[1].toIntOrNull(16) ?: continue
                map[code] = hexToString(entry.groupValues[2])
            }
        }
        return map
    }

    private val BF_RANGE = Regex(
        "<([0-9a-fA-F]+)>\\s*<([0-9a-fA-F]+)>\\s*(?:<([0-9a-fA-F]+)>|\\[([^\\]]*)\\])"
    )
    private val BF_CHAR = Regex("<([0-9a-fA-F]+)>\\s*<([0-9a-fA-F]+)>")

    /** A CMap range wider than this is a malformed file, not a font. Guards the expansion loop. */
    private const val MAX_RANGE = 0xFFFF

    /** `"0045"` → `"E"`. Multi-unit destinations become multi-char strings, surrogates included. */
    private fun hexToString(hex: String): String {
        val padded = if (hex.length % 4 == 0) hex else hex.padStart((hex.length / 4 + 1) * 4, '0')
        return buildString {
            for (i in padded.indices step 4) {
                append((padded.substring(i, i + 4).toIntOrNull(16) ?: 0).toChar())
            }
        }
    }

    /** Advances a range destination by [offset], which the spec applies to the last code unit. */
    private fun shift(destination: String, offset: Int): String {
        if (offset == 0 || destination.isEmpty()) return destination
        val last = destination.last().code + offset
        return destination.dropLast(1) + last.toChar()
    }

    // ── Content streams ──

    /**
     * Walks one content stream's operators, emitting a run for every piece of text shown.
     *
     * Only the text subset is interpreted. `BT` resets the text matrix, `Tm` sets it, `Td`/`TD`
     * move to a new line relative to the last line's start, `T*` and the two quote operators
     * advance by the leading, and the four showing operators emit. Everything else — colour,
     * paths, images, XObjects — is skipped by falling through the operand stack.
     *
     * The position recorded for a run is where its *line* starts, not where the glyph lands:
     * this does not track glyph advance widths, since it has no font metrics. That is the
     * reason [PdfText.lines] joins runs that share an x — several showing operators in one
     * positioned block all report the block's origin.
     */
    private fun readContentStream(stream: String, index: Int, cmap: Map<Int, String>): List<PdfTextRun> {
        val runs = mutableListOf<PdfTextRun>()
        val operands = mutableListOf<Token>()
        var x = 0f
        var y = 0f
        var leading = 0f

        fun show(text: String) {
            if (text.isNotBlank()) runs += PdfTextRun(index, round(x), round(y), text)
        }

        fun decodeAll(): String = operands.filterIsInstance<Token.Str>()
            .joinToString("") { decodeCids(it.bytes, cmap) }

        for (token in tokenize(stream)) {
            if (token !is Token.Op) {
                operands += token
                continue
            }
            val numbers = operands.filterIsInstance<Token.Num>().map { it.value }
            when (token.name) {
                "BT" -> { x = 0f; y = 0f }

                "Tm" -> if (numbers.size >= 6) {
                    x = numbers[numbers.size - 2]
                    y = numbers[numbers.size - 1]
                }

                "Td", "TD" -> if (numbers.size >= 2) {
                    val dx = numbers[numbers.size - 2]
                    val dy = numbers[numbers.size - 1]
                    x += dx
                    y += dy
                    if (token.name == "TD") leading = -dy
                }

                "TL" -> numbers.lastOrNull()?.let { leading = it }

                "T*" -> y -= leading

                "Tj", "TJ" -> show(decodeAll())

                // `'` shows on the next line; `"` does the same after setting word and character
                // spacing. Both are rare in generated files but cost two lines to honour.
                "'", "\"" -> {
                    y -= leading
                    show(decodeAll())
                }
            }
            operands.clear()
        }
        return runs
    }

    private fun decodeCids(bytes: IntArray, cmap: Map<Int, String>): String = buildString {
        var i = 0
        while (i + 1 < bytes.size) {
            val code = (bytes[i] shl 8) or bytes[i + 1]
            append(cmap[code] ?: "")
            i += 2
        }
    }

    private fun round(value: Float): Float = kotlin.math.round(value * 10f) / 10f

    // ── Tokenizer ──

    private sealed interface Token {
        data class Num(val value: Float) : Token
        class Str(val bytes: IntArray) : Token
        data class Op(val name: String) : Token
        /** Names, dictionaries, array brackets — kept off the operand stack entirely. */
        object Ignored : Token
    }

    private const val WHITESPACE = " \t\r\n\u0000\u000C"
    private const val DELIMITERS = "()<>[]{}/%"

    /**
     * Splits a content stream into tokens.
     *
     * The only genuinely fiddly part is the literal string, which must be scanned with both
     * escape and nesting awareness — see the class note on why its delimiters turn up inside
     * its own contents.
     */
    private fun tokenize(stream: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < stream.length) {
            val c = stream[i]
            when {
                c in WHITESPACE -> i++

                c == '%' -> {
                    while (i < stream.length && stream[i] != '\n' && stream[i] != '\r') i++
                }

                c == '(' -> {
                    val (bytes, next) = readLiteralString(stream, i + 1)
                    tokens += Token.Str(bytes)
                    i = next
                }

                c == '<' -> if (i + 1 < stream.length && stream[i + 1] == '<') {
                    tokens += Token.Ignored
                    i += 2
                } else {
                    val end = stream.indexOf('>', i + 1).let { if (it < 0) stream.length else it }
                    tokens += Token.Str(hexStringBytes(stream.substring(i + 1, end)))
                    i = end + 1
                }

                c == '>' -> { tokens += Token.Ignored; i += if (stream.startsWith(">>", i)) 2 else 1 }

                c == '[' || c == ']' || c == '{' || c == '}' -> { tokens += Token.Ignored; i++ }

                c == '/' -> {
                    var j = i + 1
                    while (j < stream.length && stream[j] !in WHITESPACE && stream[j] !in DELIMITERS) j++
                    tokens += Token.Ignored
                    i = j
                }

                else -> {
                    var j = i
                    while (j < stream.length && stream[j] !in WHITESPACE && stream[j] !in DELIMITERS) j++
                    if (j == i) { i++; continue }
                    val word = stream.substring(i, j)
                    val number = word.toFloatOrNull()
                    tokens += if (number != null) Token.Num(number) else Token.Op(word)
                    i = j
                }
            }
        }
        return tokens
    }

    /** Reads a `( … )` string from just past its opening paren. Returns its bytes and the index after it. */
    private fun readLiteralString(stream: String, from: Int): Pair<IntArray, Int> {
        val out = ArrayList<Int>()
        var depth = 1
        var i = from
        while (i < stream.length) {
            val c = stream[i]
            when {
                c == '\\' -> {
                    if (i + 1 >= stream.length) { i++; continue }
                    val next = stream[i + 1]
                    val simple = ESCAPES[next]
                    when {
                        simple != null -> { out += simple; i += 2 }

                        next in '0'..'7' -> {
                            var value = 0
                            var digits = 0
                            var j = i + 1
                            while (j < stream.length && digits < 3 && stream[j] in '0'..'7') {
                                value = value * 8 + (stream[j] - '0')
                                j++
                                digits++
                            }
                            out += value and 0xFF
                            i = j
                        }

                        // A backslash at end of line is a line continuation, contributing nothing.
                        next == '\n' -> i += 2
                        next == '\r' -> i += if (stream.startsWith("\r\n", i + 1)) 3 else 2

                        else -> { out += next.code and 0xFF; i += 2 }
                    }
                }

                c == '(' -> { depth++; out += c.code; i++ }

                c == ')' -> {
                    depth--
                    if (depth == 0) return out.toIntArray() to (i + 1)
                    out += c.code
                    i++
                }

                else -> { out += c.code and 0xFF; i++ }
            }
        }
        return out.toIntArray() to stream.length
    }

    private val ESCAPES = mapOf(
        'n' to 10, 'r' to 13, 't' to 9, 'b' to 8, 'f' to 12,
        '(' to 40, ')' to 41, '\\' to 92
    )

    private fun hexStringBytes(hex: String): IntArray {
        val digits = hex.filter { !it.isWhitespace() }
        val padded = if (digits.length % 2 == 0) digits else digits + "0"
        val out = IntArray(padded.length / 2)
        for (i in out.indices) {
            out[i] = padded.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: 0
        }
        return out
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var from = 0
        while (true) {
            val at = text.indexOf(needle, from)
            if (at < 0) return count
            count++
            from = at + needle.length
        }
    }
}
