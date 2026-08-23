package com.tripcompanion.app.data.ticket

import android.content.Context
import android.net.Uri
import com.tripcompanion.app.domain.service.TicketImportService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns the file the user picked into bytes.
 *
 * Separate from [IrctcTicketImportService] because this is the one part that cannot be tested
 * on a plain JVM: it needs a `ContentResolver` and a `Uri` handed over by the document picker.
 * Keeping it here is what lets [TicketImportService.readTicket] take a `ByteArray` and be
 * tested against real ticket files with no Android around it.
 *
 * Nothing is copied into app storage, unlike [com.tripcompanion.app.data.local.ImageStorageHelper].
 * A photo is content the app keeps and shows again later; a ticket PDF is read once, and the
 * fields that matter are saved as columns. Holding onto the file would mean holding a document
 * with someone's name, age and home address in it for no purpose anyone asked for.
 */
object TicketFileReader {

    /**
     * The picked file's bytes, or null if it could not be opened or is too large.
     *
     * The size ceiling is checked while reading rather than from the provider's reported
     * length, because a content provider is not obliged to report one. It stops one byte over
     * the limit so a file that lies about its size still cannot fill memory.
     */
    suspend fun read(context: Context, uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val limit = TicketImportService.MAX_FILE_BYTES
                val buffer = ByteArray(64 * 1024)
                val collected = java.io.ByteArrayOutputStream()
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (collected.size() + read > limit) return@withContext null
                    collected.write(buffer, 0, read)
                }
                collected.toByteArray()
            }
        } catch (_: Exception) {
            // Picking a file that has since been deleted, or one behind a provider that has
            // revoked permission, is an ordinary thing to do. The screen says the file could
            // not be read; there is nothing here worth crashing over.
            null
        }
    }
}
