package com.tripcompanion.app.feature.trip

import androidx.lifecycle.SavedStateHandle
import com.tripcompanion.app.core.time.FixedTimeProvider
import com.tripcompanion.app.data.local.fake.InMemoryTripDatabase
import com.tripcompanion.app.data.prefs.UserPreferences
import com.tripcompanion.app.data.prefs.UserPreferencesStore
import com.tripcompanion.app.data.repository.EventRepositoryImpl
import com.tripcompanion.app.data.repository.LocationRepositoryImpl
import com.tripcompanion.app.data.repository.StayDetailsRepositoryImpl
import com.tripcompanion.app.data.repository.TrainRepositoryImpl
import com.tripcompanion.app.data.repository.TripRepositoryImpl
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.service.JourneyEventLinker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class TimelineViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedTimeProvider(LocalDateTime.of(2026, 11, 1, 9, 0))

    private lateinit var db: InMemoryTripDatabase
    private lateinit var trips: TripRepositoryImpl
    private lateinit var events: EventRepositoryImpl
    private lateinit var locations: LocationRepositoryImpl
    private lateinit var stayDetails: StayDetailsRepositoryImpl
    private lateinit var trains: TrainRepositoryImpl
    private lateinit var linker: JourneyEventLinker
    private lateinit var prefs: FakeUserPreferencesStore

    private var tripId = 0L

    class FakeUserPreferencesStore(
        initial: UserPreferences = UserPreferences()
    ) : UserPreferencesStore() {
        private val _prefs = MutableStateFlow(initial)
        override val preferences: StateFlow<UserPreferences> = _prefs

        override fun setShowCompletedActivities(show: Boolean) {
            _prefs.value = _prefs.value.copy(showCompletedActivities = show)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = InMemoryTripDatabase()
        trips = TripRepositoryImpl(db.tripDao)
        events = EventRepositoryImpl(db.eventDao)
        locations = LocationRepositoryImpl(db.locationDao)
        stayDetails = StayDetailsRepositoryImpl(db.stayDetailsDao)
        trains = TrainRepositoryImpl(
            db.trainDao, db.trainPassengerDao, db.trainStopDao, db.trainRunStatusDao
        )
        linker = JourneyEventLinker(events, trains, locations, null)
        prefs = FakeUserPreferencesStore()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun seedTrip(): Long {
        return trips.insertTrip(
            Trip(
                name = "Rajasthan Adventure",
                startDate = LocalDate.of(2026, 11, 1),
                endDate = LocalDate.of(2026, 11, 4)
            )
        )
    }

    @Test
    fun `single day event appears only on its date`() = runTest(dispatcher) {
        tripId = seedTrip()
        events.insertEvent(
            Event(
                tripId = tripId,
                type = EventType.VISIT,
                title = "Amber Fort Visit",
                startTime = LocalDateTime.of(2026, 11, 1, 10, 0),
                endTime = LocalDateTime.of(2026, 11, 1, 13, 0)
            )
        )

        val handle = SavedStateHandle(mapOf("tripId" to tripId.toString()))
        val vm = TimelineViewModel(
            handle, trips, events, locations, stayDetails, trains, linker, prefs, clock
        )
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(4, state.days.size)
        // Day 1 (Nov 1)
        assertEquals(1, state.days[0].eventCount)
        // Day 2 (Nov 2)
        assertEquals(0, state.days[1].eventCount)

        vm.selectDay(LocalDate.of(2026, 11, 1))
        advanceUntilIdle()
        val day1Events = vm.state.value.dayEvents
        assertEquals(1, day1Events.size)
        assertEquals("Amber Fort Visit", day1Events.first().event.title)
        assertEquals(LocalTime.of(10, 0), day1Events.first().displayTime)
        assertFalse(day1Events.first().isCheckIn)
        assertFalse(day1Events.first().isCheckOut)
    }

    @Test
    fun `multi-day STAY appears as check-in on start day and check-out on end day`() = runTest(dispatcher) {
        tripId = seedTrip()
        events.insertEvent(
            Event(
                tripId = tripId,
                type = EventType.STAY,
                title = "Umaid Bhawan",
                startTime = LocalDateTime.of(2026, 11, 2, 14, 0),
                endTime = LocalDateTime.of(2026, 11, 4, 10, 30)
            )
        )

        val handle = SavedStateHandle(mapOf("tripId" to tripId.toString()))
        val vm = TimelineViewModel(
            handle, trips, events, locations, stayDetails, trains, linker, prefs, clock
        )
        advanceUntilIdle()

        val state = vm.state.value
        // Day 1 (Nov 1): 0 events
        assertEquals(0, state.days[0].eventCount)
        // Day 2 (Nov 2): 1 event (Check-in)
        assertEquals(1, state.days[1].eventCount)
        // Day 3 (Nov 3): 0 events
        assertEquals(0, state.days[2].eventCount)
        // Day 4 (Nov 4): 1 event (Check-out)
        assertEquals(1, state.days[3].eventCount)

        // Select Nov 2 -> Check-in
        vm.selectDay(LocalDate.of(2026, 11, 2))
        advanceUntilIdle()
        val nov2Events = vm.state.value.dayEvents
        assertEquals(1, nov2Events.size)
        assertEquals("Umaid Bhawan", nov2Events.first().event.title)
        assertEquals(LocalTime.of(14, 0), nov2Events.first().displayTime)
        assertTrue(nov2Events.first().isCheckIn)
        assertFalse(nov2Events.first().isCheckOut)

        // Select Nov 4 -> Check-out
        vm.selectDay(LocalDate.of(2026, 11, 4))
        advanceUntilIdle()
        val nov4Events = vm.state.value.dayEvents
        assertEquals(1, nov4Events.size)
        assertEquals("Umaid Bhawan", nov4Events.first().event.title)
        assertEquals(LocalTime.of(10, 30), nov4Events.first().displayTime)
        assertFalse(nov4Events.first().isCheckIn)
        assertTrue(nov4Events.first().isCheckOut)
    }

    @Test
    fun `overnight JOURNEY appears on departure day and arrival day`() = runTest(dispatcher) {
        tripId = seedTrip()
        val trainId = trains.insertTrain(
            Train(
                tripId = tripId,
                number = "12958",
                name = "Swarna Jayanti Rajdhani",
                originName = "Jaipur Junction",
                originCode = "JP",
                departureTime = LocalDateTime.of(2026, 11, 1, 23, 55),
                destinationName = "New Delhi",
                destinationCode = "NDLS",
                arrivalTime = LocalDateTime.of(2026, 11, 2, 6, 0)
            )
        )
        // Sync train to event
        val train = Train(
            id = trainId,
            tripId = tripId,
            number = "12958",
            name = "Swarna Jayanti Rajdhani",
            originName = "Jaipur Junction",
            originCode = "JP",
            departureTime = LocalDateTime.of(2026, 11, 1, 23, 55),
            destinationName = "New Delhi",
            destinationCode = "NDLS",
            arrivalTime = LocalDateTime.of(2026, 11, 2, 6, 0)
        )
        val eventId = linker.syncEvent(train, previous = null)
        trains.updateTrain(train.copy(eventId = eventId))

        val handle = SavedStateHandle(mapOf("tripId" to tripId.toString()))
        val vm = TimelineViewModel(
            handle, trips, events, locations, stayDetails, trains, linker, prefs, clock
        )
        advanceUntilIdle()

        // Check Day 1 (Nov 1 departure)
        vm.selectDay(LocalDate.of(2026, 11, 1))
        advanceUntilIdle()
        val day1Events = vm.state.value.dayEvents
        assertEquals(1, day1Events.size)
        assertEquals(LocalTime.of(23, 55), day1Events.first().displayTime)
        assertTrue(day1Events.first().isTrainDeparture)
        assertFalse(day1Events.first().isTrainArrival)

        // Check Day 2 (Nov 2 arrival)
        vm.selectDay(LocalDate.of(2026, 11, 2))
        advanceUntilIdle()
        val day2Events = vm.state.value.dayEvents
        assertEquals(1, day2Events.size)
        assertEquals(LocalTime.of(6, 0), day2Events.first().displayTime)
        assertFalse(day2Events.first().isTrainDeparture)
        assertTrue(day2Events.first().isTrainArrival)
    }
}
