package com.tripcompanion.app.data.pdf

/**
 * A piece of text and the point on the page where it was drawn.
 *
 * [stream] is the index of the PDF content stream that drew it, and it is load-bearing rather
 * than diagnostic. An IRCTC e-ticket draws the reservation panel and the page-long terms and
 * conditions in *separate* content streams, and the two overlap vertically — the boilerplate
 * runs down the same band of the page as the passenger table. Grouping by y alone therefore
 * splices legal text into the middle of a passenger row. Keeping the stream index lets a
 * parser take the panel and discard the rest, which is exact where a coordinate cutoff would
 * be a guess.
 *
 * [y] follows PDF convention: it grows *upward* from the bottom of the page, so the first line
 * of the page has the largest y. [PdfText.lines] hides that.
 */
data class PdfTextRun(
    val stream: Int,
    val x: Float,
    val y: Float,
    val text: String
)

/**
 * One horizontal line of the page: the runs that share a baseline, left to right.
 *
 * A line is not a sentence. On a ticket it is usually a table row, and the gaps between its
 * runs are the column boundaries — which is why the runs are kept individually addressable by
 * x rather than only as joined [text].
 */
data class PdfTextLine(
    val y: Float,
    val runs: List<PdfTextRun>
) {

    /** Every run on the line, single-spaced. Convenient for matching a line as a whole. */
    val text: String get() = runs.joinToString(" ") { it.text }

    /**
     * The run whose left edge is closest to [x], within [tolerance] points.
     *
     * How a value is tied to its header. A ticket's table header row and its value rows are
     * separate lines that share column positions, so "the Age of passenger 1" is "the run on
     * the passenger's line nearest the x of the `Age` header". Null when the column is empty
     * on this line, which is a real case — an infant has no berth.
     *
     * Deliberately nearest-within-a-window rather than nearest outright: values are typeset
     * slightly off their header's x, but a missing column would otherwise silently return its
     * neighbour's value, and a wrong berth is worse than a blank one.
     */
    fun runNear(x: Float, tolerance: Float = 40f): PdfTextRun? =
        runs.filter { kotlin.math.abs(it.x - x) <= tolerance }.minByOrNull { kotlin.math.abs(it.x - x) }

    /** The first run whose text starts with [prefix], ignoring case. */
    fun runStartingWith(prefix: String): PdfTextRun? =
        runs.firstOrNull { it.text.trimStart().startsWith(prefix, ignoreCase = true) }

    fun contains(needle: String): Boolean = text.contains(needle, ignoreCase = true)
}

/**
 * All the positioned text a PDF contains, before anyone has decided what it means.
 *
 * Deliberately dumb: it knows about pages and coordinates and nothing about tickets. The
 * knowledge of what a run signifies lives in the parser, so the extractor can be tested against
 * "does this produce the right glyphs at the right coordinates" without any ticket in sight.
 */
class PdfText(val runs: List<PdfTextRun>) {

    val isEmpty: Boolean get() = runs.none { it.text.isNotBlank() }

    /** The content streams that drew any text, in the order they appear in the file. */
    val streamIndices: List<Int> get() = runs.map { it.stream }.distinct().sorted()

    /** Just one stream's text, as its own [PdfText]. */
    fun stream(index: Int): PdfText = PdfText(runs.filter { it.stream == index })

    /**
     * The single stream that contains all of [markers], or null if none does or several do.
     *
     * Null on ambiguity on purpose. Two streams both claiming to be the reservation panel means
     * the assumption behind the markers is wrong, and a parser that picked the first would fail
     * quietly on a document shape nobody has seen. Better to report that the file could not be
     * read.
     */
    fun streamContainingAll(vararg markers: String): PdfText? {
        val hits = streamIndices.filter { index ->
            val text = stream(index).runs.joinToString(" ") { it.text }
            markers.all { text.contains(it, ignoreCase = true) }
        }
        return hits.singleOrNull()?.let { stream(it) }
    }

    /**
     * The text grouped into lines, top of the page first.
     *
     * [tolerance] absorbs baseline jitter: cells of one table row are not always typeset at
     * exactly the same y — on these tickets the middle column of the header box sits about
     * 1.7pt above its neighbours — and a row split in two is a row whose columns no longer
     * line up with their headers.
     *
     * Runs that start at the same x are joined, because a single label drawn as several show
     * operators after one positioning operator records the same x for each: x is only updated
     * by a positioning operator here, not advanced by glyph widths. That join is what turns
     * five separate runs back into `Start Date* 10 Sep 2026`.
     */
    fun lines(tolerance: Float = 2.5f, joinDistance: Float = 3f): List<PdfTextLine> {
        val ordered = runs.filter { it.text.isNotBlank() }
            .sortedWith(compareByDescending<PdfTextRun> { it.y }.thenBy { it.x })

        val rows = mutableListOf<MutableList<PdfTextRun>>()
        for (run in ordered) {
            val last = rows.lastOrNull()
            if (last != null && kotlin.math.abs(last.first().y - run.y) <= tolerance) {
                last.add(run)
            } else {
                rows.add(mutableListOf(run))
            }
        }

        return rows.map { row ->
            val sorted = row.sortedBy { it.x }
            val merged = mutableListOf<PdfTextRun>()
            for (run in sorted) {
                val previous = merged.lastOrNull()
                if (previous != null && run.x - previous.x < joinDistance) {
                    merged[merged.lastIndex] = previous.copy(text = previous.text + run.text)
                } else {
                    merged.add(run)
                }
            }
            PdfTextLine(y = row.first().y, runs = merged)
        }
    }

    /** Every line, newline-separated. For diagnostics and for matching against whole documents. */
    fun plainText(): String = lines().joinToString("\n") { it.text }
}
