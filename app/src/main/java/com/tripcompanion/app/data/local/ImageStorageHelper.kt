package com.tripcompanion.app.data.local

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageStorageHelper {

    /**
     * The one directory app-kept photographs live in.
     *
     * Named here rather than spelled out wherever it is needed, because a second literal is a
     * second directory the day one of them is edited — and
     * [com.tripcompanion.app.data.transfer.TripTransferServiceImpl] writes imported photographs
     * into this same folder so a transferred trip's pictures sit beside the ones taken here.
     */
    const val PHOTOS_DIR = "planned_photos"

    /** The photographs directory, which may not exist yet. */
    fun photosDir(context: Context): File = File(context.filesDir, PHOTOS_DIR)

    /**
     * Copies a selected photo Uri from Android's photo picker / content provider into
     * app-internal private storage directory (`filesDir/planned_photos/`).
     * Returns the permanent local file Uri string (e.g. `file:///data/user/0/.../planned_photos/photo_123.jpg`).
     * This guarantees the image reference will persist across device reboots and app restarts
     * without any URI permission revocation.
     */
    suspend fun saveImageToInternalStorage(context: Context, sourceUri: Uri): String = withContext(Dispatchers.IO) {
        val photosDir = photosDir(context)
        if (!photosDir.exists()) {
            photosDir.mkdirs()
        }

        val fileName = "photo_${System.currentTimeMillis()}.jpg"
        val destinationFile = File(photosDir, fileName)

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Cannot open input stream for URI: $sourceUri")
            outputStream = FileOutputStream(destinationFile)
            inputStream.copyTo(outputStream)
            destinationFile.toURI().toString()
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }
}
