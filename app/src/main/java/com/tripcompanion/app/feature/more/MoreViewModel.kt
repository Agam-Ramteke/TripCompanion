package com.tripcompanion.app.feature.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.util.GeoUtils
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.PlannedPhotoRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What each destination in the hub actually holds.
 *
 * A menu of five words is a table of contents; these counts are what make it a screen. They
 * also keep it honest — the Hotel row can say there is no stay booked instead of leading to an
 * empty screen, and the Map row counts exactly what the map will draw.
 */
data class MoreUiState(
    val trip: Trip? = null,
    /** Stops with a real coordinate — the same rule the map itself applies. */
    val mappedStopCount: Int = 0,
    val stayCount: Int = 0,
    val toVisitCount: Int = 0,
    val visitedCount: Int = 0,
    val savedCount: Int = 0,
    val trainCount: Int = 0,
    val photoPlanCount: Int = 0,
    val isLoading: Boolean = true
) {
    val hasTrip: Boolean get() = trip != null
}

/**
 * The counts behind the hub.
 *
 * Everything here is read from the repositories the destination screens read, so a row can
 * never advertise a number the screen it opens disagrees with.
 */
@HiltViewModel
class MoreViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
    private val trainRepository: TrainRepository,
    private val plannedPhotoRepository: PlannedPhotoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MoreUiState())
    val state: StateFlow<MoreUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val trips = tripRepository.getAllTrips().first()
            val trip = trips.firstOrNull { it.status == TripStatus.ACTIVE } ?: trips.firstOrNull()

            if (trip == null) {
                // Places and Saved still work with no trip at all, so their counts are still
                // collected — only the trip-scoped rows go quiet.
                locationRepository.getAllLocations().collect { places ->
                    _state.update {
                        it.copy(
                            toVisitCount = places.count { place -> !place.isVisited },
                            visitedCount = places.count { place -> place.isVisited },
                            savedCount = places.count { place -> place.isSaved },
                            isLoading = false
                        )
                    }
                }
                return@launch
            }

            combine(
                tripRepository.getTripById(trip.id),
                eventRepository.getEventsForTrip(trip.id),
                locationRepository.getAllLocations(),
                trainRepository.getTrainsForTrip(trip.id),
                plannedPhotoRepository.countForTrip(trip.id)
            ) { current, events, places, trains, photoPlans ->
                val byId = places.associateBy { it.id }
                _state.value.copy(
                    trip = current ?: trip,
                    mappedStopCount = events.count { event ->
                        val place = event.locationId?.let { byId[it] }
                        place != null && GeoUtils.hasPosition(place.latitude, place.longitude)
                    },
                    stayCount = events.count { it.type == EventType.STAY },
                    toVisitCount = places.count { !it.isVisited },
                    visitedCount = places.count { it.isVisited },
                    savedCount = places.count { it.isSaved },
                    trainCount = trains.size,
                    photoPlanCount = photoPlans,
                    isLoading = false
                )
            }.collect { next -> _state.update { next } }
        }
    }
}
