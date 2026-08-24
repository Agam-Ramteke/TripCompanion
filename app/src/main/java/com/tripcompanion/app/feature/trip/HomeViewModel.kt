package com.tripcompanion.app.feature.trip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.data.SampleTripSeeder
import com.tripcompanion.app.data.prefs.UserPreferencesStore
import com.tripcompanion.app.domain.engine.TripState
import com.tripcompanion.app.domain.engine.TripStateEngine
import com.tripcompanion.app.domain.engine.TripStats
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.PlannedPhotoRepository
import com.tripcompanion.app.domain.repository.StayDetailsRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Everything Home draws, resolved to one instant.
 *
 * The screen changes shape with the trip's phase (§29) and every phase reads from this same
 * object — there is no second state class for "before the trip". [TripState.currentTripState]
 * decides which sections appear; nothing here is phase-specific.
 */
data class HomeUiState(
    val trip: Trip? = null,
    val tripState: TripState = TripState(trip = null),
    /** Counts behind the four stat cards, all derived from stored rows. */
    val stats: TripStats = TripStats(),
    val currentLocation: Location? = null,
    val nextLocation: Location? = null,
    /**
     * The train the traveller is about to be on, or is on now.
     *
     * Home's transit card reads coach, seat and platform from this instead of stating them,
     * so the card cannot contradict the journey it links to. Null when the trip has no train.
     */
    val upcomingTrain: Train? = null,
    /**
     * The activity Home leads with: what is happening now, else what is next.
     *
     * Resolved once, here, rather than at draw time — [focusTrain] and [nextUpImageUri] are about
     * *this* event, and a screen that re-derives "what's next" while it renders can end up drawing
     * one activity's title over another's photograph.
     */
    val focusEvent: Event? = null,
    /**
     * The booking behind [focusEvent], when the next thing is a booked journey.
     *
     * A train is an itinerary row like any other (§4), so the next-up card draws the ticket itself
     * — platform, coach, berths — rather than a journey-shaped activity carrying none of it.
     */
    val focusTrain: Train? = null,
    /**
     * The photograph behind the next-up card, or null to leave the card plain.
     *
     * Always one of the user's own images, resolved by [HomeViewModel.nextUpImageUri]. Nothing
     * here is fetched and none of it is stock, which is why the card still draws in airplane mode.
     */
    val nextUpImageUri: String? = null,
    /**
     * The clock the screen renders. Held in state rather than read at draw time so the
     * countdown, the plan strip's current marker and the stat cards all describe the same
     * minute — a screen that reads the clock in three places shows three different minutes
     * at a boundary.
     */
    val now: LocalDateTime = LocalDateTime.MIN,
    /** The day the plan strip draws: today while the trip is on, its nearest end otherwise. */
    val focusedDay: LocalDate = LocalDate.MIN,
    /** Events on [focusedDay], statuses already computed by the engine (§21). */
    val focusedDayEvents: List<Event> = emptyList(),
    val isLoading: Boolean = true,
    val hasTrips: Boolean = false,
    val trips: List<Trip> = emptyList(),
    /** Greets by name when one is set in Settings, and says nothing personal when it isn't. */
    val travellerName: String = "",
    /** True while the sample trip is being written, so the button can't be pressed twice. */
    val isSeeding: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val eventRepository: EventRepository,
    private val locationRepository: LocationRepository,
    private val trainRepository: TrainRepository,
    private val photoRepository: PlannedPhotoRepository,
    private val stayDetailsRepository: StayDetailsRepository,
    private val sampleTripSeeder: SampleTripSeeder,
    private val userPreferences: UserPreferencesStore,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val engine = TripStateEngine()
    private val selectedTripId = MutableStateFlow<Long?>(null)

    private val _state = MutableStateFlow(HomeUiState(now = timeProvider.now()))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // The greeting's name. Its own collector because it changes on a Settings edit and
        // has nothing to do with the trip, so folding it into the trip combine would
        // recompute the whole screen when someone fixes a typo in their name.
        viewModelScope.launch {
            userPreferences.preferences.collect { prefs ->
                _state.update { it.copy(travellerName = prefs.travellerName) }
            }
        }

        // Which trip is on screen. Kept separate from the trip's contents so
        // switching trips does not re-subscribe to the whole database.
        viewModelScope.launch {
            tripRepository.getAllTrips().collect { trips ->
                _state.update { current ->
                    current.copy(
                        trips = trips,
                        hasTrips = trips.isNotEmpty(),
                        isLoading = if (trips.isEmpty()) false else current.isLoading,
                        trip = if (trips.isEmpty()) null else current.trip,
                        tripState = if (trips.isEmpty()) TripState(trip = null) else current.tripState
                    )
                }

                val selected = selectedTripId.value
                selectedTripId.value = when {
                    trips.isEmpty() -> null
                    selected == null || trips.none { it.id == selected } -> defaultTrip(trips).id
                    else -> selected
                }
            }
        }

        // The trip's contents, recomputed on every data change *and* every minute.
        //
        // flatMapLatest is what makes selectTrip cheap: switching cancels the
        // previous trip's collectors instead of stacking another one behind them.
        // The old code re-launched a collector on each 30-second refresh, so an
        // hour on this screen left 120 of them running against the same rows.
        viewModelScope.launch {
            selectedTripId
                .flatMapLatest { tripId ->
                    if (tripId == null) {
                        flowOf(null)
                    } else {
                        combine(
                            tripRepository.getTripById(tripId),
                            eventRepository.getEventsForTrip(tripId),
                            trainRepository.getTrainsForTrip(tripId),
                            photoRepository.countForTrip(tripId),
                            timeProvider.ticker()
                        ) { trip, events, trains, photoCount, now ->
                            Snapshot(trip, events, trains, photoCount, now)
                        }
                    }
                }
                .collect { snapshot ->
                    if (snapshot == null) {
                        _state.update {
                            it.copy(
                                trip = null,
                                tripState = TripState(trip = null),
                                stats = TripStats(),
                                currentLocation = null,
                                nextLocation = null,
                                upcomingTrain = null,
                                focusEvent = null,
                                focusTrain = null,
                                nextUpImageUri = null,
                                focusedDayEvents = emptyList(),
                                isLoading = false
                            )
                        }
                        return@collect
                    }

                    val (trip, events, trains, photoCount, now) = snapshot
                    val tripState = engine.computeState(trip, events, now)
                    val currentLocation = tripState.currentEvent?.locationId?.let {
                        locationRepository.getLocationByIdOnce(it)
                    }
                    val nextLocation = tripState.nextEvent?.locationId?.let {
                        locationRepository.getLocationByIdOnce(it)
                    }
                    val focusedDay = focusedDay(trip, now.toLocalDate())

                    // The one activity the screen leads with, and the two things that describe it.
                    // A journey with a booking behind it is drawn as the ticket, so the card can
                    // say which coach and which berths instead of only where and when.
                    val focus = tripState.currentEvent ?: tripState.nextEvent
                    val focusPlace =
                        if (tripState.currentEvent != null) currentLocation else nextLocation
                    val focusTrain = focus?.let { event ->
                        trains.firstOrNull { it.eventId == event.id }
                    }
                    val backdrop = nextUpImageUri(focus, focusPlace, trip)

                    _state.update {
                        it.copy(
                            trip = trip,
                            tripState = tripState,
                            stats = TripStats.compute(
                                trip = trip,
                                events = tripState.eventsWithComputedStatus,
                                photoCount = photoCount,
                                today = now.toLocalDate()
                            ),
                            currentLocation = currentLocation,
                            nextLocation = nextLocation,
                            upcomingTrain = pickTrain(trains, now),
                            focusEvent = focus,
                            focusTrain = focusTrain,
                            nextUpImageUri = backdrop,
                            now = now,
                            focusedDay = focusedDay,
                            focusedDayEvents = tripState.eventsWithComputedStatus.filter { event ->
                                event.startTime.toLocalDate() == focusedDay ||
                                    event.endTime.toLocalDate() == focusedDay
                            },
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun selectTrip(tripId: Long) {
        selectedTripId.value = tripId
    }

    fun completeEvent(event: Event) = setStatus(event, EventStatus.COMPLETED)

    fun skipEvent(event: Event) = setStatus(event, EventStatus.SKIPPED)

    /**
     * Writes the sample trip and selects it, so the screen fills in rather than the user
     * having to go and find what just appeared.
     */
    fun loadSampleTrip() {
        if (_state.value.isSeeding) return
        _state.update { it.copy(isSeeding = true) }
        viewModelScope.launch {
            try {
                selectedTripId.value = sampleTripSeeder.seed()
            } finally {
                _state.update { it.copy(isSeeding = false) }
            }
        }
    }

    private fun setStatus(event: Event, status: EventStatus) {
        viewModelScope.launch {
            // The event handed in carries the engine's computed status, so copying
            // it wholesale would persist a derived value. Only the status changes.
            eventRepository.updateEvent(event.copy(status = status))
        }
    }

    /** An active trip if there is one, else whatever the repository ordered first. */
    private fun defaultTrip(trips: List<Trip>): Trip =
        trips.firstOrNull { it.status == TripStatus.ACTIVE } ?: trips.first()

    /**
     * The train Home should talk about.
     *
     * A run in progress wins, because that is the one the user is sitting on. Otherwise the
     * soonest departure still ahead. Once every train has arrived the most recent one is
     * shown rather than nothing, so the card on a finished trip reads as history instead of
     * disappearing and taking the ticket link with it.
     */
    private fun pickTrain(trains: List<Train>, now: LocalDateTime): Train? {
        if (trains.isEmpty()) return null
        trains.firstOrNull { !now.isBefore(it.departureTime) && !now.isAfter(it.arrivalTime) }
            ?.let { return it }
        return trains.filter { it.departureTime.isAfter(now) }.minByOrNull { it.departureTime }
            ?: trains.maxByOrNull { it.departureTime }
    }

    /**
     * The picture behind Home's next-up card, or null when this plan has none.
     *
     * A chain rather than one column. First an explicit choice — a background the user set on the
     * activity itself — which outranks everything derived, because they picked it precisely to be
     * this card's face. Failing that, a single card stands in for four kinds of plan and each keeps
     * its picture somewhere else: a stay's is the room, a visit's is the place, and an activity the
     * user has planned a shot for has the reference photo they saved. The trip cover is the last
     * resort — no longer a picture of *this* activity, but still one of this trip.
     *
     * A stay asks its own paperwork before the place, so the backdrop is the same photograph the
     * stay card already shows for that booking rather than a second opinion about it.
     *
     * Every link is a user image copied into app storage, so the card draws offline. Read one
     * event at a time rather than folded into the combine: this is one row per redraw, against
     * five more flows fanning out over every stay and photo plan in the database.
     */
    private suspend fun nextUpImageUri(event: Event?, place: Location?, trip: Trip?): String? {
        if (event == null) return null
        event.backgroundImageUri?.usable()?.let { return it }
        if (event.type == EventType.STAY) {
            stayDetailsRepository.getForEventOnce(event.id)?.photoUri?.usable()?.let { return it }
        }
        place?.photoUri?.usable()?.let { return it }
        photoRepository.getPhotosForEvent(event.id).first()
            .firstNotNullOfOrNull { it.referenceImageUri?.usable() }
            ?.let { return it }
        return trip?.coverImageUri?.usable()
    }

    /** A blank column and a missing one are the same thing to a screen: there is no picture. */
    private fun String.usable(): String? = takeIf { it.isNotBlank() }

    /**
     * Which day the plan strip shows.
     *
     * Today, while today is inside the trip. Before departure the first day is the useful
     * answer — an empty strip on a trip that starts next week tells the user nothing. After
     * it ends, the last day, so the screen closes on what happened rather than on a blank row.
     */
    private fun focusedDay(trip: Trip?, today: LocalDate): LocalDate = when {
        trip == null -> today
        today < trip.startDate -> trip.startDate
        today > trip.endDate -> trip.endDate
        else -> today
    }

    /** Named so the five-flow combine has one destructurable value instead of a nested pair. */
    private data class Snapshot(
        val trip: Trip?,
        val events: List<Event>,
        val trains: List<Train>,
        val photoCount: Int,
        val now: LocalDateTime
    )
}
