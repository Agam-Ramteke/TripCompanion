package com.tripcompanion.app.data.repository

import com.tripcompanion.app.data.local.fake.InMemoryTripDatabase
import com.tripcompanion.app.domain.model.Activity
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.PlannedPhoto
import com.tripcompanion.app.domain.model.Trip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Multi-trip isolation (§28).
 *
 * The app is generic (§4), which means two trips that know nothing about each other
 * have to coexist without either leaking into the other's screens. These tests run
 * the real repositories over an in-memory database so the isolation being checked is
 * the one the queries actually enforce, not one a fake reimplements.
 */
class MultiTripIsolationTest {

    private lateinit var db: InMemoryTripDatabase
    private lateinit var trips: TripRepositoryImpl
    private lateinit var events: EventRepositoryImpl
    private lateinit var locations: LocationRepositoryImpl
    private lateinit var activities: ActivityRepositoryImpl
    private lateinit var photos: PlannedPhotoRepositoryImpl

    @Before
    fun setUp() {
        db = InMemoryTripDatabase()
        trips = TripRepositoryImpl(db.tripDao)
        events = EventRepositoryImpl(db.eventDao)
        locations = LocationRepositoryImpl(db.locationDao)
        activities = ActivityRepositoryImpl(db.activityDao)
        photos = PlannedPhotoRepositoryImpl(db.plannedPhotoDao)
    }

    @Test
    fun `two trips get distinct ids`() = runTest {
        val a = trips.insertTrip(trip("Northern Mountains Trek", LocalDate.of(2026, 9, 1)))
        val b = trips.insertTrip(trip("Coastal Cycling Tour", LocalDate.of(2026, 10, 10)))

        assertNotEquals(a, b)
        assertEquals(2, trips.getAllTrips().first().size)
    }

    @Test
    fun `a trip only ever sees its own events`() = runTest {
        val (tripA, tripB) = twoTrips()

        events.insertEvent(event(tripA, "Basecamp Arrival", type = EventType.STAY))
        events.insertEvent(event(tripA, "Summit Push", type = EventType.JOURNEY))
        events.insertEvent(event(tripB, "Beachside Sunset Ride", type = EventType.JOURNEY))

        val forA = events.getEventsForTrip(tripA).first()
        val forB = events.getEventsForTrip(tripB).first()

        assertEquals(listOf("Basecamp Arrival", "Summit Push"), forA.map { it.title })
        assertEquals(listOf("Beachside Sunset Ride"), forB.map { it.title })
        assertTrue(forA.all { it.tripId == tripA })
        assertTrue(forB.all { it.tripId == tripB })
    }

    @Test
    fun `an event id from one trip is not readable as another trip's event`() = runTest {
        val (tripA, tripB) = twoTrips()
        val eventA = events.insertEvent(event(tripA, "Basecamp Arrival"))

        val fetched = events.getEventById(eventA).first()

        assertNotNull(fetched)
        assertEquals(tripA, fetched!!.tripId)
        assertFalse(events.getEventsForTrip(tripB).first().any { it.id == eventA })
    }

    @Test
    fun `order counters are per trip, not global`() = runTest {
        val (tripA, tripB) = twoTrips()

        // §10 orders within a trip. A global counter would make trip B's first
        // stop sort as though it were the third stop of the day.
        assertEquals(0, events.getNextOrder(tripA))
        assertEquals(0, events.getNextOrder(tripB))

        events.insertEvent(event(tripA, "One", order = 0))
        events.insertEvent(event(tripA, "Two", order = 1))

        assertEquals(2, events.getNextOrder(tripA))
        assertEquals(0, events.getNextOrder(tripB))
    }

    @Test
    fun `photos and activities stay attached to their own event`() = runTest {
        val (tripA, tripB) = twoTrips()
        val eventA = events.insertEvent(event(tripA, "Basecamp Arrival"))
        val eventB = events.insertEvent(event(tripB, "Beachside Sunset Ride"))

        photos.insertPhoto(PlannedPhoto(eventId = eventA, title = "Tent under stars"))
        photos.insertPhoto(PlannedPhoto(eventId = eventB, title = "Bicycle on sand"))
        activities.insertActivity(Activity(eventId = eventA, title = "Pitch tents"))
        activities.insertActivity(Activity(eventId = eventB, title = "Check tyre pressure"))

        assertEquals(
            listOf("Tent under stars"),
            photos.getPhotosForEvent(eventA).first().map { it.title }
        )
        assertEquals(
            listOf("Bicycle on sand"),
            photos.getPhotosForEvent(eventB).first().map { it.title }
        )
        assertEquals(
            listOf("Pitch tents"),
            activities.getActivitiesForEvent(eventA).first().map { it.title }
        )
    }

    @Test
    fun `deleting a trip leaves the other trip completely intact`() = runTest {
        val (tripA, tripB) = twoTrips()
        val eventA = events.insertEvent(event(tripA, "Basecamp Arrival"))
        val eventB = events.insertEvent(event(tripB, "Beachside Sunset Ride"))
        photos.insertPhoto(PlannedPhoto(eventId = eventA, title = "Tent under stars"))
        photos.insertPhoto(PlannedPhoto(eventId = eventB, title = "Bicycle on sand"))
        activities.insertActivity(Activity(eventId = eventA, title = "Pitch tents"))
        activities.insertActivity(Activity(eventId = eventB, title = "Check tyre pressure"))

        trips.deleteTrip(tripA)

        assertNull(trips.getTripById(tripA).first())
        assertNotNull(trips.getTripById(tripB).first())
        assertEquals(emptyList<Event>(), events.getEventsForTrip(tripA).first())
        assertEquals(1, events.getEventsForTrip(tripB).first().size)
        assertEquals(
            listOf("Bicycle on sand"),
            photos.getPhotosForEvent(eventB).first().map { it.title }
        )
        assertEquals(
            listOf("Check tyre pressure"),
            activities.getActivitiesForEvent(eventB).first().map { it.title }
        )
    }

    @Test
    fun `deleting a trip cascades all the way down to its photos`() = runTest {
        val (tripA, _) = twoTrips()
        val eventA = events.insertEvent(event(tripA, "Basecamp Arrival"))
        photos.insertPhoto(PlannedPhoto(eventId = eventA, title = "Tent under stars"))
        activities.insertActivity(Activity(eventId = eventA, title = "Pitch tents"))

        trips.deleteTrip(tripA)

        // Orphaned photo and activity rows would otherwise accumulate invisibly and
        // keep image files alive in app-private storage forever.
        assertEquals(emptyList<PlannedPhoto>(), photos.getPhotosForEvent(eventA).first())
        assertEquals(emptyList<Activity>(), activities.getActivitiesForEvent(eventA).first())
    }

    @Test
    fun `deleting one event does not disturb its siblings`() = runTest {
        val (tripA, _) = twoTrips()
        val first = events.insertEvent(event(tripA, "One", order = 0))
        val second = events.insertEvent(event(tripA, "Two", order = 1))
        photos.insertPhoto(PlannedPhoto(eventId = second, title = "Keep me"))

        events.deleteEvent(first)

        assertEquals(listOf("Two"), events.getEventsForTrip(tripA).first().map { it.title })
        assertEquals(listOf("Keep me"), photos.getPhotosForEvent(second).first().map { it.title })
    }

    @Test
    fun `locations are shared, not owned by a trip`() = runTest {
        val (tripA, tripB) = twoTrips()

        // §13 stores places once. Two trips visiting the same place should point at
        // the same saved row rather than each keeping a private copy.
        val palace = locations.insertLocation(
            Location(name = "City Palace", address = "Udaipur", latitude = 24.576, longitude = 73.683)
        )
        events.insertEvent(event(tripA, "Palace tour").copy(locationId = palace))
        events.insertEvent(event(tripB, "Palace again").copy(locationId = palace))

        assertEquals(palace, events.getEventsForTrip(tripA).first().single().locationId)
        assertEquals(palace, events.getEventsForTrip(tripB).first().single().locationId)
        assertEquals(1, locations.getAllLocations().first().size)
    }

    @Test
    fun `deleting a trip keeps its saved locations for the next trip`() = runTest {
        val (tripA, _) = twoTrips()
        val palace = locations.insertLocation(Location(name = "City Palace"))
        events.insertEvent(event(tripA, "Palace tour").copy(locationId = palace))

        trips.deleteTrip(tripA)

        assertNotNull(locations.getLocationByIdOnce(palace))
    }

    @Test
    fun `flows for one trip re-emit when only the other trip changes`() = runTest {
        // Not a bug, but worth pinning: the fake shares one store per table, so a
        // write anywhere re-emits. What must hold is that the *contents* handed to
        // trip A never mention trip B.
        val (tripA, tripB) = twoTrips()
        events.insertEvent(event(tripA, "Mine"))
        events.insertEvent(event(tripB, "Theirs"))

        assertEquals(listOf("Mine"), events.getEventsForTrip(tripA).first().map { it.title })
    }

    private suspend fun twoTrips(): Pair<Long, Long> {
        val a = trips.insertTrip(trip("Northern Mountains Trek", LocalDate.of(2026, 9, 1)))
        val b = trips.insertTrip(trip("Coastal Cycling Tour", LocalDate.of(2026, 10, 10)))
        return a to b
    }

    private fun trip(name: String, start: LocalDate) = Trip(
        name = name,
        startDate = start,
        endDate = start.plusDays(4)
    )

    private fun event(
        tripId: Long,
        title: String,
        type: EventType = EventType.VISIT,
        order: Int = 0,
        start: LocalDateTime = LocalDateTime.of(2026, 9, 1, 14, 0)
    ) = Event(
        tripId = tripId,
        type = type,
        title = title,
        startTime = start,
        endTime = start.plusHours(2),
        order = order
    )
}
