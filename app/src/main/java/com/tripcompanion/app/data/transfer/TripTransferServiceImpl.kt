package com.tripcompanion.app.data.transfer

import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.repository.ActivityRepository
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.PlannedPhotoRepository
import com.tripcompanion.app.domain.repository.StayDetailsRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.repository.TripRepository
import com.tripcompanion.app.domain.service.TripExportError
import com.tripcompanion.app.domain.service.TripExportOutcome
import com.tripcompanion.app.domain.service.TripImportError
import com.tripcompanion.app.domain.service.TripImportOutcome
import com.tripcompanion.app.domain.service.TripTransferService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes trip files, over the repositories the rest of the app uses.
 *
 * Over the repositories rather than the DAOs so that a transferred trip is built the same way a
 * hand-entered one is — inserts go through the same code, so a trip that arrives from another
 * phone cannot end up in a state the app could not have produced itself.
 *
 * The one piece of Android here is not here at all: the photographs directory arrives as a
 * [TripImagesDir] `File`, and the user's chosen document is opened by
 * [com.tripcompanion.app.data.transfer.TripArchiveFiles] before either method is called. So this
 * class runs, whole, in a JVM test against a real zip.
 *
 * ### Ids
 *
 * Nothing keeps its id. The manifest's ids exist only so the parts can point at each other; every
 * row is inserted fresh and every reference is rewritten through an old→new map. Preserving them
 * would mean either colliding with rows already on the receiving phone or overwriting them, and a
 * trip arriving from a friend must not be able to edit the trip already on your itinerary.
 *
 * ### When it goes wrong halfway
 *
 * An import that fails after writing some rows undoes them: the trip is deleted — which cascades
 * to its events, activities, photo plans, stays, bookings and timetables — and the places and
 * image files it created are removed by hand, because neither hangs off the trip. Done this way
 * rather than in a database transaction because the alternative is a transaction abstraction
 * threaded through every repository, and this failure is rare, cheap to undo, and easy to read.
 */
@Singleton
class TripTransferServiceImpl @Inject constructor(
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
    private val activityRepository: ActivityRepository,
    private val plannedPhotoRepository: PlannedPhotoRepository,
    private val stayDetailsRepository: StayDetailsRepository,
    private val trainRepository: TrainRepository,
    private val timeProvider: TimeProvider,
    @TripImagesDir private val imagesDir: File
) : TripTransferService {

    // ---- Out ---------------------------------------------------------------

    override suspend fun export(tripId: Long, sink: OutputStream): TripExportOutcome =
        withContext(Dispatchers.IO) {
            val trip = tripRepository.getTripById(tripId).first()
                ?: return@withContext TripExportOutcome.Failed(TripExportError.TRIP_NOT_FOUND)

            val images = ImageCollector()
            val events = eventRepository.getEventsForTrip(tripId).first()
            val trains = trainRepository.getTrainsForTrip(tripId).first()

            // Only the places this trip actually visits. The places table is global — it holds
            // everywhere the user has ever saved — and sending all of it with one trip would put
            // a stranger's whole travel history on the receiving phone.
            val places = events.mapNotNull(Event::locationId).distinct()
                .mapNotNull { locationRepository.getLocationByIdOnce(it) }
                .map { it.copy(photoUri = images.carry(it.photoUri)) }

            val bundle = TripBundle(
                trip = trip.copy(coverImageUri = images.carry(trip.coverImageUri)),
                places = places,
                events = events,
                activities = events.associate { it.id to activityRepository.getActivitiesForEvent(it.id).first() }
                    .filterValues { it.isNotEmpty() },
                photos = events.associate { event ->
                    event.id to plannedPhotoRepository.getPhotosForEvent(event.id).first()
                        .map { it.copy(referenceImageUri = images.carry(it.referenceImageUri)) }
                }.filterValues { it.isNotEmpty() },
                stays = events.mapNotNull { event ->
                    stayDetailsRepository.getForEventOnce(event.id)
                        ?.let { event.id to it.copy(photoUri = images.carry(it.photoUri)) }
                }.toMap(),
                trains = trains,
                stops = trains.associate { it.id to trainRepository.getStopsOnce(it.id) }
                    .filterValues { it.isNotEmpty() }
            )

            val manifest = TripManifest.encode(bundle, timeProvider.now())
            try {
                val imageCount = TripArchive.write(sink, manifest, images.sources)
                TripExportOutcome.Written(
                    tripName = trip.name,
                    eventCount = events.size,
                    trainCount = trains.size,
                    imageCount = imageCount
                )
            } catch (_: IOException) {
                TripExportOutcome.Failed(TripExportError.WRITE_FAILED)
            }
        }

    /**
     * Gathers the photographs a trip refers to, giving each one a name inside the archive.
     *
     * [carry] is called while the bundle is being built and returns what should go in the manifest
     * in place of the local URI — so there is no second pass over the trip rewriting fields, and a
     * field that is added later cannot be forgotten by a rewriter that does not know about it.
     */
    private inner class ImageCollector {

        private val named = mutableMapOf<String, String>()
        val sources = mutableListOf<TripArchive.ImageSource>()

        /**
         * The archive name for [uri], or [uri] itself when there is nothing to carry.
         *
         * A remote URL is left exactly as it is: the other phone can fetch it as well as this one
         * can, and copying it into the archive would only make the file bigger. Only local files
         * travel — those are the ones that exist on this device and nowhere else.
         *
         * The same photograph used twice — a hotel's picture also planned as a shot — is carried
         * once and named once, which is why the map is keyed by URI.
         */
        fun carry(uri: String?): String? {
            if (uri.isNullOrBlank() || !uri.startsWith(LOCAL_URI_SCHEME)) return uri
            named[uri]?.let { return it }

            val entryName = "${TripArchive.IMAGE_DIR}img_${sources.size + 1}.${extensionOf(uri)}"
            named[uri] = entryName
            sources += TripArchive.ImageSource(entryName) { openLocal(uri) }
            return entryName
        }
    }

    private fun openLocal(uri: String): InputStream? = try {
        File(URI(uri)).takeIf { it.isFile }?.inputStream()
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IOException) {
        null
    }

    // ---- In ----------------------------------------------------------------

    override suspend fun import(source: InputStream): TripImportOutcome =
        withContext(Dispatchers.IO) {
            val written = mutableMapOf<String, String>()
            val manifest = try {
                // Images are saved as they come past, before the manifest has necessarily been
                // read — order inside a zip is not ours to choose. Each is filed under a name
                // derived from its entry, and the manifest is matched to them afterwards.
                TripArchive.read(source) { entryName, bytes ->
                    saveImage(entryName, bytes)?.let { written[entryName] = it }
                }
            } catch (e: TripArchiveException) {
                discard(written.values)
                return@withContext TripImportOutcome.Failed(e.error)
            } catch (_: IOException) {
                discard(written.values)
                return@withContext TripImportOutcome.Failed(TripImportError.UNREADABLE)
            }

            val bundle = try {
                TripManifest.decode(manifest)
            } catch (e: TripArchiveException) {
                discard(written.values)
                return@withContext TripImportOutcome.Failed(e.error)
            }

            insert(bundle, written)
        }

    private suspend fun insert(
        bundle: TripBundle,
        images: Map<String, String>
    ): TripImportOutcome {
        /** The local file an archive name became, or the value untouched when it was never one. */
        fun local(value: String?): String? = value?.let { images[it] ?: it }

        val places = mutableListOf<Long>()
        var tripId = 0L
        try {
            tripId = tripRepository.insertTrip(
                bundle.trip.copy(id = 0, coverImageUri = local(bundle.trip.coverImageUri))
            )

            val placeIds = mutableMapOf<Long, Long>()
            bundle.places.forEach { place ->
                val id = locationRepository.insertLocation(
                    place.copy(id = 0, photoUri = local(place.photoUri))
                )
                placeIds[place.id] = id
                places += id
            }

            val eventIds = mutableMapOf<Long, Long>()
            bundle.events.forEach { event ->
                val eventId = eventRepository.insertEvent(
                    event.copy(
                        id = 0,
                        tripId = tripId,
                        locationId = event.locationId?.let(placeIds::get)
                    )
                )
                eventIds[event.id] = eventId
                bundle.activities[event.id]?.forEach {
                    activityRepository.insertActivity(it.copy(id = 0, eventId = eventId))
                }
                bundle.photos[event.id]?.forEach {
                    plannedPhotoRepository.insertPhoto(
                        it.copy(id = 0, eventId = eventId, referenceImageUri = local(it.referenceImageUri))
                    )
                }
                bundle.stays[event.id]?.let {
                    stayDetailsRepository.save(it.copy(eventId = eventId, photoUri = local(it.photoUri)))
                }
            }

            bundle.trains.forEach { train ->
                val trainId = trainRepository.insertTrain(
                    train.copy(
                        id = 0,
                        tripId = tripId,
                        // A booking whose journey did not come with the file loses its link
                        // rather than keeping a number that now points at somebody else's day.
                        eventId = train.eventId?.let(eventIds::get),
                        passengers = train.passengers.map { it.copy(id = 0, trainId = 0) }
                    )
                )
                bundle.stops[train.id]?.let { stops ->
                    trainRepository.replaceSchedule(trainId, stops.map { it.copy(id = 0, trainId = trainId) })
                }
            }
        } catch (_: Exception) {
            rollback(tripId, places, images.values)
            // Not rethrown: a half-written trip is a damaged file as far as the user is concerned,
            // and there is nothing here they could act on differently.
            return TripImportOutcome.Failed(TripImportError.DAMAGED)
        }

        return TripImportOutcome.Imported(
            tripId = tripId,
            tripName = bundle.trip.name,
            eventCount = bundle.events.size,
            trainCount = bundle.trains.size,
            imageCount = images.size
        )
    }

    private suspend fun rollback(tripId: Long, places: List<Long>, images: Collection<String>) {
        // Deleting the trip cascades to its days and everything hanging off them, including the
        // bookings. Places do not hang off a trip — they are global — so they go by hand.
        if (tripId != 0L) tripRepository.deleteTrip(tripId)
        places.forEach { locationRepository.deleteLocation(it) }
        discard(images)
    }

    /**
     * Saves one photograph out of an archive and returns its local URI.
     *
     * The destination name is ours, built from the entry's *last path segment* and then made
     * unique against what is already on disk. Nothing resolves the entry name as a path: an
     * archive naming an entry `images/../../databases/trip_companion.db` would otherwise write
     * over the database, and refusing to do the resolution at all is a stronger defence than
     * trying to spot the attempt.
     */
    private fun saveImage(entryName: String, bytes: InputStream): String? = try {
        if (!imagesDir.exists()) imagesDir.mkdirs()
        val destination = unusedFile(entryName.substringAfterLast('/'))
        destination.outputStream().use { bytes.copyTo(it) }
        destination.toURI().toString()
    } catch (_: IOException) {
        // A photograph that could not be written is a photograph missing from the trip, not a
        // failed import — the same forgiveness the export shows a picture that has gone missing.
        null
    }

    private fun unusedFile(name: String): File {
        val safe = name.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
            .ifBlank { "image.jpg" }
        val stem = safe.substringBeforeLast('.', safe)
        val extension = safe.substringAfterLast('.', "jpg")
        var candidate = File(imagesDir, "$IMPORT_PREFIX$stem.$extension")
        var attempt = 2
        while (candidate.exists()) {
            candidate = File(imagesDir, "$IMPORT_PREFIX$stem-$attempt.$extension")
            attempt++
        }
        return candidate
    }

    private fun discard(uris: Collection<String>) {
        uris.forEach { uri ->
            try {
                File(URI(uri)).delete()
            } catch (_: IllegalArgumentException) {
                // Nothing to clean up: it was never a file we wrote.
            }
        }
    }

    // ---- The file name -----------------------------------------------------

    /**
     * `"Udaipur.zip"` for a trip called Udaipur.
     *
     * `.zip`, not a `.trip` extension of our own, because the save dialog derives the extension
     * from the MIME type: offering `Udaipur.trip` for an `application/zip` document produces a
     * file called `Udaipur.trip.zip`. And a plain zip is a small kindness — a file the user can
     * open on a laptop and recognise as their own trip rather than a blob only this app can read.
     */
    override suspend fun suggestedFileName(tripId: Long): String {
        val name = tripRepository.getTripById(tripId).first()?.name?.trim().orEmpty()
        // Illegal on FAT, which is what a shared SD card and most USB sticks are formatted as.
        val safe = name.filterNot { it in ILLEGAL_IN_FILE_NAMES || it.code < 0x20 }.trim()
        return "${safe.ifBlank { "Trip" }}.zip"
    }

    private companion object {
        /** Everything the app stores locally is a `file:` URI — see `ImageStorageHelper`. */
        const val LOCAL_URI_SCHEME = "file:"

        /** So a transferred photograph is recognisable on disk, and cannot collide with one taken here. */
        const val IMPORT_PREFIX = "imported_"

        const val ILLEGAL_IN_FILE_NAMES = "/\\:*?\"<>|"

        fun extensionOf(uri: String): String {
            val candidate = uri.substringAfterLast('.', "")
            return if (candidate.length in 1..5 && candidate.all { it.isLetterOrDigit() }) {
                candidate
            } else {
                "jpg"
            }
        }
    }
}
