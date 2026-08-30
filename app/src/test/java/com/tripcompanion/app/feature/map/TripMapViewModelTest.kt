package com.tripcompanion.app.feature.map

import androidx.lifecycle.SavedStateHandle
import com.tripcompanion.app.core.time.FixedTimeProvider
import com.tripcompanion.app.data.local.fake.InMemoryTripDatabase
import com.tripcompanion.app.data.repository.EventRepositoryImpl
import com.tripcompanion.app.data.repository.LocationRepositoryImpl
import com.tripcompanion.app.data.repository.TripRepositoryImpl
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.service.DeviceLocation
import com.tripcompanion.app.domain.service.DeviceLocationProvider
import com.tripcompanion.app.domain.service.RouteLeg
import com.tripcompanion.app.domain.service.RoutePlanError
import com.tripcompanion.app.domain.service.RoutePlanOutcome
import com.tripcompanion.app.domain.service.RoutePlanService
import com.tripcompanion.app.domain.service.RoutePoint
import kotlinx.coroutines.Dispatchers
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The map's travel legs: one per gap, road times when the provider answers, honest straight lines
 * when it does not.
 *
 * The sheet's "15 min · 4.8 km" line is only trustworthy if the number under each gap is the number
 * for *that* gap — leg *i* is stop *i* → stop *i*+1 (§13). Two things can break that: routing being
 * unavailable (no key, offline, no road), where the map must still show a distance but must not
 * invent a drive time; and a provider that reports the wrong *number* of legs, where pairing what it
 * did send against the gaps would silently misattribute a distance. Both are behaviours the screen
 * cannot see through — they surface only as [TripMapUiState.legs] — so they are asserted here,
 * against the real repositories over [InMemoryTripDatabase]. Only the routing service and the
 * location provider are fakes, because those are the two inputs a unit test needs to hold still.
 *
 * Place names are neutral placeholders; nothing in this engine may recognise a real one (§4).
 */
class TripMapViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** The morning of the mapped day — before the first stop, so nothing has started. */
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
        tripId = 0L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * No routing → each gap is a great-circle hop with no drive time.
     *
     * The two hops are a degree of latitude and a degree of longitude at 11°N, whose lengths differ
     * (a degree of longitude shortens away from the equator) — so the exact metres, not merely a
     * positive number, prove a real haversine rather than a stub, and prove the legs are in order.
     */
    @Test
    fun `an unrouted day falls back to straight-line legs with no drive time`() = runTest(dispatcher) {
        seedThreeStopDay()
        val vm = mapViewModel(
            FakeRoutePlanService(RoutePlanOutcome.Unavailable(RoutePlanError.NOT_CONFIGURED))
        )

        val state = vm.state.value
        // Three mapped stops on one day, numbered in visiting order.
        assertEquals(3, state.pins.size)
        assertEquals(listOf(1, 2, 3), state.pins.map { it.orderInDay })
        // One leg per gap — never one per stop.
        assertEquals(state.pins.size - 1, state.legs.size)
        // No routing → distances are honest, and carry no invented drive time.
        assertTrue(state.legs.all { it.durationSeconds == null })
        assertEquals(111_195.0, state.legs[0].distanceMeters, 500.0) // ~1° of latitude
        assertEquals(109_152.0, state.legs[1].distanceMeters, 500.0) // ~1° of longitude at 11°N
    }

    /**
     * A routed day carries the provider's own per-leg distance and time through verbatim.
     *
     * When the counts line up — one reported leg per gap — the screen shows road numbers, drive
     * time and all, rather than the straight-line understatement.
     */
    @Test
    fun `a routed day carries the provider's per-leg distance and time`() = runTest(dispatcher) {
        seedThreeStopDay()
        val vm = mapViewModel(
            FakeRoutePlanService(
                RoutePlanOutcome.Routed(
                    points = listOf(
                        RoutePoint(10.0, 20.0), RoutePoint(11.0, 20.0), RoutePoint(11.0, 21.0)
                    ),
                    legs = listOf(RouteLeg(5_000.0, 600.0), RouteLeg(7_000.0, 900.0))
                )
            )
        )

        val state = vm.state.value
        assertEquals(2, state.legs.size)
        assertEquals(5_000.0, state.legs[0].distanceMeters, 0.1)
        assertEquals(600.0, state.legs[0].durationSeconds)
        assertEquals(7_000.0, state.legs[1].distanceMeters, 0.1)
        assertEquals(900.0, state.legs[1].durationSeconds)
    }

    /**
     * A partial breakdown is discarded wholesale rather than misaligned.
     *
     * Two gaps but only one reported leg: which gap does it belong to? Any answer is a guess, so the
     * whole day drops back to straight-line distances (no drive time) instead of pairing a distance
     * with the wrong gap. Mirrors the polyline's all-or-nothing road/straight choice (§13).
     */
    @Test
    fun `a partial leg breakdown is discarded rather than misaligned`() = runTest(dispatcher) {
        seedThreeStopDay()
        val vm = mapViewModel(
            FakeRoutePlanService(
                RoutePlanOutcome.Routed(
                    points = listOf(
                        RoutePoint(10.0, 20.0), RoutePoint(11.0, 20.0), RoutePoint(11.0, 21.0)
                    ),
                    legs = listOf(RouteLeg(5_000.0, 600.0)) // one leg for two gaps
                )
            )
        )

        val state = vm.state.value
        assertEquals(2, state.legs.size)
        assertTrue(state.legs.all { it.durationSeconds == null })
    }

    // ---- Fixtures ----------------------------------------------------------

    /** Three stops on one day, at coordinates a degree apart, so the day resolves to three pins. */
    private suspend fun seedThreeStopDay() {
        tripId = trips.insertTrip(
            Trip(
                name = "A trip",
                startDate = LocalDate.of(2026, 11, 3),
                endDate = LocalDate.of(2026, 11, 8)
            )
        )
        val alpha = locations.insertLocation(Location(name = "Alpha", latitude = 10.0, longitude = 20.0))
        val beta = locations.insertLocation(Location(name = "Beta", latitude = 11.0, longitude = 20.0))
        val gamma = locations.insertLocation(Location(name = "Gamma", latitude = 11.0, longitude = 21.0))
        events.insertEvent(stop("Stop 1", 10, alpha))
        events.insertEvent(stop("Stop 2", 12, beta))
        events.insertEvent(stop("Stop 3", 14, gamma))
    }

    private fun stop(title: String, hour: Int, locationId: Long) = Event(
        tripId = tripId,
        title = title,
        startTime = LocalDateTime.of(2026, 11, 3, hour, 0),
        endTime = LocalDateTime.of(2026, 11, 3, hour + 1, 0),
        locationId = locationId
    )

    private fun TestScope.mapViewModel(route: RoutePlanService): TripMapViewModel {
        val vm = TripMapViewModel(
            savedStateHandle = SavedStateHandle(mapOf("tripId" to tripId.toString())),
            tripRepository = trips,
            eventRepository = events,
            locationRepository = locations,
            routePlanService = route,
            deviceLocationProvider = FakeDeviceLocationProvider(),
            timeProvider = clock
        )
        advanceUntilIdle()
        return vm
    }

    /** Returns whatever outcome the test wired, for any waypoints. */
    private class FakeRoutePlanService(
        private val outcome: RoutePlanOutcome
    ) : RoutePlanService {
        override suspend fun planRoute(waypoints: List<RoutePoint>): RoutePlanOutcome = outcome
    }

    /** Never emits a fix — these tests are about legs, not the location dot. */
    private class FakeDeviceLocationProvider : DeviceLocationProvider {
        override fun locationUpdates(): Flow<DeviceLocation?> = flowOf(null)
    }
}
