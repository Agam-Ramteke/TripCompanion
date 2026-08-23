package com.tripcompanion.app.feature.trip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.data.prefs.UserPreferencesStore
import com.tripcompanion.app.domain.engine.TripStateEngine
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.StayDetails
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.StayDetailsRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.repository.TripRepository
import com.tripcompanion.app.domain.service.JourneyEventLinker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * One tab in the day selector.
 *
 * Every day of the trip gets one, including the empty ones — an empty day is exactly where a
 * user needs to tap to add something, so hiding it would hide the gap in the plan.
 */
data class TimelineDay(
    val date: LocalDate,
    /** 1-based, counted from the trip's first day. */
    val dayNumber: Int,
    val eventCount: Int,
    val isToday: Boolean
)

data class TimelineUiState(
    val trip: Trip? = null,
    val days: List<TimelineDay> = emptyList(),
    val selectedDay: LocalDate? = null,
    /** Events on the selected day, statuses already computed by the engine (§21). */
    val dayEvents: List<Event> = emptyList(),
    /** How many of the selected day's events the "hide completed" preference is hiding. */
    val hiddenCompletedCount: Int = 0,
    /** Places for the day's events, keyed by id, so a row can name where it happens. */
    val places: Map<Long, Location> = emptyMap(),
    /**
     * Hotel paperwork for the day's STAY events, keyed by event id.
     *
     * A stay on the itinerary is drawn as the same card the Stay screen draws (§4), and that card
     * shows the room photo and the property's own address — both of which live here rather than on
     * the event.
     */
    val stayDetails: Map<Long, StayDetails> = emptyMap(),
    /**
     * Bookings for the day's JOURNEY events, keyed by event id.
     *
     * A journey with a ticket behind it is drawn as the ticket — same reasoning as [stayDetails]:
     * the coach, the berths and the PNR are the reason the row exists, and none of them live on
     * the event. A JOURNEY event with no booking stays an ordinary activity card.
     */
    val trains: Map<Long, Train> = emptyMap(),
    val currentEventId: Long? = null,
    val totalEventCount: Int = 0,
    val completedEventCount: Int = 0,
    val showCompleted: Boolean = true,
    val now: LocalDateTime = LocalDateTime.MIN,
    val isLoading: Boolean = true
) {
    val hasEvents: Boolean get() = totalEventCount > 0
    val progressFraction: Float
        get() = if (totalEventCount == 0) 0f else completedEventCount.toFloat() / totalEventCount
}

/**
 * The itinerary, one day at a time.
 *
 * The day selector partitions the trip and the timeline draws whichever day is chosen. There
 * is no sample data here and none in the screen: what the user typed is what the screen shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
    private val stayDetailsRepository: StayDetailsRepository,
    private val trainRepository: TrainRepository,
    private val journeyEventLinker: JourneyEventLinker,
    private val userPreferences: UserPreferencesStore,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val paramTripId: Long? = savedStateHandle.get<String>("tripId")?.toLongOrNull()
    private val engine = TripStateEngine()

    /** Null until the user taps a tab, so the default can follow the clock until then. */
    private val chosenDay = MutableStateFlow<LocalDate?>(null)

    private val _state = MutableStateFlow(TimelineUiState(now = timeProvider.now()))
    val state: StateFlow<TimelineUiState> = _state.asStateFlow()

    init {
        // Trains recorded before journeys were part of the itinerary have no row to appear in.
        // Done here rather than at startup because there is no application-level scope to own it,
        // and it is a no-op once every booking is linked.
        viewModelScope.launch { journeyEventLinker.linkExistingTrains() }

        viewModelScope.launch {
            resolveTripId()
                .flatMapLatest { tripId ->
                    if (tripId == null) {
                        flowOf(null)
                    } else {
                        // `combine` takes at most five flows, and this screen needs eight, so the
                        // pairs that always change together are combined first. Nesting rather
                        // than collecting separately keeps one snapshot per emission: the day's
                        // rows, their stay paperwork, their tickets and the clock are always the
                        // same moment.
                        combine(
                            tripRepository.getTripById(tripId),
                            eventRepository.getEventsForTrip(tripId),
                            combine(
                                locationRepository.getAllLocations(),
                                trainRepository.getTrainsForTrip(tripId)
                            ) { places, trains -> places to trains },
                            combine(
                                stayDetailsRepository.getAll(),
                                userPreferences.preferences
                            ) { stays, prefs -> stays to prefs.showCompletedActivities },
                            combine(chosenDay, timeProvider.ticker()) { day, now -> day to now }
                        ) { trip, events, (places, trains), (stays, showCompleted), (day, now) ->
                            trip?.let {
                                Snapshot(it, events, places, trains, stays, showCompleted, day, now)
                            }
                        }
                    }
                }
                .collect { snapshot ->
                    if (snapshot == null) {
                        _state.update { it.copy(isLoading = false) }
                        return@collect
                    }
                    _state.update { render(snapshot) }
                }
        }
    }

    fun selectDay(day: LocalDate) {
        chosenDay.value = day
    }

    fun setShowCompleted(show: Boolean) = userPreferences.setShowCompletedActivities(show)

    fun completeEvent(event: Event) = setStatus(event, EventStatus.COMPLETED)

    fun skipEvent(event: Event) = setStatus(event, EventStatus.SKIPPED)

    /**
     * Clears a Done or Skipped decision and hands the event back to the engine,
     * which will call it whatever the clock now implies (§21).
     */
    fun reopenEvent(event: Event) = setStatus(event, EventStatus.UPCOMING)

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch { eventRepository.deleteEvent(eventId) }
    }

    private fun setStatus(event: Event, status: EventStatus) {
        viewModelScope.launch {
            // The event carries the engine's computed status, so only the field the user
            // actually decided is written back.
            eventRepository.updateEvent(event.copy(status = status))
        }
    }

    /**
     * Which trip is on screen, as an id.
     *
     * A route argument wins. Without one — the bottom-bar Itinerary tab has no trip to pass —
     * the active trip, else the first, which is the same rule Home and the map use. Emitting
     * the id rather than the trip means renaming a trip does not tear down its event query.
     */
    private fun resolveTripId(): Flow<Long?> = tripRepository.getAllTrips()
        .map { trips ->
            (paramTripId?.takeIf { it > 0L }?.let { id -> trips.firstOrNull { it.id == id } }
                ?: trips.firstOrNull { it.status == TripStatus.ACTIVE }
                ?: trips.firstOrNull())?.id
        }
        .distinctUntilChanged()

    private fun render(snapshot: Snapshot): TimelineUiState {
        val (trip, events, allPlaces, allTrains, allStayDetails, showCompleted, chosen, now) = snapshot
        val computed = engine.computeState(trip, events, now)
        val withStatus = computed.eventsWithComputedStatus

        val byDay = withStatus.groupBy { it.startTime.toLocalDate() }
        val today = now.toLocalDate()
        val days = tripDays(trip).map { date ->
            TimelineDay(
                date = date,
                dayNumber = ChronoUnit.DAYS.between(trip.startDate, date).toInt() + 1,
                eventCount = byDay[date]?.size ?: 0,
                isToday = date == today
            )
        }
        val selected = chosen?.takeIf { candidate -> days.any { it.date == candidate } }
            ?: defaultDay(trip, today)

        val onDay = byDay[selected].orEmpty()
        val visible = if (showCompleted) onDay else onDay.filter { it.status != EventStatus.COMPLETED }
        val placesById = allPlaces.associateBy { it.id }
        val stayDetailsByEvent = allStayDetails.associateBy { it.eventId }
        val trainsByEvent = allTrains.mapNotNull { train -> train.eventId?.let { it to train } }
            .toMap()

        return TimelineUiState(
            trip = trip,
            days = days,
            selectedDay = selected,
            dayEvents = visible,
            hiddenCompletedCount = onDay.size - visible.size,
            places = visible.mapNotNull { event ->
                event.locationId?.let { id -> placesById[id]?.let { id to it } }
            }.toMap(),
            stayDetails = visible.mapNotNull { event ->
                stayDetailsByEvent[event.id]?.let { event.id to it }
            }.toMap(),
            trains = visible.mapNotNull { event ->
                trainsByEvent[event.id]?.let { event.id to it }
            }.toMap(),
            currentEventId = computed.currentEvent?.id,
            totalEventCount = computed.totalEventCount,
            completedEventCount = computed.completedEventCount,
            showCompleted = showCompleted,
            now = now,
            isLoading = false
        )
    }

    /**
     * Every date the trip covers.
     *
     * Built from the trip's own dates rather than from the events, so a day with nothing on it
     * still gets a tab. An end date before the start date yields a single day rather than an
     * empty selector — bad data should not make the screen unusable.
     */
    private fun tripDays(trip: Trip): List<LocalDate> {
        val span = ChronoUnit.DAYS.between(trip.startDate, trip.endDate).toInt()
        if (span < 0) return listOf(trip.startDate)
        return (0..span).map { trip.startDate.plusDays(it.toLong()) }
    }

    /** Today while the trip is running, its first day before, its last day after. */
    private fun defaultDay(trip: Trip, today: LocalDate): LocalDate = when {
        today < trip.startDate -> trip.startDate
        today > trip.endDate -> trip.endDate
        else -> today
    }

    private data class Snapshot(
        val trip: Trip,
        val events: List<Event>,
        val places: List<Location>,
        val trains: List<Train>,
        val stayDetails: List<StayDetails>,
        val showCompleted: Boolean,
        val chosenDay: LocalDate?,
        val now: LocalDateTime
    )
}
