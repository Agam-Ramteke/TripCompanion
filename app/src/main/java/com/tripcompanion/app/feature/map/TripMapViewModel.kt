package com.tripcompanion.app.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.engine.TripStateEngine
import com.tripcompanion.app.domain.model.DayRouteLeg
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.RouteLegStatus
import com.tripcompanion.app.domain.model.TransportMode
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.TripRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.service.DeviceLocation
import com.tripcompanion.app.domain.service.DeviceLocationProvider
import com.tripcompanion.app.domain.service.JourneyEventLinker
import com.tripcompanion.app.domain.service.LocationSearchOutcome
import com.tripcompanion.app.domain.service.LocationSearchService
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One pin: the place, and the itinerary entry that put it on the map.
 */
data class MapPin(
    val event: Event,
    val place: Location,
    /** 1-based position within its day, so the pin can be numbered in visiting order. */
    val orderInDay: Int
)

/**
 * The pins as (latitude, longitude) pairs in visiting order — the vertices of the straight route.
 */
fun routePoints(pins: List<MapPin>): List<Pair<Double, Double>> =
    pins.map { it.place.latitude to it.place.longitude }

/**
 * The travel from one visited stop to the next, for the itinerary sheet.
 */
data class TravelLeg(
    val distanceMeters: Double,
    val durationSeconds: Double?
)

data class TripMapUiState(
    val trip: Trip? = null,
    val availableTrips: List<Trip> = emptyList(),
    val days: List<LocalDate> = emptyList(),
    val selectedDay: LocalDate? = null,
    val pins: List<MapPin> = emptyList(),
    val legs: List<DayRouteLeg> = emptyList(),
    val totalPinCount: Int = 0,
    val unmappedCount: Int = 0,
    val routePolyline: List<Pair<Double, Double>> = emptyList(),
    val selectedPinId: Long? = null,
    val focusLatitude: Double = 0.0,
    val focusLongitude: Double = 0.0,
    val focusEventId: Long? = null,
    val completedCount: Int = 0,
    val deviceLocation: DeviceLocation? = null,
    val now: LocalDateTime = LocalDateTime.MIN,
    val isLoading: Boolean = true,
    val isRefreshingLocations: Boolean = false
) {
    val hasPins: Boolean get() = totalPinCount > 0
}

/**
 * Visual Itinerary Overview ViewModel.
 *
 * Provides a clean geographical view of the day's stops and independent route legs.
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
    private val timeProvider: TimeProvider,
    private val trainRepository: TrainRepository? = null,
    private val journeyEventLinker: JourneyEventLinker? = null,
    private val locationSearchService: LocationSearchService? = null
) : ViewModel() {

    private val engine = TripStateEngine()

    private sealed interface DaySelection {
        data object Auto : DaySelection
        data object AllDays : DaySelection
        data class Day(val date: LocalDate) : DaySelection
    }

    private data class RoadRoute(
        val waypoints: List<RoutePoint>,
        val line: List<Pair<Double, Double>>,
        val legs: List<RouteLeg>
    )

    private val selectedTripId = MutableStateFlow(
        savedStateHandle.get<String>("tripId")?.toLongOrNull()
    )
    private val daySelection = MutableStateFlow<DaySelection>(DaySelection.Auto)
    private val roadRoute = MutableStateFlow(RoadRoute(emptyList(), emptyList(), emptyList()))
    private val selectedPinId = MutableStateFlow<Long?>(null)

    private val deviceLocation = MutableStateFlow<DeviceLocation?>(null)
    private var locationJob: Job? = null

    private val _state = MutableStateFlow(TripMapUiState(now = timeProvider.now()))
    val state: StateFlow<TripMapUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(tripRepository.getAllTrips(), selectedTripId) { trips, requestedId ->
                trips to requestedId
            }.flatMapLatest { (trips, requestedId) ->
                val trip = requestedId?.let { id -> trips.firstOrNull { it.id == id } }
                    ?: trips.firstOrNull { it.status == TripStatus.ACTIVE }
                    ?: trips.firstOrNull()

                if (trip == null) {
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
                        combine(roadRoute, selectedPinId) { road, selectedId -> road to selectedId },
                        timeProvider.ticker()
                    ) { events, places, selection, routeAndSelection, now ->
                        val (road, selectedId) = routeAndSelection
                        buildState(trip, trips, events, places, selection, road, selectedId, now)
                    }
                }
            }.collect { next -> _state.value = next }
        }

        // Request road route when stops change
        viewModelScope.launch {
            _state
                .map { frame -> frame.pins.map { RoutePoint(it.place.latitude, it.place.longitude) } }
                .distinctUntilChanged()
                .mapLatest { waypoints -> planFor(waypoints) }
                .collect { resolved -> roadRoute.value = resolved }
        }

        // Auto-heal legacy or invalid out-of-country coordinates with LocationIQ
        viewModelScope.launch {
            val all = locationRepository.getAllLocations().first()
            val hasLegacyCoords = all.any {
                (it.latitude != 0.0 && it.longitude != 0.0) &&
                    (it.latitude !in 6.0..38.0 || it.longitude !in 68.0..98.0 || it.providerName != "LocationIQ")
            }
            if (hasLegacyCoords) {
                refreshLocations()
            }
        }
    }

    fun refreshLocations() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshingLocations = true) }
            try {
                val trip = _state.value.trip ?: return@launch
                val events = eventRepository.getEventsForTrip(trip.id).first()
                val trains = trainRepository?.getTrainsForTrip(trip.id)?.first() ?: emptyList()
                val allLocations = locationRepository.getAllLocations().first()
                val locationById = allLocations.associateBy { it.id }.toMutableMap()

                // 1. Refresh all train stations via LocationIQ
                for (train in trains) {
                    val stationName = train.boardingPointName.ifBlank { train.originName }.trim()
                    if (stationName.isNotBlank()) {
                        val locId = journeyEventLinker?.resolveStationLocation(stationName, forceRefresh = true)
                        if (locId != null && train.eventId != null) {
                            val ev = events.firstOrNull { it.id == train.eventId }
                            if (ev != null && ev.locationId != locId) {
                                eventRepository.updateEvent(ev.copy(locationId = locId))
                            }
                        }
                    }
                }

                // 2. Refresh all event locations on this trip
                val searchService = locationSearchService ?: return@launch
                val eventLocationIds = events.mapNotNull { it.locationId }.distinct()
                for (locId in eventLocationIds) {
                    val loc = locationById[locId] ?: locationRepository.getLocationByIdOnce(locId) ?: continue
                    val query = if (loc.category == "Transit" || loc.name.contains("station", ignoreCase = true)) {
                        if (loc.name.contains("station", ignoreCase = true)) {
                            "${loc.name}, India"
                        } else {
                            "${loc.name} Railway Station, India"
                        }
                    } else if (loc.address.isNotBlank() && !loc.address.contains("Pakistan", ignoreCase = true)) {
                        "${loc.name}, ${loc.address}"
                    } else {
                        "${loc.name}, India"
                    }

                    when (val outcome = searchService.searchPlaces(query)) {
                        is LocationSearchOutcome.Results -> {
                            val place = outcome.places.firstOrNull()
                            if (place != null) {
                                locationRepository.updateLocation(
                                    loc.copy(
                                        latitude = place.latitude,
                                        longitude = place.longitude,
                                        address = place.formattedAddress.ifBlank { loc.address },
                                        providerName = place.providerName,
                                        providerPlaceId = place.providerPlaceId
                                    )
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            } finally {
                _state.update { it.copy(isRefreshingLocations = false) }
            }
        }
    }

    fun selectTrip(id: Long) {
        if (selectedTripId.value == id) return
        daySelection.value = DaySelection.Auto
        selectedPinId.value = null
        selectedTripId.value = id
    }

    fun selectDay(day: LocalDate?) {
        daySelection.value = if (day == null) DaySelection.AllDays else DaySelection.Day(day)
        selectedPinId.value = null
    }

    fun selectPin(pinId: Long?) {
        selectedPinId.value = pinId
    }

    fun onLocationPermissionGranted() {
        if (locationJob != null) return
        locationJob = viewModelScope.launch {
            deviceLocationProvider.locationUpdates().collect { deviceLocation.value = it }
        }
    }

    private fun buildState(
        trip: Trip,
        trips: List<Trip>,
        events: List<Event>,
        places: List<Location>,
        selection: DaySelection,
        road: RoadRoute,
        selectedId: Long?,
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

        val tripFocus = focusPin(trip, events, numbered, now)

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

        val focus = if (tripFocus != null && visible.any { it.event.id == tripFocus.event.id }) {
            tripFocus
        } else {
            visible.firstOrNull()
        }

        val visibleWaypoints = visible.map { RoutePoint(it.place.latitude, it.place.longitude) }
        val routeMatches = road.waypoints == visibleWaypoints
        val polyline = if (routeMatches && road.line.size >= 2) {
            road.line
        } else {
            visible.map { it.place.latitude to it.place.longitude }
        }

        val statuses = visible.associate { it.event.id to engine.computeEventStatus(it.event, now) }
        val completedCount = statuses.values.count { it == EventStatus.COMPLETED }

        val dayRouteLegs = buildDayRouteLegs(visible, road, routeMatches, statuses, focus?.event?.id)

        return TripMapUiState(
            trip = trip,
            availableTrips = trips,
            days = numbered.map { it.event.startTime.toLocalDate() }.distinct().sorted(),
            selectedDay = resolvedDay,
            pins = visible,
            legs = dayRouteLegs,
            totalPinCount = numbered.size,
            unmappedCount = (events.size - numbered.size).coerceAtLeast(0),
            routePolyline = polyline,
            selectedPinId = selectedId,
            focusLatitude = focus?.place?.latitude ?: 0.0,
            focusLongitude = focus?.place?.longitude ?: 0.0,
            focusEventId = focus?.event?.id,
            completedCount = completedCount,
            deviceLocation = deviceLocation.value,
            now = now,
            isLoading = false
        )
    }

    private fun buildDayRouteLegs(
        visible: List<MapPin>,
        road: RoadRoute,
        routeMatches: Boolean,
        statuses: Map<Long, EventStatus>,
        focusEventId: Long?
    ): List<DayRouteLeg> {
        if (visible.size < 2) return emptyList()
        val useRoad = routeMatches && road.legs.size == visible.size - 1

        return (0 until visible.size - 1).map { i ->
            val from = visible[i]
            val to = visible[i + 1]

            val toStatus = statuses[to.event.id] ?: EventStatus.UPCOMING
            val legStatus = when {
                toStatus == EventStatus.COMPLETED || toStatus == EventStatus.SKIPPED || toStatus == EventStatus.MISSED ->
                    RouteLegStatus.COMPLETED
                to.event.id == focusEventId ->
                    RouteLegStatus.CURRENT
                else ->
                    RouteLegStatus.FUTURE
            }

            val mode = when {
                from.event.type == EventType.JOURNEY && from.event.title.contains("Train", ignoreCase = true) ->
                    TransportMode.TRAIN
                to.place.name.contains("Station", ignoreCase = true) ->
                    TransportMode.CAR
                else ->
                    TransportMode.CAR
            }

            val distanceMeters = if (useRoad) {
                road.legs[i].distanceMeters
            } else {
                haversineMeters(from.place.latitude, from.place.longitude, to.place.latitude, to.place.longitude)
            }

            val durationSeconds = if (useRoad) {
                road.legs[i].durationSeconds
            } else {
                null
            }

            // Approximate segment points from full polyline or straight line
            val points = if (useRoad && road.line.size >= visible.size) {
                val step = road.line.size / (visible.size - 1)
                val startIdx = i * step
                val endIdx = if (i == visible.size - 2) road.line.size else (i + 1) * step
                road.line.subList(startIdx.coerceIn(0, road.line.size), (endIdx + 1).coerceIn(0, road.line.size))
            } else {
                listOf(
                    from.place.latitude to from.place.longitude,
                    to.place.latitude to to.place.longitude
                )
            }

            DayRouteLeg(
                id = "${from.event.id}_${to.event.id}",
                fromStopId = from.event.id,
                toStopId = to.event.id,
                fromName = from.event.title,
                toName = to.event.title,
                mode = mode,
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                points = points,
                status = legStatus
            )
        }
    }

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

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

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
        const val EARTH_RADIUS_M = 6_371_000.0
    }
}
