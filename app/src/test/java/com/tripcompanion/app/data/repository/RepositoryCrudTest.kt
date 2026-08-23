package com.tripcompanion.app.data.repository

import com.tripcompanion.app.data.local.fake.InMemoryTripDatabase
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.PlannedPhoto
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Create, read, update and delete for every entity the user can touch (§28).
 *
 * These run the real repository implementations against an in-memory database, so
 * they cover the mapper on both legs of every round trip as well as the repository
 * behaviour layered on top of the DAO — the `updatedAt` stamp, the id allocation,
 * and the next-order counter.
 */
class RepositoryCrudTest {

    private lateinit var db: InMemoryTripDatabase
    private lateinit var trips: TripRepositoryImpl
    private lateinit var events: EventRepositoryImpl
    private lateinit var locations: LocationRepositoryImpl
    private lateinit var photos: PlannedPhotoRepositoryImpl

    @Before
    fun setUp() {
        db = InMemoryTripDatabase()
        trips = TripRepositoryImpl(db.tripDao)
        events = EventRepositoryImpl(db.eventDao)
        locations = LocationRepositoryImpl(db.locationDao)
        photos = PlannedPhotoRepositoryImpl(db.plannedPhotoDao)
    }

    // ---- Trip --------------------------------------------------------------

    @Test
    fun `a trip can be created, read back, edited and deleted`() = runTest {
        val id = trips.insertTrip(
            Trip(
                name = "Udaipur, four days",
                startDate = LocalDate.of(2026, 11, 3),
                endDate = LocalDate.of(2026, 11, 6)
            )
        )

        val created = trips.getTripById(id).first()
        assertNotNull(created)
        assertEquals("Udaipur, four days", created!!.name)
        assertEquals(TripStatus.PLANNING, created.status)

        trips.updateTrip(created.copy(name = "Udaipur, five days", endDate = LocalDate.of(2026, 11, 7)))

        val edited = trips.getTripById(id).first()!!
        assertEquals("Udaipur, five days", edited.name)
        assertEquals(LocalDate.of(2026, 11, 7), edited.endDate)
        assertEquals(id, edited.id)

        trips.deleteTrip(id)
        assertNull(trips.getTripById(id).first())
    }

    @Test
    fun `updating a trip refreshes updatedAt but not createdAt`() = runTest {
        val created = LocalDateTime.of(2026, 8, 1, 9, 0)
        val id = trips.insertTrip(
            Trip(
                name = "Trip",
                startDate = LocalDate.of(2026, 11, 3),
                endDate = LocalDate.of(2026, 11, 6),
                createdAt = created,
                updatedAt = created
            )
        )

        val beforeEdit = LocalDateTime.now()
        trips.updateTrip(trips.getTripById(id).first()!!.copy(name = "Renamed"))

        val after = trips.getTripById(id).first()!!
        assertEquals(created, after.createdAt)
        assertTrue(
            "updatedAt should be stamped at edit time, not carried over",
            !after.updatedAt.isBefore(beforeEdit)
        )
    }

    @Test
    fun `trips list in date order regardless of insertion order`() = runTest {
        trips.insertTrip(Trip(name = "Third", startDate = LocalDate.of(2027, 1, 1), endDate = LocalDate.of(2027, 1, 3)))
        trips.insertTrip(Trip(name = "First", startDate = LocalDate.of(2026, 3, 1), endDate = LocalDate.of(2026, 3, 3)))
        trips.insertTrip(Trip(name = "Second", startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 3)))

        assertEquals(
            listOf("First", "Second", "Third"),
            trips.getAllTrips().first().map { it.name }
        )
    }

    @Test
    fun `an empty database reports no trips rather than failing`() = runTest {
        assertEquals(emptyList<Trip>(), trips.getAllTrips().first())
        assertNull(trips.getTripById(999).first())
    }

    // ---- Event -------------------------------------------------------------

    @Test
    fun `an event can be created, read back, edited and deleted`() = runTest {
        val tripId = seedTrip()
        val id = events.insertEvent(
            Event(
                tripId = tripId,
                type = EventType.FOOD,
                title = "Rooftop dinner",
                startTime = LocalDateTime.of(2026, 11, 3, 20, 0),
                endTime = LocalDateTime.of(2026, 11, 3, 22, 0),
                whatWeAreDoing = "Book the corner table facing the lake."
            )
        )

        val created = events.getEventById(id).first()!!
        assertEquals(EventType.FOOD, created.type)
        assertEquals("Book the corner table facing the lake.", created.whatWeAreDoing)

        events.updateEvent(created.copy(title = "Rooftop dinner, later", status = EventStatus.COMPLETED))

        val edited = events.getEventById(id).first()!!
        assertEquals("Rooftop dinner, later", edited.title)
        assertEquals(EventStatus.COMPLETED, edited.status)

        events.deleteEvent(id)
        assertNull(events.getEventById(id).first())
    }

    @Test
    fun `an event keeps the location it was given`() = runTest {
        val tripId = seedTrip()
        val locationId = locations.insertLocation(
            Location(name = "City Palace", latitude = 24.5760, longitude = 73.6832)
        )
        val eventId = events.insertEvent(
            Event(
                tripId = tripId,
                title = "Palace tour",
                startTime = LocalDateTime.of(2026, 11, 3, 10, 0),
                endTime = LocalDateTime.of(2026, 11, 3, 13, 0),
                locationId = locationId
            )
        )

        val saved = events.getEventById(eventId).first()!!
        assertEquals(locationId, saved.locationId)
        assertEquals("City Palace", locations.getLocationByIdOnce(saved.locationId!!)!!.name)
    }

    @Test
    fun `an event's location can be cleared`() = runTest {
        val tripId = seedTrip()
        val locationId = locations.insertLocation(Location(name = "City Palace"))
        val eventId = events.insertEvent(
            Event(
                tripId = tripId,
                title = "Palace tour",
                startTime = LocalDateTime.of(2026, 11, 3, 10, 0),
                endTime = LocalDateTime.of(2026, 11, 3, 13, 0),
                locationId = locationId
            )
        )

        events.updateEvent(events.getEventById(eventId).first()!!.copy(locationId = null))

        assertNull(events.getEventById(eventId).first()!!.locationId)
        // Clearing the reference must not delete the saved place itself.
        assertNotNull(locations.getLocationByIdOnce(locationId))
    }

    @Test
    fun `next order climbs by one and survives a gap`() = runTest {
        val tripId = seedTrip()
        assertEquals(0, events.getNextOrder(tripId))

        events.insertEvent(sampleEvent(tripId, "One", order = 0))
        assertEquals(1, events.getNextOrder(tripId))

        events.insertEvent(sampleEvent(tripId, "Two", order = 1))
        events.insertEvent(sampleEvent(tripId, "Ten", order = 9))

        // MAX + 1, not COUNT: reusing 3 here would collide with the manual order 9
        // the moment the list is re-sorted.
        assertEquals(10, events.getNextOrder(tripId))
    }

    @Test
    fun `event order can be rewritten without touching anything else`() = runTest {
        val tripId = seedTrip()
        val id = events.insertEvent(sampleEvent(tripId, "One", order = 0))

        events.updateEventOrder(id, 7)

        val after = events.getEventById(id).first()!!
        assertEquals(7, after.order)
        assertEquals("One", after.title)
    }

    // ---- Location ----------------------------------------------------------

    @Test
    fun `a location can be created, read back, edited and deleted`() = runTest {
        val id = locations.insertLocation(
            Location(
                name = "City Palace",
                address = "Old City, Udaipur",
                latitude = 24.5760,
                longitude = 73.6832,
                category = "Historic",
                providerPlaceId = "osm_9876",
                providerName = "OpenStreetMap"
            )
        )

        val created = locations.getLocationByIdOnce(id)!!
        assertEquals("City Palace", created.name)
        assertEquals("osm_9876", created.providerPlaceId)

        locations.updateLocation(created.copy(name = "City Palace Museum", latitude = 24.5761))

        val edited = locations.getLocationById(id).first()!!
        assertEquals("City Palace Museum", edited.name)
        assertEquals(24.5761, edited.latitude, 0.0000001)

        locations.deleteLocation(id)
        assertNull(locations.getLocationByIdOnce(id))
    }

    @Test
    fun `a saved location reads back with no network involved`() = runTest {
        // §13: everything the map needs is on the row. There is no provider call on
        // this path — the repository only ever touches the database.
        val id = locations.insertLocation(
            Location(name = "Ambrai Ghat", latitude = 24.5789, longitude = 73.6796)
        )

        val saved = locations.getLocationByIdOnce(id)!!

        assertEquals(24.5789, saved.latitude, 0.0000001)
        assertEquals(73.6796, saved.longitude, 0.0000001)
    }

    @Test
    fun `locations list alphabetically`() = runTest {
        locations.insertLocation(Location(name = "Saheliyon ki Bari"))
        locations.insertLocation(Location(name = "Ambrai Ghat"))
        locations.insertLocation(Location(name = "Jagdish Temple"))

        assertEquals(
            listOf("Ambrai Ghat", "Jagdish Temple", "Saheliyon ki Bari"),
            locations.getAllLocations().first().map { it.name }
        )
    }

    // ---- Planned photo -----------------------------------------------------

    @Test
    fun `a photo can be created, read back, edited and deleted`() = runTest {
        val eventId = seedEvent()
        val id = photos.insertPhoto(
            PlannedPhoto(
                eventId = eventId,
                title = "Palace doorway",
                referenceImageUri = "/files/planned_photos/p_1.jpg"
            )
        )

        val created = photos.getPhotoById(id).first()!!
        assertEquals("Palace doorway", created.title)

        photos.updatePhoto(created.copy(title = "Blue doorway"))
        assertEquals("Blue doorway", photos.getPhotoById(id).first()!!.title)

        photos.deletePhoto(id)
        assertNull(photos.getPhotoById(id).first())
    }

    @Test
    fun `a photo saves with an image and no text at all`() = runTest {
        // §15 states this outright: a title is optional.
        val eventId = seedEvent()
        val id = photos.insertPhoto(
            PlannedPhoto(eventId = eventId, referenceImageUri = "/files/planned_photos/p_2.jpg")
        )

        val saved = photos.getPhotoById(id).first()!!

        assertEquals("", saved.title)
        assertEquals("/files/planned_photos/p_2.jpg", saved.referenceImageUri)
    }

    @Test
    fun `photos list in their own order, per event`() = runTest {
        val eventId = seedEvent()
        photos.insertPhoto(PlannedPhoto(eventId = eventId, title = "Third", order = 2))
        photos.insertPhoto(PlannedPhoto(eventId = eventId, title = "First", order = 0))
        photos.insertPhoto(PlannedPhoto(eventId = eventId, title = "Second", order = 1))

        assertEquals(
            listOf("First", "Second", "Third"),
            photos.getPhotosForEvent(eventId).first().map { it.title }
        )
        assertEquals(3, photos.getNextOrder(eventId))
    }

    @Test
    fun `an event with no photos returns an empty list, not null`() = runTest {
        assertEquals(emptyList<PlannedPhoto>(), photos.getPhotosForEvent(seedEvent()).first())
    }

    private suspend fun seedTrip(): Long = trips.insertTrip(
        Trip(
            name = "Trip",
            startDate = LocalDate.of(2026, 11, 3),
            endDate = LocalDate.of(2026, 11, 6)
        )
    )

    private suspend fun seedEvent(): Long = events.insertEvent(sampleEvent(seedTrip(), "A stop"))

    private fun sampleEvent(tripId: Long, title: String, order: Int = 0) = Event(
        tripId = tripId,
        title = title,
        startTime = LocalDateTime.of(2026, 11, 3, 10, 0),
        endTime = LocalDateTime.of(2026, 11, 3, 12, 0),
        order = order
    )
}
