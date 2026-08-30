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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What each destination in the hub actually holds.
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
 * The counts behind the hub, reactively listening to database state.
 */
@HiltViewModel
class MoreViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
    private val trainRepository: TrainRepository,
    private val plannedPhotoRepository: PlannedPhotoRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<MoreUiState> = tripRepository.getAllTrips()
        .flatMapLatest { trips ->
            val activeTrip = trips.firstOrNull { it.status == TripStatus.ACTIVE } ?: trips.firstOrNull()
            if (activeTrip == null) {
                locationRepository.getAllLocations().map { places ->
                    MoreUiState(
                        trip = null,
                        toVisitCount = places.count { !it.isVisited },
                        visitedCount = places.count { it.isVisited },
                        savedCount = places.count { it.isSaved },
                        isLoading = false
                    )
                }
            } else {
                combine(
                    tripRepository.getTripById(activeTrip.id),
                    eventRepository.getEventsForTrip(activeTrip.id),
                    locationRepository.getAllLocations(),
                    trainRepository.getTrainsForTrip(activeTrip.id),
                    plannedPhotoRepository.countForTrip(activeTrip.id)
                ) { current, events, places, trains, photoPlans ->
                    val byId = places.associateBy { it.id }
                    MoreUiState(
                        trip = current ?: activeTrip,
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
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MoreUiState(isLoading = true)
        )
}
