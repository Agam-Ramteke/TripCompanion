package com.tripcompanion.app.feature.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.util.GeoUtils
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.PlannedPhoto
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.PlannedPhotoRepository
import com.tripcompanion.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A neighbour of the place on screen, with the walk already measured. */
data class NearbyPlace(
    val place: Location,
    val distanceKm: Double
) {
    val distanceLabel: String get() = GeoUtils.formatDistance(distanceKm)
}

data class PlaceDetailUiState(
    val place: Location? = null,
    /** Itinerary entries pointing here — the answer to "when am I going". */
    val scheduledEvents: List<Event> = emptyList(),
    /** Photo plans attached to those entries, which is what "photo ideas" actually means here. */
    val photoIdeas: List<PlannedPhoto> = emptyList(),
    val nearby: List<NearbyPlace> = emptyList(),
    /**
     * The trip an "Add to itinerary" tap would write to.
     *
     * Null when there are no trips, which is why the button explains itself instead of
     * disappearing: a place saved before any trip exists is the normal way people plan.
     */
    val defaultTripId: Long? = null,
    val isLoading: Boolean = true
) {
    val isOnItinerary: Boolean get() = scheduledEvents.isNotEmpty()
    val hasPosition: Boolean
        get() = place != null && GeoUtils.hasPosition(place.latitude, place.longitude)
}

/**
 * One place, and everything the rest of the database knows about it.
 *
 * "Nearby" is computed from stored coordinates rather than fetched: the places worth
 * suggesting next to this one are the ones already on the user's own list, and a search
 * provider would answer with restaurants they have never heard of while offline on a hill.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaceDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationRepository: LocationRepository,
    private val eventRepository: EventRepository,
    private val photoRepository: PlannedPhotoRepository,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val locationId: Long = savedStateHandle.get<String>("locationId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(PlaceDetailUiState())
    val state: StateFlow<PlaceDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                locationRepository.getLocationById(locationId),
                locationRepository.getAllLocations(),
                eventRepository.getEventsForLocation(locationId),
                tripRepository.getAllTrips()
            ) { place, all, events, trips ->
                _state.value.copy(
                    place = place,
                    scheduledEvents = events,
                    nearby = nearbyTo(place, all),
                    defaultTripId = (trips.firstOrNull { it.status == TripStatus.ACTIVE }
                        ?: trips.firstOrNull())?.id,
                    isLoading = false
                )
            }.collect { next -> _state.update { next } }
        }

        // Photo plans hang off events, not places, so this follows whichever events currently
        // point here. flatMapLatest rather than a nested collector: adding this place to a
        // second day must not leave the first day's subscription running.
        viewModelScope.launch {
            eventRepository.getEventsForLocation(locationId)
                .flatMapLatest { events ->
                    if (events.isEmpty()) {
                        flowOf(emptyList())
                    } else {
                        combine(events.map { photoRepository.getPhotosForEvent(it.id) }) { lists ->
                            lists.toList().flatten()
                        }
                    }
                }
                .collect { photos -> _state.update { it.copy(photoIdeas = photos) } }
        }
    }

    fun toggleSaved() {
        val place = _state.value.place ?: return
        viewModelScope.launch { locationRepository.setSaved(place.id, !place.isSaved) }
    }

    fun toggleVisited() {
        val place = _state.value.place ?: return
        viewModelScope.launch { locationRepository.setVisited(place.id, !place.isVisited) }
    }

    /**
     * The five closest places that actually have a position.
     *
     * A row inserted by hand can sit at 0,0 in the Gulf of Guinea; offering it as "6,200 km
     * away" would be arithmetic presented as a suggestion.
     */
    private fun nearbyTo(place: Location?, all: List<Location>): List<NearbyPlace> {
        if (place == null || !GeoUtils.hasPosition(place.latitude, place.longitude)) return emptyList()
        return all.asSequence()
            .filter { it.id != place.id && GeoUtils.hasPosition(it.latitude, it.longitude) }
            .map {
                NearbyPlace(
                    place = it,
                    distanceKm = GeoUtils.distanceKm(
                        place.latitude, place.longitude, it.latitude, it.longitude
                    )
                )
            }
            .sortedBy { it.distanceKm }
            .take(5)
            .toList()
    }
}
