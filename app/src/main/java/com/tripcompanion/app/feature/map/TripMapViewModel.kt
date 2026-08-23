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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

data class TripMapUiState(
    val trip: Trip? = null,
    /** Days that actually have a pin. A day with nothing mapped is not offered as a filter. */
    val days: List<LocalDate> = emptyList(),
    /** Null means "the whole trip". */
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
    /** Where the map opens: the current or next stop, else the first pin. */
    val focusLatitude: Double = 0.0,
    val focusLongitude: Double = 0.0,
    val now: LocalDateTime = LocalDateTime.MIN,
    val isLoading: Boolean = true
) {
    val hasPins: Boolean get() = totalPinCount > 0
}

/**
 * The trip's places, as pins.
 *
 * Only events that both point at a place and have a real coordinate become pins. An event
 * typed in without a location is not a silent pin at 0,0 in the Atlantic — it simply isn't
 * on the map, and the count tells the user how many are missing.
 */
@HiltViewModel
class TripMapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val engine = TripStateEngine()
    private val requestedTripId: Long? = savedStateHandle.get<String>("tripId")?.toLongOrNull()
    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    private val _state = MutableStateFlow(TripMapUiState(now = timeProvider.now()))
    val state: StateFlow<TripMapUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val trips = tripRepository.getAllTrips().first()
            val trip = requestedTripId?.let { id -> trips.firstOrNull { it.id == id } }
                ?: trips.firstOrNull { it.status == TripStatus.ACTIVE }
                ?: trips.firstOrNull()

            if (trip == null) {
                _state.update { it.copy(isLoading = false) }
                return@launch
            }

            combine(
                eventRepository.getEventsForTrip(trip.id),
                locationRepository.getAllLocations(),
                selectedDay,
                timeProvider.ticker()
            ) { events, places, day, now ->
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
                val visible = if (day == null) numbered else {
                    numbered.filter { it.event.startTime.toLocalDate() == day }
                }
                val focus = focusPin(trip, events, numbered, now)
                _state.value.copy(
                    trip = trip,
                    days = numbered.map { it.event.startTime.toLocalDate() }.distinct().sorted(),
                    selectedDay = day,
                    pins = visible,
                    totalPinCount = numbered.size,
                    unmappedCount = (events.size - numbered.size).coerceAtLeast(0),
                    focusLatitude = focus?.place?.latitude ?: 0.0,
                    focusLongitude = focus?.place?.longitude ?: 0.0,
                    now = now,
                    isLoading = false
                )
            }.collect { next -> _state.update { next } }
        }
    }

    /** Null selects the whole trip; the screen's "All days" chip passes it. */
    fun selectDay(day: LocalDate?) {
        selectedDay.value = day
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
}
