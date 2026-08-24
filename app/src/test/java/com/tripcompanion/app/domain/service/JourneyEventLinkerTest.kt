package com.tripcompanion.app.domain.service

import com.tripcompanion.app.data.local.fake.InMemoryTripDatabase
import com.tripcompanion.app.data.repository.EventRepositoryImpl
import com.tripcompanion.app.data.repository.LocationRepositoryImpl
import com.tripcompanion.app.data.repository.TrainRepositoryImpl
import com.tripcompanion.app.data.repository.TripRepositoryImpl
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.Trip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class JourneyEventLinkerTest {

    private lateinit var db: InMemoryTripDatabase
    private lateinit var trips: TripRepositoryImpl
    private lateinit var events: EventRepositoryImpl
    private lateinit var trains: TrainRepositoryImpl
    private lateinit var locations: LocationRepositoryImpl

    private var tripId = 0L

    class FakeLocationSearchService(
        private val places: List<SearchResultLocation> = emptyList()
    ) : LocationSearchService {
        override suspend fun searchPlaces(query: String, viewport: SearchViewport?): LocationSearchOutcome {
            return if (places.isEmpty()) LocationSearchOutcome.Empty else LocationSearchOutcome.Results(places)
        }
    }

    @Before
    fun setUp() {
        db = InMemoryTripDatabase()
        trips = TripRepositoryImpl(db.tripDao)
        events = EventRepositoryImpl(db.eventDao)
        trains = TrainRepositoryImpl(
            db.trainDao, db.trainPassengerDao, db.trainStopDao, db.trainRunStatusDao
        )
        locations = LocationRepositoryImpl(db.locationDao)
    }

    private suspend fun seedTrip(): Long {
        return trips.insertTrip(
            Trip(
                name = "Golden Triangle",
                startDate = LocalDate.of(2026, 11, 1),
                endDate = LocalDate.of(2026, 11, 5)
            )
        )
    }

    @Test
    fun `syncEvent creates location for train origin station and attaches locationId`() = runTest {
        tripId = seedTrip()
        val fakeSearch = FakeLocationSearchService(
            listOf(
                SearchResultLocation(
                    name = "Jaipur Junction Railway Station",
                    formattedAddress = "Station Road, Jaipur, Rajasthan",
                    latitude = 26.9196,
                    longitude = 75.7878,
                    category = "Transit"
                )
            )
        )
        val linker = JourneyEventLinker(events, trains, locations, fakeSearch)

        val train = Train(
            tripId = tripId,
            number = "12958",
            name = "Rajdhani Express",
            originName = "Jaipur Junction",
            originCode = "JP",
            departureTime = LocalDateTime.of(2026, 11, 1, 23, 55),
            destinationName = "New Delhi",
            destinationCode = "NDLS",
            arrivalTime = LocalDateTime.of(2026, 11, 2, 6, 0)
        )

        val eventId = linker.syncEvent(train, previous = null)
        val createdEvent = events.getEventById(eventId).first()

        assertNotNull(createdEvent)
        assertEquals(EventType.JOURNEY, createdEvent!!.type)
        assertEquals("Train to New Delhi", createdEvent.title)
        assertNotNull(createdEvent.locationId)

        val createdLoc = locations.getLocationById(createdEvent.locationId!!).first()
        assertNotNull(createdLoc)
        assertEquals("Jaipur Junction", createdLoc!!.name)
        assertEquals(26.9196, createdLoc.latitude, 0.0001)
        assertEquals(75.7878, createdLoc.longitude, 0.0001)
        assertEquals("Station Road, Jaipur, Rajasthan", createdLoc.address)
    }

    @Test
    fun `syncEvent uses existing location if matching station already exists`() = runTest {
        tripId = seedTrip()
        val existingLocId = locations.insertLocation(
            Location(
                name = "New Delhi",
                address = "Paharganj, New Delhi",
                latitude = 28.6429,
                longitude = 77.2195,
                category = "Transit"
            )
        )
        val fakeSearch = FakeLocationSearchService()
        val linker = JourneyEventLinker(events, trains, locations, fakeSearch)

        val train = Train(
            tripId = tripId,
            number = "12002",
            name = "Shatabdi Express",
            originName = "New Delhi",
            originCode = "NDLS",
            departureTime = LocalDateTime.of(2026, 11, 1, 6, 0),
            destinationName = "Agra Cantt",
            destinationCode = "AGC",
            arrivalTime = LocalDateTime.of(2026, 11, 1, 8, 0)
        )

        val eventId = linker.syncEvent(train, previous = null)
        val createdEvent = events.getEventById(eventId).first()

        assertNotNull(createdEvent)
        assertEquals(existingLocId, createdEvent!!.locationId)
        // No duplicate location inserted
        assertEquals(1, locations.getAllLocations().first().size)
    }
}
