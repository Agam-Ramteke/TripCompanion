package com.tripcompanion.app.feature.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.engine.TripStateEngine
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.PlannedPhoto
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.PlannedPhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

data class EventDetailState(
    val event: Event? = null,
    val location: Location? = null,
    val plannedPhotos: List<PlannedPhoto> = emptyList(),
    val computedStatus: EventStatus = EventStatus.UPCOMING,
    /** The clock this screen is rendering against, so relative copy stays honest. */
    val now: LocalDateTime = LocalDateTime.MIN,
    /** Neighbours in the trip's one canonical order (§10). Null at either end. */
    val previousEventId: Long? = null,
    val nextEventId: Long? = null,
    val isLoading: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
    private val plannedPhotoRepository: PlannedPhotoRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val eventId: Long = savedStateHandle.get<String>("eventId")?.toLongOrNull() ?: 0L
    private val engine = TripStateEngine()

    private val _state = MutableStateFlow(EventDetailState(now = timeProvider.now()))
    val state: StateFlow<EventDetailState> = _state.asStateFlow()

    init {
        val eventFlow = eventRepository.getEventById(eventId)

        // The neighbours come from the same trip as the event, so the trip is
        // discovered from the event rather than passed in — one less argument that
        // could disagree with the row in the database.
        val siblingsFlow = eventFlow
            .map { it?.tripId }
            .distinctUntilChanged()
            .flatMapLatest { tripId ->
                if (tripId == null) flowOf(emptyList()) else eventRepository.getEventsForTrip(tripId)
            }

        viewModelScope.launch {
            combine(
                eventFlow,
                plannedPhotoRepository.getPhotosForEvent(eventId),
                siblingsFlow,
                timeProvider.ticker()
            ) { event, photos, siblings, now ->
                Snapshot(event, photos, siblings, now)
            }.collect { snapshot ->
                val location = snapshot.event?.locationId?.let {
                    locationRepository.getLocationByIdOnce(it)
                }
                val (previousId, nextId) = neighbours(snapshot.event, snapshot.siblings)

                _state.update {
                    it.copy(
                        event = snapshot.event,
                        location = location,
                        plannedPhotos = snapshot.photos,
                        computedStatus = snapshot.event
                            ?.let { event -> engine.computeEventStatus(event, snapshot.now) }
                            ?: EventStatus.UPCOMING,
                        now = snapshot.now,
                        previousEventId = previousId,
                        nextEventId = nextId,
                        isLoading = false
                    )
                }
            }
        }
    }

    /** Marks the event done. A terminal status the engine will not overwrite (§21). */
    fun completeEvent() = setStatus(EventStatus.COMPLETED)

    /** Marks the event skipped. Also terminal. */
    fun skipEvent() = setStatus(EventStatus.SKIPPED)

    /**
     * Undoes a Done or Skipped decision.
     *
     * Writing UPCOMING is not the same as claiming the event is in the future — it
     * clears the manual override and hands the event back to the state engine,
     * which will call it ACTIVE or MISSED or whatever the clock actually implies.
     * Storing the recomputed status instead would bake this moment into the row.
     */
    fun reopenEvent() = setStatus(EventStatus.UPCOMING)

    private fun setStatus(status: EventStatus) {
        val event = _state.value.event ?: return
        if (event.status == status) return
        viewModelScope.launch {
            eventRepository.updateEvent(event.copy(status = status))
        }
    }

    fun deletePhoto(photoId: Long) {
        viewModelScope.launch { plannedPhotoRepository.deletePhoto(photoId) }
    }

    /**
     * The events either side of this one, using the app's single ordering (§10).
     * Sorting here rather than trusting query order is what keeps Timeline, Home
     * and this screen agreeing on what "next" means.
     */
    private fun neighbours(event: Event?, siblings: List<Event>): Pair<Long?, Long?> {
        if (event == null || siblings.isEmpty()) return null to null
        val ordered = siblings.sortedWith(TripStateEngine.EVENT_CHRONOLOGICAL_COMPARATOR)
        val index = ordered.indexOfFirst { it.id == event.id }
        if (index < 0) return null to null
        return ordered.getOrNull(index - 1)?.id to ordered.getOrNull(index + 1)?.id
    }

    private data class Snapshot(
        val event: Event?,
        val photos: List<PlannedPhoto>,
        val siblings: List<Event>,
        val now: LocalDateTime
    )
}
