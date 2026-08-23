package com.tripcompanion.app.feature.train

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.data.prefs.UserPreferencesStore
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.service.TrainScheduleOutcome
import com.tripcompanion.app.domain.service.TrainStatusError
import com.tripcompanion.app.domain.service.TrainStatusOutcome
import com.tripcompanion.app.domain.service.TrainStatusService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * One train: its booking, its timetable, and where it is right now.
 *
 * The screen has four tabs and they all read this one object. Live status, route and ticket
 * disagreeing about a coach number is the exact failure the old hardcoded transit card had,
 * so there is one record and every tab renders it.
 */
data class TrainDetailUiState(
    val train: Train? = null,
    /** The stored timetable, ordered by serial. Empty until fetched or entered. */
    val stops: List<TrainStop> = emptyList(),
    /** The last known position. Null before the first successful fetch or projection. */
    val status: TrainRunStatus? = null,
    /** The journey this booking is attached to, so the screen can link back to the itinerary. */
    val linkedEvent: Event? = null,
    val now: LocalDateTime = LocalDateTime.MIN,
    val isRefreshing: Boolean = false,
    val isLoadingSchedule: Boolean = false,
    /**
     * Why the last refresh failed, if it did.
     *
     * Held alongside [status] rather than replacing it: the right thing to show when a
     * refresh fails on a moving train is the last known position with its age.
     */
    val error: TrainStatusError? = null,
    /** Set when a schedule fetch had something to say — "this provider can't fetch timetables". */
    val notice: String? = null,
    /** False when the position on screen is projected from the timetable rather than observed. */
    val isLive: Boolean = false,
    val providerName: String = "",
    val isLoading: Boolean = true
) {
    /** How stale the snapshot is, for the "updated N min ago" line. Null with no snapshot. */
    val minutesSinceUpdate: Long?
        get() = status?.let { Duration.between(it.fetchedAt, now).toMinutes().coerceAtLeast(0) }

    val hasSchedule: Boolean get() = stops.isNotEmpty()
}

/**
 * One sentence per failure, in the user's terms rather than the transport's.
 *
 * Top-level because both the ViewModel and the screen need it: the tracking screen shows the
 * same sentence for a failed refresh that a schedule fetch shows for a failed fetch, and two
 * copies of this `when` would drift apart the first time an error case was added.
 */
fun trainStatusMessage(error: TrainStatusError): String = when (error) {
    TrainStatusError.NETWORK_UNAVAILABLE -> "No connection. Showing the last known position."
    TrainStatusError.TIMEOUT -> "The railway did not answer in time. Try again in a moment."
    TrainStatusError.RATE_LIMITED -> "Too many checks just now. Wait a minute and pull to refresh."
    TrainStatusError.PROVIDER_ERROR -> "The railway's service returned an error."
    TrainStatusError.MALFORMED_RESPONSE -> "The railway sent something this app could not read."
    TrainStatusError.TRAIN_NOT_FOUND -> "This train number is not running on that date."
    TrainStatusError.NO_SCHEDULE -> "No timetable stored yet, so there is nothing to track against."
    TrainStatusError.UNKNOWN -> "Could not check the train just now."
}

@HiltViewModel
class TrainDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trainRepository: TrainRepository,
    private val eventRepository: EventRepository,
    private val trainStatusService: TrainStatusService,
    private val userPreferences: UserPreferencesStore,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val trainId: Long = savedStateHandle.get<String>("trainId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(
        TrainDetailUiState(
            now = timeProvider.now(),
            isLive = trainStatusService.isLive,
            providerName = trainStatusService.providerName
        )
    )
    val state: StateFlow<TrainDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                trainRepository.getTrainById(trainId),
                trainRepository.getStops(trainId),
                trainRepository.observeRunStatus(trainId),
                timeProvider.ticker()
            ) { train, stops, status, now ->
                Snapshot(train, stops, status, now)
            }.collect { (train, stops, status, now) ->
                _state.update {
                    it.copy(
                        train = train,
                        stops = stops,
                        status = status,
                        now = now,
                        isLoading = false
                    )
                }
            }
        }

        // The journey this train belongs to, if the user linked one. Separate collector
        // because it depends on a field of the train and would otherwise re-subscribe to
        // the whole train on every tick.
        viewModelScope.launch {
            trainRepository.getTrainById(trainId)
                .filterNotNull()
                .collect { train ->
                    val event = train.eventId?.let { eventRepository.getEventById(it).first() }
                    _state.update { it.copy(linkedEvent = event) }
                }
        }

        // First look, then again on each tick. The service holds the freshness rule
        // (TrainStatusService.CACHE_TTL_MINUTES), so a call per minute costs a comparison
        // rather than a request — deciding here would put the same rule in two places.
        viewModelScope.launch {
            refresh(force = false)
            timeProvider.ticker().collect {
                if (userPreferences.preferences.value.autoRefreshLiveStatus) {
                    refresh(force = false)
                }
            }
        }
    }

    /**
     * Ask where the train is.
     *
     * @param force set by pull-to-refresh, where the user has asked for a request and should
     *   get one whatever the cache says.
     */
    fun refresh(force: Boolean = false) {
        if (trainId == 0L) return
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            try {
                when (val outcome = trainStatusService.refresh(trainId, force)) {
                    is TrainStatusOutcome.Updated ->
                        _state.update { it.copy(status = outcome.status, error = null) }
                    is TrainStatusOutcome.Cached ->
                        _state.update { it.copy(status = outcome.status, error = null) }
                    is TrainStatusOutcome.Failed ->
                        _state.update {
                            it.copy(status = outcome.cached ?: it.status, error = outcome.error)
                        }
                }
            } finally {
                _state.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Fetch the timetable once, so the route and ETAs work offline for the rest of the trip. */
    fun loadSchedule() {
        if (trainId == 0L || _state.value.isLoadingSchedule) return
        _state.update { it.copy(isLoadingSchedule = true, notice = null) }
        viewModelScope.launch {
            try {
                val notice = when (val outcome = trainStatusService.refreshSchedule(trainId)) {
                    is TrainScheduleOutcome.Loaded ->
                        if (outcome.stops.isEmpty()) "No timetable found for this train number."
                        else null
                    TrainScheduleOutcome.Unsupported ->
                        "Timetables need a live data source. Add stops by hand for now."
                    is TrainScheduleOutcome.Failed -> messageFor(outcome.error)
                }
                _state.update { it.copy(notice = notice) }
                // A fresh timetable changes every projected ETA, so ask again with it in place.
                if (notice == null) refresh(force = true)
            } finally {
                _state.update { it.copy(isLoadingSchedule = false) }
            }
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    /** The API carries no platform, so this one is the user's own note. */
    fun setPlatform(platform: String) = edit { it.copy(platform = platform.trim()) }

    /** Feeds the offline projection: without a feed, the user's own estimate is the best input. */
    fun setKnownDelay(minutes: Int) = edit { it.copy(knownDelayMinutes = minutes.coerceAtLeast(0)) }

    /**
     * Moves one passenger to a different coach or berth.
     *
     * Per passenger and not per booking, because the railway reallots seat by seat: being moved
     * out of a coach at the last minute happens to one person on a PNR as readily as to all of
     * them. [TrainAllotment.berthType] and the booking status are carried over rather than
     * re-entered — the platform fix is "we are in S5 now", not a whole re-booking — and the
     * verbatim `currentStatusText` is rewritten to match so the ticket screen cannot end up
     * showing a berth the app has just been told is wrong.
     */
    fun setPassengerAllotment(passengerId: Long, coach: String, berth: String) = edit { train ->
        train.copy(
            passengers = train.passengers.map { passenger ->
                if (passenger.id != passengerId) {
                    passenger
                } else {
                    val moved = passenger.allotment.copy(coach = coach.trim().uppercase(), berth = berth.trim())
                    passenger.copy(allotment = moved, currentStatusText = moved.text)
                }
            }
        )
    }

    fun deleteTrain(onDone: () -> Unit) {
        viewModelScope.launch {
            trainRepository.deleteTrain(trainId)
            onDone()
        }
    }

    private fun edit(change: (Train) -> Train) {
        val train = _state.value.train ?: return
        viewModelScope.launch { trainRepository.updateTrain(change(train)) }
    }

    /** One sentence per failure, in the user's terms rather than the transport's. */
    private fun messageFor(error: TrainStatusError): String = trainStatusMessage(error)

    private data class Snapshot(
        val train: Train?,
        val stops: List<TrainStop>,
        val status: TrainRunStatus?,
        val now: LocalDateTime
    )
}
