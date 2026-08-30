package com.tripcompanion.app.domain.service

import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps every train booking on the itinerary as a JOURNEY event.
 *
 * A journey is not a separate kind of thing from the rest of the plan — it is the part of the day
 * you spend travelling, and leaving it in a Trains tab of its own meant the itinerary showed a gap
 * where six hours on a train were, and Home could never say "next up: the 06:15". So a train is
 * *always* an itinerary row: this class creates the row, adopts one the user already wrote if
 * there is an obvious match, and afterwards keeps the two in step.
 *
 * The rules, and why each one is what it is:
 *
 * - **Times always follow the booking.** Two sources for one fact is how an itinerary ends up
 *   disagreeing with the ticket in someone's hand, and the ticket wins that argument every time.
 * - **The title only follows while nobody has retyped it.** It is rewritten when it is blank or
 *   still equals what this class generated from the booking as it stood before the edit; a title
 *   the user typed is theirs and survives every later save.
 * - **Station location is auto-attached.** Adding a train searches and attaches its origin / boarding
 *   station as a [Location] on the journey event, putting it on the itinerary and map.
 * - **"What we're doing" is never written at all**, not even on creation. It is the one field on
 *   the event that is prose, it would go stale the moment the booking changed, and the itinerary
 *   draws the live ticket card above it anyway.
 * - **Deleting a train leaves its event alone.** [Train.eventId] is deliberately not a foreign
 *   key: the ticket and the plan are two records, and cancelling a booking does not cancel the
 *   need to get there.
 */
@Singleton
class JourneyEventLinker @Inject constructor(
    private val eventRepository: EventRepository,
    private val trainRepository: TrainRepository,
    private val locationRepository: LocationRepository? = null,
    private val locationSearchService: LocationSearchService? = null
) {

    private val backfillLock = Mutex()

    @Volatile
    private var backfilled = false

    /**
     * Makes sure [train] has a JOURNEY event, and returns its id for the caller to store on the
     * booking.
     *
     * Call this *before* saving the train, with [previous] being the record the editor was opened
     * on (null for a new booking) — the id comes back so the train is written with the link
     * already on it rather than needing a second write.
     */
    suspend fun syncEvent(train: Train, previous: Train?): Long {
        val stationName = train.boardingPointName.ifBlank { train.originName }.trim()
        val stationLocationId = resolveStationLocation(stationName)

        val linked = train.eventId?.let { eventRepository.getEventById(it).first() }
        if (linked != null) {
            val locId = linked.locationId ?: stationLocationId
            eventRepository.updateEvent(
                linked.copy(
                    title = retitle(linked.title, train, previous),
                    startTime = train.departureTime,
                    endTime = train.arrivalTime,
                    locationId = locId
                )
            )
            return linked.id
        }

        // Either nothing was linked, or the link points at an event that has since been deleted.
        // Both mean the same thing here: find the row this journey belongs in, or make one.
        val adopted = adoptable(train)
        if (adopted != null) {
            val locId = adopted.locationId ?: stationLocationId
            // The title is left exactly as it is. An event this class did not create was written
            // by the user, and matching it to a ticket is not licence to rename it.
            eventRepository.updateEvent(
                adopted.copy(
                    startTime = train.departureTime,
                    endTime = train.arrivalTime,
                    locationId = locId
                )
            )
            return adopted.id
        }

        return eventRepository.insertEvent(
            Event(
                tripId = train.tripId,
                type = EventType.JOURNEY,
                title = journeyTitle(train),
                startTime = train.departureTime,
                endTime = train.arrivalTime,
                locationId = stationLocationId,
                order = eventRepository.getNextOrder(train.tripId)
            )
        )
    }

    /**
     * Resolves or creates a [Location] for the station name, searching online if available.
     */
    suspend fun resolveStationLocation(stationName: String, forceRefresh: Boolean = false): Long? {
        if (stationName.isBlank() || locationRepository == null) return null
        return try {
            val existing = locationRepository.getAllLocations().first()
            val match = existing.firstOrNull {
                it.name.equals(stationName, ignoreCase = true) ||
                    it.name.startsWith(stationName, ignoreCase = true)
            }
            if (!forceRefresh && match != null && match.latitude in 6.0..38.0 && match.longitude in 68.0..98.0 && match.providerName == "LocationIQ") {
                return match.id
            }

            val place: SearchResultLocation? = locationSearchService?.let { searchService ->
                val query = if (stationName.contains("station", ignoreCase = true)) {
                    "$stationName, India"
                } else {
                    "$stationName Railway Station, India"
                }
                when (val outcome = searchService.searchPlaces(query)) {
                    is LocationSearchOutcome.Results -> {
                        val mainToken = stationName.split(" ", "-", ".").firstOrNull { it.length > 2 }?.lowercase()
                        outcome.places.firstOrNull { p ->
                            if (mainToken != null) {
                                p.name.lowercase().contains(mainToken) || p.formattedAddress.lowercase().contains(mainToken)
                            } else true
                        } ?: outcome.places.firstOrNull()
                    }
                    else -> null
                }
            }

            if (place != null) {
                if (match != null) {
                    locationRepository.updateLocation(
                        match.copy(
                            name = stationName,
                            address = place.formattedAddress,
                            latitude = place.latitude,
                            longitude = place.longitude,
                            category = "Transit",
                            providerPlaceId = place.providerPlaceId,
                            providerName = place.providerName
                        )
                    )
                    match.id
                } else {
                    locationRepository.insertLocation(
                        Location(
                            name = stationName,
                            address = place.formattedAddress,
                            latitude = place.latitude,
                            longitude = place.longitude,
                            category = "Transit",
                            providerPlaceId = place.providerPlaceId,
                            providerName = place.providerName
                        )
                    )
                }
            } else if (match != null) {
                match.id
            } else {
                locationRepository.insertLocation(
                    Location(
                        name = stationName,
                        category = "Transit"
                    )
                )
            }
        } catch (_: Exception) {
            try {
                locationRepository.insertLocation(
                    Location(
                        name = stationName,
                        category = "Transit"
                    )
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Gives every train recorded before this existed its place on the itinerary.
     *
     * Idempotent and cheap on the second call: only trains with no live link are touched, so a
     * run after everything is linked reads two tables and writes nothing. Guarded by a mutex and
     * a once-per-process flag because more than one screen calls it on the way in, and two
     * concurrent runs would both find the same train unlinked and give it an event each.
     */
    suspend fun linkExistingTrains() {
        if (backfilled) return
        backfillLock.withLock {
            if (backfilled) return
            trainRepository.getAllTrains().first().forEach { train ->
                val linked = train.eventId?.let { eventRepository.getEventById(it).first() }
                if (linked != null) return@forEach
                val eventId = syncEvent(train, previous = null)
                // Safe despite `updateTrain` replacing the party wholesale: every read from this
                // repository comes with its passengers attached, so they are written back intact.
                trainRepository.updateTrain(train.copy(eventId = eventId))
            }
            backfilled = true
        }
    }

    /**
     * A JOURNEY event the user already wrote for this journey, if there is an unmistakable one.
     *
     * Unmistakable means: on the same trip, not already claimed by another booking, and starting
     * within [ADOPTION_WINDOW] of the train's departure. Someone who typed "Train to Udaipur" on
     * their itinerary and then entered the ticket should end up with one row, not two — and a
     * planned journey and a booked train two hours apart on the same trip are the same journey.
     * Beyond that window this guesses nothing and makes a fresh row instead.
     */
    private suspend fun adoptable(train: Train): Event? {
        val claimed = trainRepository.getTrainsForTrip(train.tripId).first()
            .filter { it.id != train.id }
            .mapNotNull { it.eventId }
            .toSet()
        return eventRepository.getEventsForTrip(train.tripId).first()
            .filter { it.type == EventType.JOURNEY && it.id !in claimed }
            .map { it to gapMinutes(it, train) }
            .filter { (_, gap) -> gap <= ADOPTION_WINDOW }
            .minByOrNull { (_, gap) -> gap }
            ?.first
    }

    private fun gapMinutes(event: Event, train: Train): Long =
        kotlin.math.abs(Duration.between(event.startTime, train.departureTime).toMinutes())

    /**
     * The title after the booking changed: the generated one while the stored one is still
     * generated, otherwise whatever the user has since typed.
     *
     * [previous] is only trusted when it was pointing at this same event. A train whose link the
     * user just moved from one event to another says nothing about what the new event's title
     * used to be.
     */
    private fun retitle(stored: String, train: Train, previous: Train?): String {
        val generated = journeyTitle(train)
        if (stored.isBlank()) return generated
        val wasGenerated = stored == generated ||
            (previous != null && previous.eventId == train.eventId && stored == journeyTitle(previous))
        return if (wasGenerated) generated else stored
    }

    /**
     * What an auto-created journey is called: `"Train to Udaipur City"`.
     *
     * The destination, not the route, because the row sits in a day whose previous stop already
     * says where you are leaving from. Falls back to the service's own name and then to its
     * number — a booking with neither is not something to invent a destination for.
     */
    private fun journeyTitle(train: Train): String {
        val destination = train.destinationName.trim()
        return when {
            destination.isNotEmpty() -> "Train to $destination"
            train.name.isNotBlank() -> train.name.trim()
            else -> "Train ${train.number}".trim()
        }
    }

    private companion object {
        /** How far a planned journey may sit from a booked departure and still be the same one. */
        const val ADOPTION_WINDOW = 120L
    }
}
