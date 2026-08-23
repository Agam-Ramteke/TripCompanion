package com.tripcompanion.app.feature.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class TripEditorState(
    val tripId: Long? = null,
    val name: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now().plusDays(3),
    val status: TripStatus = TripStatus.PLANNING,
    /**
     * The trip's cover photo, copied into app-private storage.
     *
     * Held in the editor's state rather than written straight through, so cancelling really
     * cancels — and, more importantly, so [TripEditorViewModel.save] carries it back out. An
     * edit that rebuilt the trip without this field would silently drop the photo.
     */
    val coverImageUri: String? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val savedTripId: Long? = null,
    /**
     * Carried through an edit so saving does not overwrite it.
     *
     * Null for a trip being created, because it has no creation time until it is
     * saved and inventing one before then would be a lie the row keeps forever.
     */
    val createdAt: LocalDateTime? = null
) {
    /** §9: a trip cannot finish before it starts, so the button must not offer to save one. */
    val datesAreValid: Boolean get() = !endDate.isBefore(startDate)

    val canSave: Boolean get() = name.isNotBlank() && datesAreValid && !isSaving

    /**
     * Inclusive of both ends: a trip from the 3rd to the 6th is four days.
     *
     * Counted by [DateTimeUtils.dayCount] rather than here, so the editor's preview and the
     * itinerary's day tabs can never disagree about how long the trip is.
     */
    val dayCount: Int get() = if (datesAreValid) DateTimeUtils.dayCount(startDate, endDate) else 0

    /**
     * One fewer than the days, and never negative.
     *
     * A single-day trip has no nights, which is worth saying rather than showing "0 nights":
     * the screen leaves the word out entirely in that case.
     */
    val nightCount: Int get() = (dayCount - 1).coerceAtLeast(0)
}

@HiltViewModel
class TripEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val tripId: Long? = savedStateHandle.get<String>("tripId")?.toLongOrNull()

    private val _state = MutableStateFlow(TripEditorState())
    val state: StateFlow<TripEditorState> = _state.asStateFlow()

    init {
        if (tripId != null) {
            viewModelScope.launch {
                tripRepository.getTripById(tripId).firstOrNull()?.let { trip ->
                    _state.update {
                        it.copy(
                            tripId = trip.id,
                            name = trip.name,
                            startDate = trip.startDate,
                            endDate = trip.endDate,
                            status = trip.status,
                            coverImageUri = trip.coverImageUri,
                            isEditing = true,
                            createdAt = trip.createdAt
                        )
                    }
                }
            }
        }
    }

    fun updateName(name: String) {
        _state.update { it.copy(name = name) }
    }

    /**
     * Moving the start past the end drags the end along rather than rejecting the
     * date — someone shifting a trip a month later changes the start first, and
     * refusing that edit until they fix the end is backwards.
     */
    fun updateStartDate(date: LocalDate) {
        _state.update {
            it.copy(
                startDate = date,
                endDate = if (date.isAfter(it.endDate)) date else it.endDate
            )
        }
    }

    /**
     * The end is not dragged in the other direction. Picking an end before the start
     * is unambiguously a mistake, not a shift, so the value is kept and surfaced as
     * invalid instead of silently rewriting the start the user just chose.
     */
    fun updateEndDate(date: LocalDate) {
        _state.update { it.copy(endDate = date) }
    }

    fun updateStatus(status: TripStatus) {
        _state.update { it.copy(status = status) }
    }

    /**
     * The cover, already copied into app-private storage by the screen.
     *
     * Takes a nullable so the same call clears it: "remove photo" and "choose photo" are the
     * same edit with a different value, not two paths that can drift apart.
     */
    fun updateCover(uri: String?) {
        _state.update { it.copy(coverImageUri = uri) }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val now = timeProvider.now()
            val trip = Trip(
                id = current.tripId ?: 0,
                name = current.name.trim(),
                startDate = current.startDate,
                endDate = current.endDate,
                status = current.status,
                coverImageUri = current.coverImageUri,
                createdAt = current.createdAt ?: now,
                updatedAt = now
            )
            val resultId = if (current.isEditing) {
                tripRepository.updateTrip(trip)
                trip.id
            } else {
                tripRepository.insertTrip(trip)
            }
            _state.update { it.copy(isSaving = false, savedTripId = resultId) }
        }
    }
}
