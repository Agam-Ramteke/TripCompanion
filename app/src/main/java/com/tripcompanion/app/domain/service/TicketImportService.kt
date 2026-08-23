package com.tripcompanion.app.domain.service

import com.tripcompanion.app.domain.model.ParsedTicket

/**
 * Why an e-ticket could not be read.
 *
 * The user's categories, not the parser's — the same distinction
 * [LocationSearchError] draws. "This is a photo of a ticket, not the PDF" and
 * "this PDF is not a ticket" both mean the import failed, but only the first has
 * an obvious next step for the person holding the phone.
 */
enum class TicketImportError {
    /** The file is not a PDF at all — a photo, a screenshot, a renamed archive. */
    NOT_A_PDF,

    /**
     * A real PDF with no text in it, which in practice means a scan or a photograph
     * printed to PDF. Nothing can be extracted without character recognition, which
     * this app does not do.
     */
    NO_TEXT_LAYER,

    /** Text came out, but none of it looks like a reservation. */
    NOT_AN_ETICKET,

    /** The file could not be opened or read at all. */
    UNREADABLE
}

/**
 * The outcome of reading a ticket.
 *
 * [Parsed] carries its own [ParsedTicket.warnings], so a partial success stays a
 * success: a ticket whose arrival time is a station name is still worth importing,
 * and demoting it to a failure would throw away the nine fields that did read.
 */
sealed interface TicketImportOutcome {
    data class Parsed(val ticket: ParsedTicket) : TicketImportOutcome
    data class Failed(val error: TicketImportError) : TicketImportOutcome
}

/**
 * Reads a train e-ticket PDF into something the app can save.
 *
 * The boundary that keeps PDF internals out of the UI and the domain, the way
 * [LocationSearchService] keeps HTTP out of them. Nothing about content streams,
 * glyph codes or page coordinates is visible through this interface, so the ticket
 * layouts it understands can be extended — or the whole extractor replaced with a
 * library — without a screen changing.
 *
 * Takes bytes rather than a file or a `Uri` on purpose: reading the user's chosen
 * file is the caller's job, which keeps every implementation of this testable on a
 * plain JVM against real ticket files.
 */
interface TicketImportService {

    /**
     * Never throws. A file that cannot be read is a value, because choosing the
     * wrong file is an ordinary thing to do, not an exceptional one.
     */
    suspend fun readTicket(bytes: ByteArray): TicketImportOutcome

    companion object {
        /**
         * Largest file this will attempt.
         *
         * An e-ticket is tens of kilobytes; anything at this size is a different kind
         * of document, and refusing early beats inflating it to find out.
         */
        const val MAX_FILE_BYTES = 12L * 1024 * 1024
    }
}
