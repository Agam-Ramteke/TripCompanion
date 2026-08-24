package com.tripcompanion.app.feature.event

import androidx.lifecycle.SavedStateHandle
import com.tripcompanion.app.core.time.FixedTimeProvider
import com.tripcompanion.app.data.local.fake.InMemoryTripDatabase
import com.tripcompanion.app.data.repository.EventRepositoryImpl
import com.tripcompanion.app.data.repository.LocationRepositoryImpl
import com.tripcompanion.app.data.repository.TripRepositoryImpl
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class EventEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = FixedTimeProvider(LocalDateTime.of(2026, 11, 3, 9, 0))

    private lateinit var db: InMemoryTripDatabase
    private lateinit var trips: TripRepositoryImpl
    private lateinit var events: EventRepositoryImpl
    private lateinit var locations: LocationRepositoryImpl

    private var tripId = 0L

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = InMemoryTripDatabase()
        trips = TripRepositoryImpl(db.tripDao)
        events = EventRepositoryImpl(db.eventDao)
        locations = LocationRepositoryImpl(db.locationDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun seedTrip(): Long {
        return trips.insertTrip(
            Trip(
                name = "Test Journey",
                startDate = LocalDate.of(2026, 11, 1),
                endDate = LocalDate.of(2026, 11, 5)
            )
        )
    }

    @Test
    fun `default dates and times for new activity open on clock time`() = runTest(dispatcher) {
        tripId = seedTrip()
        val handle = SavedStateHandle(mapOf("tripId" to tripId.toString()))
        val vm = EventEditorViewModel(handle, events, locations, clock)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(LocalDate.of(2026, 11, 3), state.startDate)
        assertEquals(LocalDate.of(2026, 11, 3), state.endDate)
        assertEquals(LocalTime.of(9, 0), state.startTime)
        assertEquals(LocalTime.of(10, 0), state.endTime)
        assertFalse(state.isOvernight)
        assertTrue(state.isTimeRangeValid)
    }

    @Test
    fun `switching to STAY switches default times to check-in 14-00 and check-out next day 10-30`() = runTest(dispatcher) {
        tripId = seedTrip()
        val handle = SavedStateHandle(mapOf("tripId" to tripId.toString()))
        val vm = EventEditorViewModel(handle, events, locations, clock)
        advanceUntilIdle()

        vm.updateType(EventType.STAY)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(EventType.STAY, state.type)
        assertEquals(LocalDate.of(2026, 11, 3), state.startDate)
        assertEquals(LocalDate.of(2026, 11, 4), state.endDate)
        assertEquals(LocalTime.of(14, 0), state.startTime)
        assertEquals(LocalTime.of(10, 30), state.endTime)
        assertTrue(state.isTimeRangeValid)
    }

    @Test
    fun `saving a multi-day stay saves accurate start and end datetimes`() = runTest(dispatcher) {
        tripId = seedTrip()
        val handle = SavedStateHandle(mapOf("tripId" to tripId.toString()))
        val vm = EventEditorViewModel(handle, events, locations, clock)
        advanceUntilIdle()

        vm.updateType(EventType.STAY)
        vm.updateTitle("Grand Heritage Palace")
        vm.updateStartDate(LocalDate.of(2026, 11, 2))
        vm.updateEndDate(LocalDate.of(2026, 11, 4))
        vm.updateStartTime(LocalTime.of(15, 0))
        vm.updateEndTime(LocalTime.of(11, 0))
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.saved)
        val storedEvents = events.getEventsForTrip(tripId).first()
        assertEquals(1, storedEvents.size)
        val saved = storedEvents.first()
        assertEquals("Grand Heritage Palace", saved.title)
        assertEquals(EventType.STAY, saved.type)
        assertEquals(LocalDateTime.of(2026, 11, 2, 15, 0), saved.startTime)
        assertEquals(LocalDateTime.of(2026, 11, 4, 11, 0), saved.endTime)
    }

    @Test
    fun `validation fails when end datetime is before start datetime`() = runTest(dispatcher) {
        tripId = seedTrip()
        val handle = SavedStateHandle(mapOf("tripId" to tripId.toString()))
        val vm = EventEditorViewModel(handle, events, locations, clock)
        advanceUntilIdle()

        vm.updateTitle("Invalid Timing Event")
        vm.updateStartTime(LocalTime.of(15, 0))
        vm.updateEndTime(LocalTime.of(10, 0))
        advanceUntilIdle()

        assertFalse(vm.state.value.isTimeRangeValid)
        vm.save()
        advanceUntilIdle()

        assertFalse(vm.state.value.saved)
        assertNotNull(vm.state.value.validationError)
    }
}
