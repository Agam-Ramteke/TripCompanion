package com.tripcompanion.app.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.engine.TripStateEngine
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.TripRepository
import com.tripcompanion.app.domain.service.DeviceLocation
import com.tripcompanion.app.domain.service.DeviceLocationProvider
import com.tripcompanion.app.domain.service.RouteLeg
import com.tripcompanion.app.domain.service.RoutePlanOutcome
import com.tripcompanion.app.domain.service.RoutePlanService
import com.tripcompanion.app.domain.service.RoutePoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * One pin: the place, and the itinerary entry that put it on the map.
 *
 * Both halves are needed. The place supplies the coordinate and the name; the event supplies
 * the colour, the time and the day, which is what makes the map a plan rather than a list of
 * dots.
 */
data class MapPin(
    val event: Event,
    val place: Location,
    /** 1-based position within its day, so the pin can be numbered in visiting order. */
    val orderInDay: Int
)

/**
 * The pins as (latitude, longitude) pairs in visiting order — the vertices of the *straight* route.
 *
 * A plain pure function, deliberately Android-free (no `GeoPoint`), so the ordering it promises is
 * unit-testable on the JVM. It is two things at once: the fallback line drawn when road routing is
 * unavailable (no key, offline, no road between the stops), and the waypoint list handed to the
 * routing service to fatten into road geometry. Order is the caller's: [MapPin]s already arrive
 * grouped by day and numbered within it, so the line walks the day in visiting order.
 */
fun routePoints(pins: List<MapPin>): List<Pair<Double, Double>> =
    pins.map { it.place.latitude to it.place.longitude }

/**
 * The travel from one visited stop to the next, for the itinerary sheet's "15 min · 4.8 km" line.
 *
 * [distanceMeters] is always known; [durationSeconds] is null when only a straight-line distance
 * could be worked out (no routing key, offline, no road), so the sheet shows the distance without
 * inventing a drive time. Raw units; the sheet rounds at the point of display.
 */
data class TravelLeg(
    val distanceMeters: Double,
    val durationSeconds: Double?
)

data class TripMapUiState(
    val trip: Trip? = null,
    /**
     * Every trip the user has, for the top-of-map switcher. Kept here rather than fetched by the
     * screen so the switcher and the pins can never disagree about which trip is showing.
     */
    val availableTrips: List<Trip> = emptyList(),
    /** Days that actually have a pin. A day with nothing mapped is not offered as a filter. */
    val days: List<LocalDate> = emptyList(),
    /**
     * The day the map is showing, or null for the whole trip.
     *
     * This is the *resolved* day, not the raw selection: with nothing chosen it follows the focus
     * stop (see [TripMapViewModel]), so the map opens on the day being travelled rather than on a
     * whole-trip overview the user then has to narrow by hand.
     */
    val selectedDay: LocalDate? = null,
    val pins: List<MapPin> = emptyList(),
    /** Every pin on the trip, so the day filter can say what it is hiding. */
    val totalPinCount: Int = 0,
    /**
     * Events that could not be mapped at all — no place, or a place with no coordinate.
     *
     * Counted rather than hidden: a map that quietly shows six of nine stops is worse than one
     * that shows six and says so.
     */
    val unmappedCount: Int = 0,
    /**
     * The line to draw through the visible stops: road-following geometry when the routing service
     * has answered for exactly these stops, otherwise straight legs between them. The screen draws
     * it verbatim and never has to know which kind it got.
     */
    val routePolyline: List<Pair<Double, Double>> = emptyList(),
    /**
     * Per-leg travel between the visible stops, aligned so [legs] element *i* is the trip from pin
     * *i* to pin *i*+1 — hence one fewer than [pins]. Road distance and drive time when the routing
     * service answered for exactly these stops; straight-line distance with a null time otherwise.
     */
    val legs: List<TravelLeg> = emptyList(),
    /** Where the map opens: the current or next stop, else the first pin of the shown day. */
    val focusLatitude: Double = 0.0,
    val focusLongitude: Double = 0.0,
    /**
     * The event the camera opens on — current, else next, else the day's first — so the screen can
     * mark it CURRENT on the map and headline it in the sheet's NEXT STOP card without re-deriving it.
     */
    val focusEventId: Long? = null,
    /** The device's own position for the current-location dot, or null when unknown / permission off. */
    val deviceLocation: DeviceLocation? = null,
    val now: LocalDateTime = LocalDateTime.MIN,
    val isLoading: Boolean = true
) {
    val hasPins: Boolean get() = totalPinCount > 0
}

/**
 * The trip's places, as pins, with a road-following line between the stops of the shown day.
 *
 * Only events that both point at a place and have a real coordinate become pins. An event
 * typed in without a location is not a silent pin at 0,0 in the Atlantic — it simply isn't
 * on the map, and the count tells the user how many are missing.
 *
 * Flows meet here. The trip, its events and the clock build the pins synchronously; the road route
 * (with its per-leg travel) arrives asynchronously from [RoutePlanService], and the device's own
 * position streams in from [DeviceLocationProvider] once permission is granted — both merged back
 * into the frame. The trip and the day are [MutableStateFlow]s the switchers write to, so
 * re-pointing the map — another trip, another day — re-derives the pins and re-requests the route in
 * place, never tearing down the screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
    private val routePlanService: RoutePlanService,
    private val deviceLocationProvider: DeviceLocationProvider,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val engine = TripStateEngine()

    /**
     * Which day the map shows, as an intent rather than a value.
     *
     * Three states, because "null = whole trip" cannot also mean "nothing chosen yet": [Auto] is
     * the untouched default and resolves to the focus stop's day, so the map lands where the
     * traveller is; [AllDays] and [Day] are the user's explicit overrides. Collapsing [Auto] into
     * [AllDays] would open every trip on a whole-trip overview, which is exactly the "too long, not
     * descriptive" view this feature replaced.
     */
    private sealed interface DaySelection {
        data object Auto : DaySelection
        data object AllDays : DaySelection
        data class Day(val date: LocalDate) : DaySelection
    }

    /**
     * The road geometry last computed, tagged with the waypoints it was computed for.
     *
     * The tag is what lets [buildState] tell a fresh route from a stale one: when the user switches
     * day, the pins change before the new route arrives, and drawing the previous day's road line
     * over the new day's pins would be a lie. Mismatched waypoints fall back to straight legs until
     * the matching route lands.
     */
    private data class RoadRoute(
        val waypoints: List<RoutePoint>,
        val line: List<Pair<Double, Double>>,
        val legs: List<RouteLeg>
    )

    // Seeded from the nav arg, then owned by the switcher. Null means "resolve a default" — the
    // active trip, else the first — recomputed on every trips emission so a freshly-created or
    // newly-activated trip is picked up without the screen being reopened.
    private val selectedTripId = MutableStateFlow(
        savedStateHandle.get<String>("tripId")?.toLongOrNull()
    )
    private val daySelection = MutableStateFlow<DaySelection>(DaySelection.Auto)
    private val roadRoute = MutableStateFlow(RoadRoute(emptyList(), emptyList(), emptyList()))

    // The device's own position, null until permission is granted and a fix arrives. Folded into the
    // road-route arm of pipeline 1 rather than added as a sixth flow, because combine tops out at
    // five and the road line and the dot both change at roughly camera speed.
    private val deviceLocation = MutableStateFlow<DeviceLocation?>(null)
    private var locationJob: Job? = null

    private val _state = MutableStateFlow(TripMapUiState(now = timeProvider.now()))
    val state: StateFlow<TripMapUiState> = _state.asStateFlow()

    init {
        // Pipeline 1 — build the map frame from the trip, its events, the chosen day, the latest
        // road route and the clock. Synchronous; emits an instant straight-line map, then re-emits
        // with road geometry the moment pipeline 2 supplies it.
        viewModelScope.launch {
            combine(tripRepository.getAllTrips(), selectedTripId) { trips, requestedId ->
                trips to requestedId
            }.flatMapLatest { (trips, requestedId) ->
                val trip = requestedId?.let { id -> trips.firstOrNull { it.id == id } }
                    ?: trips.firstOrNull { it.status == TripStatus.ACTIVE }
                    ?: trips.firstOrNull()

                if (trip == null) {
                    // No trips at all: still surface the (empty) switcher list and stop loading.
                    flowOf(
                        TripMapUiState(
                            availableTrips = trips,
                            now = timeProvider.now(),
                            isLoading = false
                        )
                    )
                } else {
                    combine(
                        eventRepository.getEventsForTrip(trip.id),
                        locationRepository.getAllLocations(),
                        daySelection,
                        combine(roadRoute, deviceLocation) { road, loc -> road to loc },
                        timeProvider.ticker()
                    ) { events, places, selection, routeAndLocation, now ->
                        val (road, loc) = routeAndLocation
                        buildState(trip, trips, events, places, selection, road, loc, now)
                    }
                }
            }.collect { next -> _state.value = next }
        }

        // Pipeline 2 — whenever the visible stops change, ask the routing service for a road line
        // and publish it into `roadRoute`. Keyed on the waypoint list so a clock tick (same stops)
        // never re-hits the metered API, and `mapLatest` cancels an in-flight request the instant
        // the day or trip changes under it.
        viewModelScope.launch {
            _state
                .map { frame -> frame.pins.map { RoutePoint(it.place.latitude, it.place.longitude) } }
                .distinctUntilChanged()
                .mapLatest { waypoints -> planFor(waypoints) }
                .collect { resolved -> roadRoute.value = resolved }
        }
    }

    /**
     * Re-point the map at another trip without leaving the screen.
     *
     * The day selection resets to [DaySelection.Auto]: "Day 2" of the trip you just left has no
     * meaning on the one you just opened, and the new trip should open on its own focus day rather
     * than inherit a filter that might hide every pin.
     */
    fun selectTrip(id: Long) {
        if (selectedTripId.value == id) return
        daySelection.value = DaySelection.Auto
        selectedTripId.value = id
    }

    /** Null selects the whole trip; the screen's "All days" chip passes it. A date pins that day. */
    fun selectDay(day: LocalDate?) {
        daySelection.value = if (day == null) DaySelection.AllDays else DaySelection.Day(day)
    }

    /**
     * Begin following the device's location, once the user has granted the runtime permission.
     *
     * Idempotent: the screen may call this on every grant callback and every resume, but only the
     * first launches a collection. Before this — and if permission is denied — [deviceLocation]
     * stays null and the map simply shows no dot; the feature degrades to absent, never to a crash.
     * The collection lives for the ViewModel's lifetime, and cancelling it (with the scope) removes
     * the underlying location updates via the provider's `awaitClose`.
     */
    fun onLocationPermissionGranted() {
        if (locationJob != null) return
        locationJob = viewModelScope.launch {
            deviceLocationProvider.locationUpdates().collect { deviceLocation.value = it }
        }
    }

    /** Assemble one frame of map state from the trip, its events, the chosen day, and the clock. */
    private fun buildState(
        trip: Trip,
        trips: List<Trip>,
        events: List<Event>,
        places: List<Location>,
        selection: DaySelection,
        road: RoadRoute,
        loc: DeviceLocation?,
        now: LocalDateTime
    ): TripMapUiState {
        val byId = places.associateBy { it.id }
        val all = events.mapNotNull { event ->
            val place = event.locationId?.let { byId[it] } ?: return@mapNotNull null
            if (!hasPosition(place)) return@mapNotNull null
            event to place
        }
        val numbered = all
            .groupBy { it.first.startTime.toLocalDate() }
            .flatMap { (_, sameDay) ->
                sameDay.mapIndexed { index, (event, place) -> MapPin(event, place, index + 1) }
            }

        // The focus stop (current or next, else first) is taken from the whole trip, from the same
        // engine Home uses, so the map agrees about what "next" means.
        val tripFocus = focusPin(trip, events, numbered, now)

        // Auto follows the focus stop's day; an explicit choice overrides. This is what makes the
        // map open on the day being travelled instead of a whole-trip overview.
        val resolvedDay: LocalDate? = when (selection) {
            DaySelection.AllDays -> null
            is DaySelection.Day -> selection.date
            DaySelection.Auto -> tripFocus?.event?.startTime?.toLocalDate()
        }
        val visible = if (resolvedDay == null) {
            numbered
        } else {
            numbered.filter { it.event.startTime.toLocalDate() == resolvedDay }
        }

        // Centre on the focus stop when it is on the shown day; otherwise on that day's first stop,
        // so switching to a day the traveller has not reached still frames that day, not an off-screen
        // pin from elsewhere in the trip.
        val focus = if (tripFocus != null && visible.any { it.event.id == tripFocus.event.id }) {
            tripFocus
        } else {
            visible.firstOrNull()
        }

        // Road geometry and per-leg travel only when they belong to exactly these stops; straight
        // legs and straight-line distances until the matching route arrives, and whenever routing is
        // unavailable.
        val visibleWaypoints = visible.map { RoutePoint(it.place.latitude, it.place.longitude) }
        val routeMatches = road.waypoints == visibleWaypoints
        val polyline = if (routeMatches && road.line.size >= 2) {
            road.line
        } else {
            routePoints(visible)
        }
        val legs = travelLegs(visible, road, routeMatches)

        return TripMapUiState(
            trip = trip,
            availableTrips = trips,
            days = numbered.map { it.event.startTime.toLocalDate() }.distinct().sorted(),
            selectedDay = resolvedDay,
            pins = visible,
            totalPinCount = numbered.size,
            unmappedCount = (events.size - numbered.size).coerceAtLeast(0),
            routePolyline = polyline,
            legs = legs,
            focusLatitude = focus?.place?.latitude ?: 0.0,
            focusLongitude = focus?.place?.longitude ?: 0.0,
            focusEventId = focus?.event?.id,
            deviceLocation = loc,
            now = now,
            isLoading = false
        )
    }

    /**
     * The road line and per-leg travel for a set of stops, or empty lists when there is none to draw.
     *
     * Empty is the straight-line signal: fewer than two stops is not a route, and every failure the
     * service reports — no key, offline, no road — comes back as [RoutePlanOutcome.Unavailable] and
     * is turned into the same empty [RoadRoute], because from the map's point of view they are one
     * outcome: fall back to straight legs and straight-line distances.
     */
    private suspend fun planFor(waypoints: List<RoutePoint>): RoadRoute {
        if (waypoints.size < RoutePlanService.MIN_WAYPOINTS) {
            return RoadRoute(waypoints, emptyList(), emptyList())
        }
        return when (val outcome = routePlanService.planRoute(waypoints)) {
            is RoutePlanOutcome.Routed ->
                RoadRoute(waypoints, outcome.points.map { it.latitude to it.longitude }, outcome.legs)
            is RoutePlanOutcome.Unavailable ->
                RoadRoute(waypoints, emptyList(), emptyList())
        }
    }

    /**
     * Travel between consecutive visible stops, for the itinerary sheet.
     *
     * The road legs are used only when they belong to exactly these stops *and* there is one per gap
     * — leg *i* is pin *i* → pin *i*+1. A partial breakdown (the provider dropped a malformed
     * segment, so the counts no longer line up) is discarded wholesale rather than risk pairing a
     * distance with the wrong gap; the whole day then falls back to straight-line distances, which
     * carry no drive time. This mirrors the polyline's all-or-nothing road/straight choice.
     */
    private fun travelLegs(
        visible: List<MapPin>,
        road: RoadRoute,
        routeMatches: Boolean
    ): List<TravelLeg> {
        if (visible.size < 2) return emptyList()
        val useRoad = routeMatches && road.legs.size == visible.size - 1
        return (0 until visible.size - 1).map { i ->
            if (useRoad) {
                TravelLeg(road.legs[i].distanceMeters, road.legs[i].durationSeconds)
            } else {
                val from = visible[i].place
                val to = visible[i + 1].place
                TravelLeg(
                    distanceMeters = haversineMeters(
                        from.latitude, from.longitude, to.latitude, to.longitude
                    ),
                    durationSeconds = null
                )
            }
        }
    }

    /**
     * Great-circle distance in metres between two coordinates.
     *
     * The honest fallback when there is no routed distance: a straight line is shorter than the road
     * and everyone knows it, which is exactly why it is shown without a drive time. Earth as a sphere
     * is plenty at trip scale.
     */
    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Which pin the map centres on.
     *
     * Where the traveller is or is heading, taken from the same engine the rest of the app
     * uses so the map agrees with Home about what "next" means. Falls back to the first pin,
     * which for a trip that has not started is the right answer anyway.
     */
    private fun focusPin(
        trip: Trip,
        events: List<Event>,
        pins: List<MapPin>,
        now: LocalDateTime
    ): MapPin? {
        if (pins.isEmpty()) return null
        val state = engine.computeState(trip, events, now)
        val focusEventId = state.currentEvent?.id ?: state.nextEvent?.id
        return pins.firstOrNull { it.event.id == focusEventId } ?: pins.first()
    }

    private fun hasPosition(place: Location): Boolean =
        place.latitude != 0.0 || place.longitude != 0.0

    private companion object {
        /** Mean Earth radius; the haversine fallback treats the planet as a sphere. */
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}
