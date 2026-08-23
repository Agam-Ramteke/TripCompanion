package com.tripcompanion.app.feature.stay

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.StayDetails
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.StayDetailsRepository
import com.tripcompanion.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class HotelUiState(
    val trip: Trip? = null,
    /** Every STAY on the trip. More than one is a normal itinerary, not an edge case. */
    val stays: List<Event> = emptyList(),
    val selected: Event? = null,
    val details: StayDetails? = null,
    val location: Location? = null,
    val now: LocalDateTime = LocalDateTime.MIN,
    val isEditing: Boolean = false,
    val isLoading: Boolean = true
)

/**
 * The hotel screen's data: a STAY event, the paperwork keyed to it, and the place it points at.
 *
 * Check-in and check-out times are read off the event rather than stored again here, so the
 * hotel card and the itinerary entry can never disagree about when the room is booked.
 */
@HiltViewModel
class HotelViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val stayDetailsRepository: StayDetailsRepository,
    private val locationRepository: LocationRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val requestedTripId: Long? = savedStateHandle.get<String>("tripId")?.toLongOrNull()
    private val selectedStayId = MutableStateFlow<Long?>(null)

    private val _state = MutableStateFlow(HotelUiState(now = timeProvider.now()))
    val state: StateFlow<HotelUiState> = _state.asStateFlow()

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
                selectedStayId,
                timeProvider.ticker()
            ) { events, chosenId, now ->
                val stays = events.filter { it.type == EventType.STAY }
                val selected = stays.firstOrNull { it.id == chosenId } ?: currentStay(stays, now)
                Triple(stays, selected, now)
            }.collect { (stays, selected, now) ->
                val details = selected?.let { stayDetailsRepository.getForEventOnce(it.id) }
                val location = selected?.locationId?.let { locationRepository.getLocationByIdOnce(it) }
                _state.update {
                    it.copy(
                        trip = trip,
                        stays = stays,
                        selected = selected,
                        details = details,
                        location = location,
                        now = now,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectStay(eventId: Long) {
        selectedStayId.value = eventId
    }

    fun setEditing(editing: Boolean) {
        _state.update { it.copy(isEditing = editing) }
    }

    /**
     * Writes the paperwork.
     *
     * Takes the whole record rather than one field at a time: the form is edited as a form
     * and saved once, so a half-typed phone number never reaches the database.
     */
    fun saveDetails(details: StayDetails) {
        viewModelScope.launch {
            stayDetailsRepository.save(details)
            _state.update { it.copy(details = details, isEditing = false) }
        }
    }

    /**
     * Which stay the screen opens on.
     *
     * The one the user is inside right now, else the next one starting, else the last one that
     * ended — so a finished trip still shows where it was rather than an empty screen.
     */
    private fun currentStay(stays: List<Event>, now: LocalDateTime): Event? {
        if (stays.isEmpty()) return null
        stays.firstOrNull { !now.isBefore(it.startTime) && now.isBefore(it.endTime) }
            ?.let { return it }
        return stays.filter { it.startTime.isAfter(now) }.minByOrNull { it.startTime }
            ?: stays.maxByOrNull { it.endTime }
    }
}
