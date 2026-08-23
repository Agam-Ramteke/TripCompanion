package com.tripcompanion.app.feature.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Which of the three lists a trip belongs in.
 *
 * Derived from the dates first and the stored status second, so a trip nobody remembered to
 * mark active still appears under Ongoing on the day it starts.
 */
enum class TripBucket(val label: String) {
    UPCOMING("Upcoming"),
    ONGOING("Ongoing"),
    PAST("Past")
}

/**
 * A trip plus the two numbers its card shows.
 *
 * The counts come from the trip's own events rather than a stored column, so they cannot go
 * stale when an activity is added or deleted.
 */
data class TripListItem(
    val trip: Trip,
    val bucket: TripBucket,
    val eventCount: Int,
    val completedCount: Int
) {
    val progressFraction: Float
        get() = if (eventCount == 0) 0f else completedCount.toFloat() / eventCount
}

data class TripListUiState(
    val bucket: TripBucket = TripBucket.UPCOMING,
    val items: List<TripListItem> = emptyList(),
    val upcomingCount: Int = 0,
    val ongoingCount: Int = 0,
    val pastCount: Int = 0,
    val now: LocalDateTime = LocalDateTime.MIN,
    val isLoading: Boolean = true
) {
    val totalCount: Int get() = upcomingCount + ongoingCount + pastCount
    val hasAnyTrip: Boolean get() = totalCount > 0

    fun countFor(bucket: TripBucket): Int = when (bucket) {
        TripBucket.UPCOMING -> upcomingCount
        TripBucket.ONGOING -> ongoingCount
        TripBucket.PAST -> pastCount
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripListViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    eventRepository: EventRepository,
    timeProvider: TimeProvider
) : ViewModel() {

    private val chosenBucket = MutableStateFlow<TripBucket?>(null)

    private val _state = MutableStateFlow(TripListUiState(now = timeProvider.now()))
    val state: StateFlow<TripListUiState> = _state.asStateFlow()

    /**
     * Each trip with its events.
     *
     * `flatMapLatest` re-subscribes only when the set of trips changes — putting the ticker in
     * this flow instead would tear down and rebuild every trip's query once a minute.
     */
    private val tripsWithEvents: Flow<List<Pair<Trip, List<Event>>>> =
        tripRepository.getAllTrips().flatMapLatest { trips ->
            if (trips.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    trips.map { trip -> eventRepository.getEventsForTrip(trip.id).map { trip to it } }
                ) { pairs -> pairs.toList() }
            }
        }

    init {
        viewModelScope.launch {
            combine(
                tripsWithEvents,
                timeProvider.ticker(),
                chosenBucket
            ) { pairs, now, chosen ->
                val today = now.toLocalDate()
                val all = pairs.map { (trip, events) ->
                    TripListItem(
                        trip = trip,
                        bucket = bucketOf(trip, today),
                        eventCount = events.size,
                        completedCount = events.count { it.status == EventStatus.COMPLETED }
                    )
                }
                val counts = TripBucket.entries.associateWith { bucket ->
                    all.count { it.bucket == bucket }
                }
                // Opens on a list that has something in it. Only used until the user picks,
                // after which their choice stands even when that list empties.
                val bucket = chosen ?: TripBucket.entries.firstOrNull { (counts[it] ?: 0) > 0 }
                    ?: TripBucket.UPCOMING

                TripListUiState(
                    bucket = bucket,
                    items = order(all.filter { it.bucket == bucket }, bucket),
                    upcomingCount = counts[TripBucket.UPCOMING] ?: 0,
                    ongoingCount = counts[TripBucket.ONGOING] ?: 0,
                    pastCount = counts[TripBucket.PAST] ?: 0,
                    now = now,
                    isLoading = false
                )
            }.collect { next -> _state.update { next } }
        }
    }

    fun setBucket(bucket: TripBucket) {
        chosenBucket.value = bucket
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch { tripRepository.deleteTrip(tripId) }
    }

    /**
     * Soonest first for the two forward-looking lists, most recent first for Past.
     *
     * A past list ordered ascending puts last year's trip above last week's, which is the
     * opposite of how anyone looks back through them.
     */
    private fun order(items: List<TripListItem>, bucket: TripBucket): List<TripListItem> =
        if (bucket == TripBucket.PAST) {
            items.sortedWith(compareByDescending<TripListItem> { it.trip.endDate }.thenByDescending { it.trip.id })
        } else {
            items.sortedWith(compareBy<TripListItem> { it.trip.startDate }.thenBy { it.trip.id })
        }

    /**
     * Dates decide, with one exception.
     *
     * A cancelled trip is never Ongoing however its dates read — the traveller is not on it.
     * Everything else falls out of the calendar, which means a trip nobody marked ACTIVE still
     * shows up as ongoing the morning it begins.
     */
    private fun bucketOf(trip: Trip, today: LocalDate): TripBucket = when {
        trip.status == TripStatus.CANCELLED -> TripBucket.PAST
        trip.status == TripStatus.COMPLETED -> TripBucket.PAST
        today < trip.startDate -> TripBucket.UPCOMING
        today > trip.endDate -> TripBucket.PAST
        else -> TripBucket.ONGOING
    }
}
