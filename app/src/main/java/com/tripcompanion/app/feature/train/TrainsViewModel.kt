package com.tripcompanion.app.feature.train

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.repository.TripRepository
import com.tripcompanion.app.domain.service.TrainStatusService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Where a journey is in time, judged only from the booking's own two timestamps.
 *
 * The list deliberately does not ask the network. Twelve trains on screen would be twelve
 * requests on a metered free tier to render three words each, and the words that matter here —
 * which of these have I already taken — are already in the database. Live delay belongs to the
 * one train the user opened.
 */
enum class TrainPhase { UPCOMING, RUNNING, ARRIVED }

/**
 * Which phase a booking is in at [now], from its own departure and arrival.
 *
 * Top-level and public because more than one caller needs the answer: this list sorts and filters
 * by it, and the itinerary and Home word a badge from it. One rule in one place, so a journey
 * cannot be "On board" on one screen and "Arrived" on another.
 */
fun trainPhaseAt(train: Train, now: LocalDateTime): TrainPhase = when {
    now.isBefore(train.departureTime) -> TrainPhase.UPCOMING
    now.isAfter(train.arrivalTime) -> TrainPhase.ARRIVED
    else -> TrainPhase.RUNNING
}

/** The tabs across the top of the Trains screen. */
enum class TrainFilter(val label: String) {
    UPCOMING("Upcoming"),
    PAST("Past"),
    ALL("All")
}

data class TrainListItem(
    val train: Train,
    val phase: TrainPhase,
    /** The trip this booking belongs to, so a card can say which trip it is part of. */
    val tripName: String
)

data class TrainsUiState(
    val filter: TrainFilter = TrainFilter.UPCOMING,
    /** Already filtered and sorted for display. */
    val items: List<TrainListItem> = emptyList(),
    /** Every booking, whatever the filter — so an empty tab can say "3 past journeys". */
    val totalCount: Int = 0,
    val upcomingCount: Int = 0,
    val pastCount: Int = 0,
    val now: LocalDateTime = LocalDateTime.MIN,
    /** Adding a train needs a trip to attach it to; without one the button explains itself. */
    val trips: List<Trip> = emptyList(),
    val isLive: Boolean = false,
    val providerName: String = "",
    val isLoading: Boolean = true
) {
    val hasAnyTrain: Boolean get() = totalCount > 0
    val hasTrips: Boolean get() = trips.isNotEmpty()
}

@HiltViewModel
class TrainsViewModel @Inject constructor(
    private val trainRepository: TrainRepository,
    tripRepository: TripRepository,
    trainStatusService: TrainStatusService,
    timeProvider: TimeProvider
) : ViewModel() {

    private val filter = MutableStateFlow(TrainFilter.UPCOMING)

    private val _state = MutableStateFlow(
        TrainsUiState(
            now = timeProvider.now(),
            isLive = trainStatusService.isLive,
            providerName = trainStatusService.providerName
        )
    )
    val state: StateFlow<TrainsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                trainRepository.getAllTrains(),
                tripRepository.getAllTrips(),
                timeProvider.ticker(),
                filter
            ) { trains, trips, now, selected ->
                val tripNames = trips.associate { it.id to it.name }
                val all = trains.map { train ->
                    TrainListItem(
                        train = train,
                        phase = trainPhaseAt(train, now),
                        tripName = tripNames[train.tripId].orEmpty()
                    )
                }
                _state.value.copy(
                    filter = selected,
                    items = all.filter { selected.accepts(it.phase) }.sortedWith(order(selected)),
                    totalCount = all.size,
                    upcomingCount = all.count { it.phase != TrainPhase.ARRIVED },
                    pastCount = all.count { it.phase == TrainPhase.ARRIVED },
                    now = now,
                    trips = trips,
                    isLoading = false
                )
            }.collect { next -> _state.update { next } }
        }
    }

    fun setFilter(next: TrainFilter) {
        filter.value = next
    }

    fun deleteTrain(trainId: Long) {
        viewModelScope.launch { trainRepository.deleteTrain(trainId) }
    }

    private fun TrainFilter.accepts(phase: TrainPhase): Boolean = when (this) {
        TrainFilter.UPCOMING -> phase != TrainPhase.ARRIVED
        TrainFilter.PAST -> phase == TrainPhase.ARRIVED
        TrainFilter.ALL -> true
    }

    /**
     * Soonest first while looking forward, most recent first when looking back.
     *
     * Both are "nearest to now". A past list ordered ascending buries last week's journey
     * under one from a year ago.
     */
    private fun order(filter: TrainFilter): Comparator<TrainListItem> =
        if (filter == TrainFilter.PAST) {
            compareByDescending<TrainListItem> { it.train.departureTime }.thenBy { it.train.id }
        } else {
            compareBy<TrainListItem> { it.train.departureTime }.thenBy { it.train.id }
        }
}
