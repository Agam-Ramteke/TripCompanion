package com.tripcompanion.app.data.transfer

import com.tripcompanion.app.domain.service.TripImportError
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A trip file, mechanically: a zip with one JSON manifest and the photographs it refers to.
 *
 * A zip rather than a single JSON document with the images encoded inside it. Base64 costs a
 * third more bytes, holds every photograph in memory at once on both sides of the transfer, and
 * produces a file nothing else on earth can open. This way the manifest streams in and out as
 * text and each photograph is copied through a 64 KB buffer, so a trip with forty pictures
 * transfers in about as much memory as a trip with one — and if it ever goes wrong, the user can
 * open the thing on a laptop and see their own photographs sitting in a folder.
 *
 * The manifest is written first on purpose: [read] hands images to its caller as it meets them
 * and returns the manifest text at the end, so nothing has to be buffered whole either way.
 *
 * Neither method closes the stream it was given. The caller opened it — from a document picker,
 * usually — and closing someone else's stream is how a SAF write ends up truncated.
 */
internal object TripArchive {

    /**
     * What kind of file this is, written into the manifest rather than relied on from the name.
     *
     * A file name survives nothing: it goes through a chat app, gets renamed, loses its
     * extension. The manifest saying what it is means a trip file is still recognisable as one
     * after all of that, and an ordinary zip is still recognisable as *not* one.
     */
    const val FORMAT = "com.tripcompanion.trip"

    /**
     * The format's version, checked on import.
     *
     * Bump this when the manifest gains a field the *reader* must understand to be correct. A new
     * optional field does not need a bump — [TripManifest] reads what it knows and ignores the
     * rest, so an older app importing a newer trip would silently drop it. Which is exactly why
     * a version this app does not know is refused outright rather than read hopefully.
     */
    const val VERSION = 1

    /** The manifest's entry name. Its presence is what makes a zip a trip file. */
    const val MANIFEST_ENTRY = "trip.json"

    /** Where photographs live inside the archive. */
    const val IMAGE_DIR = "images/"

    private const val BUFFER_BYTES = 64 * 1024

    /**
     * A photograph on its way into an archive, opened only when it is reached.
     *
     * A lambda rather than bytes so the file is streamed straight through the zip. Returning null
     * means the picture has gone missing since it was linked — an ordinary thing on a phone whose
     * storage was cleared — and the entry is skipped rather than the export failing over it.
     */
    class ImageSource(val entryName: String, val open: () -> InputStream?)

    /** Writes [manifest] and every image in [images] to [sink], which is left open. */
    fun write(sink: OutputStream, manifest: String, images: List<ImageSource>): Int {
        var written = 0
        val zip = ZipOutputStream(sink)
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(manifest.toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        images.forEach { image ->
            val source = image.open() ?: return@forEach
            source.use { input ->
                zip.putNextEntry(ZipEntry(image.entryName))
                input.copyTo(zip, BUFFER_BYTES)
                zip.closeEntry()
            }
            written++
        }
        // Finished, not closed: the caller's stream stays the caller's. `finish` is what writes
        // the central directory, so skipping it leaves a file that looks like a truncated zip.
        zip.finish()
        zip.flush()
        return written
    }

    /**
     * Reads an archive, handing each image to [onImage] as it is met, and returns the manifest.
     *
     * [onImage] is given the entry's own name only so it can be matched to the manifest; it must
     * choose its own destination. Nothing here trusts an entry name as a path — a zip that names
     * an entry `../../databases/trip_companion.db` is a real attack on anyone who resolves it
     * against a directory, and the defence is to never do that rather than to sanitise.
     *
     * Directory entries and anything outside [IMAGE_DIR] are skipped rather than refused: a zip
     * that has been round-tripped through a desktop tool can pick up a `__MACOSX` folder and a
     * thumbnail cache, and none of that makes the trip inside it unreadable.
     */
    fun read(source: InputStream, onImage: (entryName: String, bytes: InputStream) -> Unit): String {
        var manifest: String? = null
        val zip = ZipInputStream(source)
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            when {
                entry.isDirectory -> Unit
                name == MANIFEST_ENTRY -> manifest = zip.readBytes().toString(Charsets.UTF_8)
                name.startsWith(IMAGE_DIR) -> onImage(name, zip)
            }
            zip.closeEntry()
        }
        return manifest ?: throw TripArchiveException(TripImportError.NOT_A_TRIP_FILE)
    }
}

/**
 * A trip file this app will not read, and which of the user's four answers applies.
 *
 * Thrown rather than returned because it is raised deep inside the manifest read and there is
 * nothing useful to do with a half-parsed trip on the way back up. The service catches it at the
 * boundary and returns a [com.tripcompanion.app.domain.service.TripImportOutcome.Failed].
 */
internal class TripArchiveException(val error: TripImportError) : Exception(error.name)
