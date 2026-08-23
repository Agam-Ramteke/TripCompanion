package com.tripcompanion.app.data.ticket

import com.tripcompanion.app.data.pdf.PdfTextExtractor
import com.tripcompanion.app.domain.service.TicketImportError
import com.tripcompanion.app.domain.service.TicketImportOutcome
import com.tripcompanion.app.domain.service.TicketImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads an IRCTC e-ticket, off the main thread, without ever throwing.
 *
 * Three jobs, and deliberately no more: get off the main thread, turn the two ways this can
 * fail into the user's two categories, and delegate. All the reading is in [PdfTextExtractor]
 * and all the meaning is in [IrctcTicketParser], both of which are pure and tested directly.
 *
 * Inflating a PDF and walking its content streams is CPU work on a few hundred kilobytes, so
 * [Dispatchers.Default] rather than IO: the file is already bytes by the time it arrives here.
 */
@Singleton
class IrctcTicketImportService @Inject constructor() : TicketImportService {

    override suspend fun readTicket(bytes: ByteArray): TicketImportOutcome {
        if (bytes.size > TicketImportService.MAX_FILE_BYTES) {
            return TicketImportOutcome.Failed(TicketImportError.NOT_A_PDF)
        }
        if (!looksLikePdf(bytes)) {
            return TicketImportOutcome.Failed(TicketImportError.NOT_A_PDF)
        }

        return withContext(Dispatchers.Default) {
            val text = try {
                PdfTextExtractor.extract(bytes)
            } catch (_: Exception) {
                // A malformed PDF is a file the user picked by mistake, not a bug to crash on.
                return@withContext TicketImportOutcome.Failed(TicketImportError.UNREADABLE)
            }

            // A scan or a photo printed to PDF: real pages, real images, no glyphs. There is
            // nothing to parse and no amount of retrying changes that, so it is worth saying
            // plainly rather than reporting an empty ticket.
            if (text.isEmpty) {
                return@withContext TicketImportOutcome.Failed(TicketImportError.NO_TEXT_LAYER)
            }

            val ticket = IrctcTicketParser.parse(text)
            if (ticket.hasSubstance) {
                TicketImportOutcome.Parsed(ticket)
            } else {
                TicketImportOutcome.Failed(TicketImportError.NOT_AN_ETICKET)
            }
        }
    }

    /**
     * The file's own claim to be a PDF, from its first bytes rather than its name.
     *
     * A `.pdf` extension is a suggestion; sharing a ticket through three apps is how a JPEG
     * ends up wearing one. The header is checked here rather than inside the extractor so
     * "you picked a photo" and "this PDF is a photo" stay two different messages.
     */
    private fun looksLikePdf(bytes: ByteArray): Boolean {
        if (bytes.size < PDF_HEADER.size) return false
        return PDF_HEADER.indices.all { bytes[it] == PDF_HEADER[it] }
    }

    private companion object {
        /** `%PDF-` */
        val PDF_HEADER = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D)
    }
}
