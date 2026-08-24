package com.tripcompanion.app.feature.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.*
import com.tripcompanion.app.domain.repository.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

data class EventEditorState(
    val eventId: Long? = null,
    val tripId: Long = 0,
    val type: EventType = EventType.VISIT,
    val title: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endTime: LocalTime = LocalTime.of(11, 0),
    val isOvernight: Boolean = false,
    val locationId: Long? = null,
    val locationName: String = "",
    val whatWeAreDoing: String = "",
    val notes: String = "",
    val backgroundImageUri: String? = null,
    val status: EventStatus = EventStatus.UPCOMING,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val validationError: String? = null,
    /** Preserved across an edit so saving does not restamp the row as newly created. */
    val createdAt: LocalDateTime? = null
) {
    val eventDate: LocalDate
        get() = startDate

    val startDateTime: LocalDateTime
        get() = LocalDateTime.of(startDate, startTime)

    val endDateTime: LocalDateTime
        get() = LocalDateTime.of(
            if (type == EventType.STAY) endDate else if (isOvernight) startDate.plusDays(1) else startDate,
            endTime
        )

    val isTimeRangeValid: Boolean
        get() = endDateTime.isAfter(startDateTime)
}

@HiltViewModel
class EventEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val tripId: Long = savedStateHandle.get<String>("tripId")?.toLongOrNull() ?: 0L
    private val eventId: Long? = savedStateHandle.get<String>("eventId")?.toLongOrNull()

    /**
     * A place chosen before the activity existed.
     *
     * Place Detail's "Add to itinerary" knows where but not what, so it hands the place over in
     * the route and the form opens with it already attached. Only read for a new activity — an
     * existing one keeps the place it was saved with.
     */
    private val presetLocationId: Long? = savedStateHandle.get<String>("locationId")?.toLongOrNull()

    private val _state = MutableStateFlow(
        EventEditorState(
            tripId = tripId,
            startDate = timeProvider.now().toLocalDate(),
            endDate = timeProvider.now().toLocalDate(),
            startTime = timeProvider.now().toLocalTime().withSecond(0).withNano(0),
            endTime = timeProvider.now().toLocalTime().plusHours(1).withSecond(0).withNano(0)
        )
    )
    val state: StateFlow<EventEditorState> = _state.asStateFlow()

    init {
        if (eventId != null) {
            viewModelScope.launch {
                eventRepository.getEventById(eventId).firstOrNull()?.let { event ->
                    val locName = event.locationId?.let {
                        locationRepository.getLocationByIdOnce(it)?.name
                    } ?: ""
                    val isOvernight = event.endTime.toLocalDate().isAfter(event.startTime.toLocalDate())
                    _state.update {
                        it.copy(
                            eventId = event.id,
                            tripId = event.tripId,
                            type = event.type,
                            title = event.title,
                            startDate = event.startTime.toLocalDate(),
                            endDate = event.endTime.toLocalDate(),
                            startTime = event.startTime.toLocalTime(),
                            endTime = event.endTime.toLocalTime(),
                            isOvernight = isOvernight,
                            locationId = event.locationId,
                            locationName = locName,
                            whatWeAreDoing = event.whatWeAreDoing,
                            notes = event.notes,
                            backgroundImageUri = event.backgroundImageUri,
                            status = event.status,
                            isEditing = true,
                            createdAt = event.createdAt
                        )
                    }
                }
            }
        }

        if (eventId == null && presetLocationId != null) {
            viewModelScope.launch {
                locationRepository.getLocationByIdOnce(presetLocationId)?.let { place ->
                    _state.update {
                        it.copy(
                            locationId = place.id,
                            locationName = place.name,
                            // The place's name is the obvious first draft of the activity's
                            // title. Prefilled rather than fixed: "City Palace" is usually
                            // right, and when it isn't, it is one field to change.
                            title = it.title.ifBlank { place.name }
                        )
                    }
                }
            }
        }

        // Nothing here watches for the location picker's answer. That answer lands in the
        // *navigation entry's* handle, and this one — injected by Hilt — is a different object
        // that never receives it. The nav graph reads the entry and hands the place to the
        // screen, which calls `updateLocation`.
    }

    fun updateTitle(title: String) {
        _state.update { it.copy(title = title, validationError = null) }
    }

    fun updateType(type: EventType) {
        _state.update { current ->
            if (type == EventType.STAY) {
                current.copy(
                    type = type,
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(10, 30),
                    endDate = if (current.endDate.isAfter(current.startDate)) {
                        current.endDate
                    } else {
                        current.startDate.plusDays(1)
                    },
                    validationError = null
                )
            } else {
                current.copy(type = type, validationError = null)
            }
        }
    }

    fun updateStartDate(date: LocalDate) {
        _state.update { current ->
            val newEndDate = if (current.type == EventType.STAY && !current.endDate.isAfter(date)) {
                date.plusDays(1)
            } else if (current.type != EventType.STAY) {
                date
            } else {
                current.endDate
            }
            current.copy(startDate = date, endDate = newEndDate, validationError = null)
        }
    }

    fun updateEndDate(date: LocalDate) {
        _state.update { it.copy(endDate = date, validationError = null) }
    }

    fun updateStartTime(time: LocalTime) {
        _state.update { it.copy(startTime = time, validationError = null) }
    }

    fun updateEndTime(time: LocalTime) {
        _state.update { it.copy(endTime = time, validationError = null) }
    }

    fun toggleOvernight(isOvernight: Boolean) {
        _state.update { it.copy(isOvernight = isOvernight, validationError = null) }
    }

    fun updateLocation(locationId: Long, locationName: String) {
        _state.update {
            it.copy(
                locationId = locationId,
                locationName = locationName,
                // Prefill title if empty
                title = it.title.ifBlank { locationName }
            )
        }
    }

    fun clearLocation() {
        _state.update { it.copy(locationId = null, locationName = "") }
    }

    fun updateWhatWeAreDoing(text: String) {
        _state.update { it.copy(whatWeAreDoing = text) }
    }

    fun updateNotes(notes: String) {
        _state.update { it.copy(notes = notes) }
    }

    fun updateBackgroundImage(uri: String?) {
        _state.update { it.copy(backgroundImageUri = uri) }
    }

    fun clearBackgroundImage() {
        _state.update { it.copy(backgroundImageUri = null) }
    }

    fun save() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(validationError = "Please enter a title") }
            return
        }

        if (!s.isTimeRangeValid) {
            _state.update {
                it.copy(
                    validationError = if (s.type == EventType.STAY) {
                        "Check-out time must be after check-in time"
                    } else {
                        "End time must be after start time"
                    }
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }

            val startDateTime = s.startDateTime
            val endDateTime = s.endDateTime

            val event = Event(
                id = s.eventId ?: 0L,
                tripId = s.tripId,
                type = s.type,
                title = s.title.trim(),
                startTime = startDateTime,
                endTime = endDateTime,
                locationId = s.locationId,
                whatWeAreDoing = s.whatWeAreDoing.trim(),
                notes = s.notes.trim(),
                backgroundImageUri = s.backgroundImageUri,
                status = s.status,
                createdAt = s.createdAt ?: timeProvider.now(),
                updatedAt = timeProvider.now(),
                order = if (s.eventId == null) {
                    eventRepository.getNextOrder(s.tripId)
                } else {
                    0
                }
            )

            if (s.isEditing) {
                eventRepository.updateEvent(event)
            } else {
                eventRepository.insertEvent(event)
            }

            _state.update { it.copy(isSaving = false, saved = true) }
        }
    }
}
