package com.tripcompanion.app.data.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The PDF reader, held to what it must get right for a ticket to be readable at all.
 *
 * Two halves. The first runs the extractor over the four real e-tickets in
 * `src/test/resources/tickets/` and asserts the structural facts the parser is built on — that
 * the glyph codes decode back to characters, that a row's cells land on their header's x, that
 * runs split by the generator come back joined. The second exercises [PdfText] and
 * [PdfTextLine] on runs built by hand, where a page can be shaped to order.
 *
 * The fixtures are the author's own bookings with every piece of personal data replaced inside
 * the PDF's own content streams, so the names and numbers asserted here are invented.
 *
 * Coordinates appear as literals on purpose. They are measurements of a specific document, and
 * pinning them is the only way a change in the extractor's arithmetic shows up as a failure
 * rather than as a ticket that silently reads one column to the left.
 */
class PdfTextExtractorTest {

    private fun bytes(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/tickets/$name")) {
            "missing fixture /tickets/$name"
        }.readBytes()

    private fun extract(name: String): PdfText = PdfTextExtractor.extract(bytes(name))

    private val fixtures = listOf(
        "mmt_sleeper.pdf",
        "goibibo_kzj_agc.pdf",
        "goibibo_agc_udz.pdf",
        "goibibo_mtj_ngp.pdf"
    )

    // ── Real documents ──

    @Test
    fun `every fixture yields text`() {
        fixtures.forEach { name ->
            val text = extract(name)
            assertFalse("$name extracted nothing", text.isEmpty)
            assertTrue("$name extracted too little to be a ticket", text.runs.size > 100)
        }
    }

    /**
     * The glyph codes really are decoded, and the string lexer really does survive them.
     *
     * `E` is the code `0x0028` in these files, whose low byte is the ASCII `(` — so the byte
     * pair for `E` contains a string delimiter, and this heading contains both an `E` and a
     * literal pair of brackets. If the tokenizer were a regex over the stream it would end the
     * string early and this assertion would be the one that noticed.
     */
    @Test
    fun `a heading with an E and real brackets comes back intact`() {
        fixtures.forEach { name ->
            assertTrue(
                "$name lost the ERS heading",
                extract(name).plainText().contains("Electronic Reservation Slip (ERS)")
            )
        }
    }

    @Test
    fun `nothing undecoded survives into the text`() {
        fixtures.forEach { name ->
            val stray = extract(name).plainText().filter { it != '\n' && it < ' ' }
            assertEquals("$name kept control characters", "", stray)
        }
    }

    /**
     * One stream holds the reservation, and it is found by what it says rather than by where it is.
     *
     * These files draw the page in two streams, and the split is not panel-versus-boilerplate:
     * the terms and conditions begin in the same stream as the reservation and continue into the
     * second. Picking a stream is therefore only the first cut, as the next test shows.
     */
    @Test
    fun `exactly one stream holds the reservation`() {
        fixtures.forEach { name ->
            val text = extract(name)
            assertEquals("$name did not split into two streams", 2, text.streamIndices.size)

            val panel = text.streamContainingAll("PNR", "Booking Status")
            assertNotNull("$name has no single reservation stream", panel)
            assertTrue(
                "$name put everything in one stream",
                panel!!.runs.size < text.runs.size
            )
            assertEquals(
                "$name drew the reservation somewhere other than first",
                text.streamIndices.first(),
                panel.runs.first().stream
            )
        }
    }

    /**
     * The chosen stream is not a clean slice of the ticket, and the parser must not assume it is.
     *
     * `CLERKAGE` is from the refund conditions, several inches below the payment table, and it
     * is in the same stream as the passenger list. Anchoring on markers and column positions is
     * what separates the two; the stream index only narrows the search.
     */
    @Test
    fun `the reservation stream carries boilerplate too`() {
        val panel = extract("mmt_sleeper.pdf").streamContainingAll("PNR", "Booking Status")!!

        assertTrue(panel.plainText().contains("CLERKAGE"))
    }

    /**
     * A table row's cells sit within a column's width of that column's header.
     *
     * This is the whole basis of reading the passenger table: the header line and each passenger
     * line are separate lines that share x positions, so "this passenger's booking status" is
     * "the run on their line nearest the x of the `Booking Status` header". The numbers below
     * are measured off the fixture — the header at 535, the value under it at 538.
     */
    @Test
    fun `a passenger row lines up with its headers`() {
        val panel = extract("mmt_sleeper.pdf").streamContainingAll("PNR", "Booking Status")!!
        val lines = panel.lines()

        val header = lines.first { it.contains("Booking Status") }
        assertEquals("#", header.runs.first().text)
        assertEquals("Name", header.runNear(91f)?.text)
        assertEquals("Age", header.runNear(355f)?.text)
        assertEquals("Gender", header.runNear(429f)?.text)
        assertEquals("Booking Status", header.runNear(535f)?.text)
        assertEquals("Current Status", header.runNear(685f)?.text)

        // The row below it, read through the header's own x positions.
        val first = lines[lines.indexOf(header) + 1]
        assertEquals("1.", first.runs.first().text)
        assertEquals("Ananya Kapoor", first.runNear(91f)?.text)
        assertEquals("21", first.runNear(355f)?.text)
        assertEquals("Female", first.runNear(429f)?.text)
        assertEquals("CNF/S4/8/SU", first.runNear(535f)?.text)
        assertEquals("CNF/S4/8", first.runNear(685f)?.text)
    }

    /**
     * A label and its value drawn as several show operators come back as one run.
     *
     * The generator emits `Start Date*`, the space, and each part of the date separately, all
     * after a single positioning operator, so every piece records the same x. Without the join
     * a parser looking for a date beside a label would find five fragments and no date.
     */
    @Test
    fun `runs drawn in pieces come back joined`() {
        val panel = extract("mmt_sleeper.pdf").streamContainingAll("PNR", "Booking Status")!!
        val line = panel.lines().first { it.contains("Start Date") }

        assertEquals("Start Date* 10 Sep 2026", line.runs.first().text)
        assertEquals("Departure* 18:30 10 Sep 2026", line.runNear(365f)?.text)
    }

    @Test
    fun `lines read down the page and across it`() {
        fixtures.forEach { name ->
            val lines = extract(name).streamContainingAll("PNR", "Booking Status")!!.lines()

            lines.zipWithNext().forEach { (above, below) ->
                assertTrue("$name read the page out of order", above.y > below.y)
            }
            lines.forEach { line ->
                line.runs.zipWithNext().forEach { (left, right) ->
                    assertTrue("$name read a line out of order", left.x <= right.x)
                }
            }
        }
    }

    // ── Files that are not tickets ──

    /**
     * Anything unreadable comes back empty rather than thrown.
     *
     * The distinction the import flow draws is between "this file has no ticket in it" and "this
     * file could not be opened", and it can only draw it if the extractor answers instead of
     * failing. A renamed image, a truncated download and an empty file are all the first case.
     */
    @Test
    fun `a file that is not a PDF extracts to nothing`() {
        listOf(
            ByteArray(0),
            "not a pdf at all".toByteArray(),
            ByteArray(4096) { (it * 31 % 251).toByte() },
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(512)
        ).forEachIndexed { index, input ->
            assertTrue("input $index produced text", PdfTextExtractor.extract(input).isEmpty)
        }
    }

    @Test
    fun `a truncated ticket extracts to nothing`() {
        assertTrue(PdfTextExtractor.extract(bytes("mmt_sleeper.pdf").copyOf(2048)).isEmpty)
    }

    // ── The page, as a structure ──

    private fun at(x: Float, y: Float, text: String, stream: Int = 0) =
        PdfTextRun(stream = stream, x = x, y = y, text = text)

    @Test
    fun `a page of whitespace is empty`() {
        assertTrue(PdfText(emptyList()).isEmpty)
        assertTrue(PdfText(listOf(at(0f, 0f, "   "))).isEmpty)
        assertFalse(PdfText(listOf(at(0f, 0f, "PNR"))).isEmpty)
    }

    /** Baseline jitter inside one row must not split the row. */
    @Test
    fun `cells a fraction apart are one line`() {
        val page = PdfText(
            listOf(
                at(60f, 800f, "Left"),
                at(300f, 801.7f, "Middle"),
                at(600f, 800f, "Right"),
                at(60f, 780f, "Next row")
            )
        )

        val lines = page.lines()
        assertEquals(2, lines.size)
        assertEquals("Left Middle Right", lines[0].text)
        assertEquals("Next row", lines[1].text)
    }

    /** A gap wider than the tolerance is two rows, however close it looks. */
    @Test
    fun `cells further apart are two lines`() {
        val page = PdfText(listOf(at(60f, 800f, "Above"), at(60f, 796f, "Below")))

        assertEquals(listOf("Above", "Below"), page.lines().map { it.text })
    }

    @Test
    fun `an empty column reads as absent rather than as its neighbour`() {
        val line = PdfText(
            listOf(at(63f, 800f, "1."), at(91f, 800f, "Ananya Kapoor"), at(355f, 800f, "21"))
        ).lines().single()

        assertEquals("21", line.runNear(355f)?.text)
        // Nothing was drawn under the Gender header, and the age is not it.
        assertNull(line.runNear(429f)?.text)
        // Widen the window and the neighbour is reached, which is why the window is narrow.
        assertEquals("21", line.runNear(429f, tolerance = 100f)?.text)
    }

    @Test
    fun `a run can be found by how it starts`() {
        val line = PdfText(
            listOf(at(63f, 800f, "PNR"), at(300f, 800f, "  Train No./Name"))
        ).lines().single()

        assertEquals("  Train No./Name", line.runStartingWith("train")?.text)
        assertNull(line.runStartingWith("Class"))
        assertTrue(line.contains("pnr"))
        assertFalse(line.contains("Coach"))
    }

    @Test
    fun `a stream can be read on its own`() {
        val page = PdfText(
            listOf(
                at(60f, 800f, "Reservation", stream = 9),
                at(60f, 100f, "Conditions", stream = 10)
            )
        )

        assertEquals(listOf(9, 10), page.streamIndices)
        assertEquals("Reservation", page.stream(9).plainText())
        assertEquals("Conditions", page.stream(10).plainText())
        assertEquals("Reservation\nConditions", page.plainText())
    }

    @Test
    fun `a stream is chosen only when one stream has everything`() {
        val page = PdfText(
            listOf(
                at(60f, 800f, "PNR 1000000001", stream = 9),
                at(60f, 780f, "Booking Status", stream = 9),
                at(60f, 100f, "PNR refunds", stream = 10)
            )
        )

        assertEquals("PNR 1000000001\nBooking Status", page.streamContainingAll("PNR", "Booking Status")?.plainText())
        // Split across two streams: neither has both.
        assertNull(page.streamContainingAll("Booking Status", "refunds"))
        assertNull(page.streamContainingAll("Coach"))
    }

    /**
     * Two streams that both look like the reservation is a document shape nobody has seen.
     *
     * Answering with the first would be a guess that fails silently. Null makes the import say
     * it could not read the file, which is the truth.
     */
    @Test
    fun `an ambiguous page is refused rather than guessed at`() {
        val page = PdfText(
            listOf(
                at(60f, 800f, "PNR Booking Status", stream = 1),
                at(60f, 400f, "PNR Booking Status", stream = 2)
            )
        )

        assertNull(page.streamContainingAll("PNR", "Booking Status"))
    }
}
