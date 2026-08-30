package com.tripcompanion.app.feature.map

import androidx.lifecycle.SavedStateHandle
import com.tripcompanion.app.core.time.FixedTimeProvider
import com.tripcompanion.app.data.SampleTripSeeder
import com.tripcompanion.app.data.local.fake.InMemoryTripDatabase
import com.tripcompanion.app.data.repository.EventRepositoryImpl
import com.tripcompanion.app.data.repository.LocationRepositoryImpl
import com.tripcompanion.app.data.repository.PlannedPhotoRepositoryImpl
import com.tripcompanion.app.data.repository.StayDetailsRepositoryImpl
import com.tripcompanion.app.data.repository.TrainRepositoryImpl
import com.tripcompanion.app.data.repository.TripRepositoryImpl
import com.tripcompanion.app.domain.model.RouteLegStatus
import com.tripcompanion.app.domain.service.DeviceLocation
import com.tripcompanion.app.domain.service.DeviceLocationProvider
import com.tripcompanion.app.domain.service.RoutePlanError
import com.tripcompanion.app.domain.service.RoutePlanOutcome
import com.tripcompanion.app.domain.service.RoutePlanService
import com.tripcompanion.app.domain.service.RoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Unit test for the Day Route Map prototype with 1-day Agra itinerary.
 * Verifies the 7 stops in sequence, independent route legs, and status calculations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgraDayRouteMapTest {

    private val dispatcher = StandardTestDispatcher()

    // 8:30 AM on Day 1 — traveler has completed Cantonment and Tajview (Rest/Breakfast), currently arriving at Taj Mahal
    private val clock = FixedTimeProvider(LocalDateTime.of(2026, 11, 3, 8, 30))

    private lateinit var db: InMemoryTripDatabase
    private lateinit var tripRepo: TripRepositoryImpl
    private lateinit var eventRepo: EventRepositoryImpl
    private lateinit var locationRepo: LocationRepositoryImpl
    private lateinit var seeder: SampleTripSeeder

    private var agraTripId = 0L

    @Before
    fun setUp() = runTest(dispatcher) {
        Dispatchers.setMain(dispatcher)
        db = InMemoryTripDatabase()
        tripRepo = TripRepositoryImpl(db.tripDao)
        eventRepo = EventRepositoryImpl(db.eventDao)
        locationRepo = LocationRepositoryImpl(db.locationDao)

        val trainRepo = TrainRepositoryImpl(
            trainDao = db.trainDao,
            trainPassengerDao = db.trainPassengerDao,
            trainStopDao = db.trainStopDao,
            trainRunStatusDao = db.trainRunStatusDao
        )

        seeder = SampleTripSeeder(
            tripRepository = tripRepo,
            eventRepository = eventRepo,
            locationRepository = locationRepo,
            trainRepository = trainRepo,
            stayDetailsRepository = StayDetailsRepositoryImpl(db.stayDetailsDao),
            photoRepository = PlannedPhotoRepositoryImpl(db.plannedPhotoDao),
            timeProvider = clock
        )

        agraTripId = seeder.seedAgraTrip()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `agra trip has exactly 7 stops in visiting order`() = runTest(dispatcher) {
        val vm = mapViewModel()
        val state = vm.state.value

        assertEquals(7, state.pins.size)
        val titles = state.pins.map { it.event.title }
        assertEquals(
            listOf(
                "Agra Cantt Railway Station",
                "Hotel Rest & Breakfast",
                "Taj Mahal",
                "Lunch at Pinch of Spice",
                "Agra Fort",
                "Mehtab Bagh Sunset",
                "Hotel / End of Day"
            ),
            titles
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), state.pins.map { it.orderInDay })
    }

    @Test
    fun `agra route contains 6 legs connecting the 7 consecutive stops`() = runTest(dispatcher) {
        val vm = mapViewModel()
        val state = vm.state.value

        assertEquals(6, state.legs.size)
        // Leg 1: Station -> Hotel (completed)
        // Leg 2: Hotel -> Taj Mahal (arrival / current focus)
        // Leg 3: Taj Mahal -> Lunch (future)
        // Leg 4: Lunch -> Agra Fort (future)
        // Leg 5: Agra Fort -> Mehtab Bagh (future)
        // Leg 6: Mehtab Bagh -> Hotel (future)
        assertEquals("Agra Cantt Railway Station", state.legs[0].fromName)
        assertEquals("Hotel Rest & Breakfast", state.legs[0].toName)
        assertEquals(RouteLegStatus.COMPLETED, state.legs[0].status)

        assertEquals("Hotel Rest & Breakfast", state.legs[1].fromName)
        assertEquals("Taj Mahal", state.legs[1].toName)
        // At 8:30 AM, Taj Mahal is starting/current
        assertTrue(
            state.legs[1].status == RouteLegStatus.CURRENT ||
            state.legs[1].status == RouteLegStatus.COMPLETED
        )

        // Future legs
        assertEquals(RouteLegStatus.FUTURE, state.legs[2].status)
        assertEquals(RouteLegStatus.FUTURE, state.legs[3].status)
        assertEquals(RouteLegStatus.FUTURE, state.legs[4].status)
        assertEquals(RouteLegStatus.FUTURE, state.legs[5].status)
    }

    @Test
    fun `focus latitude and longitude point to the current or next stop`() = runTest(dispatcher) {
        val vm = mapViewModel()
        val state = vm.state.value

        assertNotNull(state.focusEventId)
        val focusPin = state.pins.firstOrNull { it.event.id == state.focusEventId }
        assertNotNull(focusPin)
        assertEquals("Taj Mahal", focusPin?.event?.title)
        assertEquals(27.1751, state.focusLatitude, 0.001)
        assertEquals(78.0421, state.focusLongitude, 0.001)
    }

    private fun TestScope.mapViewModel(): TripMapViewModel {
        val vm = TripMapViewModel(
            savedStateHandle = SavedStateHandle(mapOf("tripId" to agraTripId.toString())),
            tripRepository = tripRepo,
            eventRepository = eventRepo,
            locationRepository = locationRepo,
            routePlanService = FakeRoutePlanService(),
            deviceLocationProvider = FakeDeviceLocationProvider(),
            timeProvider = clock
        )
        advanceUntilIdle()
        return vm
    }

    private class FakeRoutePlanService : RoutePlanService {
        override suspend fun planRoute(waypoints: List<RoutePoint>): RoutePlanOutcome =
            RoutePlanOutcome.Unavailable(RoutePlanError.NOT_CONFIGURED)
    }

    private class FakeDeviceLocationProvider : DeviceLocationProvider {
        override fun locationUpdates(): Flow<DeviceLocation?> = flowOf(null)
    }
}
