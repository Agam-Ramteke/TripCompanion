package com.tripcompanion.app.data.transfer

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/**
 * Opens the document the user picked, so nothing else has to know about content providers.
 *
 * The same split as [com.tripcompanion.app.data.ticket.TicketFileReader], for the same reason:
 * this is the one part that cannot run without a `ContentResolver` and a `Uri` handed over by the
 * document picker. Keeping it here is what lets
 * [com.tripcompanion.app.domain.service.TripTransferService] take plain streams and be tested
 * against a real archive with no Android around it.
 *
 * Unlike the ticket reader, nothing is buffered. A trip with photographs is megabytes, and the
 * point of the archive format is that neither side ever holds it all at once.
 *
 * Whoever opens the stream closes it — the caller, here, not the service. That division is
 * deliberate: the flush that writes a file's last block belongs to whoever owns the descriptor,
 * and a stream closed by the other party is how a save ends up truncated.
 */
object TripArchiveFiles {

    /**
     * Opens the document at [uri] to write a trip into, or null if it cannot be opened.
     *
     * `"wt"` — truncate — because the save dialog will happily hand back a document that already
     * exists, and the default mode leaves whatever was longer than the new trip stuck on the end.
     * A zip with another file's tail after its central directory is a corrupt zip.
     */
    fun openForWrite(context: Context, uri: Uri): OutputStream? = try {
        context.contentResolver.openOutputStream(uri, "wt")
    } catch (_: Exception) {
        // A document that has since been deleted, a provider that revoked permission, a storage
        // volume pulled out. All ordinary; none worth crashing over.
        null
    }

    /** Opens the document at [uri] to read a trip out of, or null if it cannot be opened. */
    fun openForRead(context: Context, uri: Uri): InputStream? = try {
        context.contentResolver.openInputStream(uri)
    } catch (_: Exception) {
        null
    }
}
