package com.tripcompanion.app.feature.location

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.core.util.GeoUtils
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.service.LocationSearchError
import com.tripcompanion.app.domain.service.LocationSearchOutcome
import com.tripcompanion.app.domain.service.LocationSearchService
import com.tripcompanion.app.domain.service.SearchViewport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the search box is currently showing.
 *
 * [Failed] carries the typed [LocationSearchError] rather than a message string:
 * choosing words is the UI's job, and a ViewModel that hands the screen a
 * pre-baked sentence has quietly become the copy deck.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    data class Success(val results: List<SearchResultLocation>) : SearchUiState
    data class Empty(val query: String) : SearchUiState
    data class Failed(val error: LocationSearchError) : SearchUiState
}

data class LocationPickerState(
    val locations: List<Location> = emptyList(),
    val searchQuery: String = "",
    val searchState: SearchUiState = SearchUiState.Idle,
    val name: String = "",
    val address: String = "",
    // India's geographic centroid — a neutral starting view. Not a trip-specific
    // default: §4 forbids the app assuming any particular place.
    val latitude: Double = 20.5937,
    val longitude: Double = 78.9629,
    val category: String = "",
    val providerPlaceId: String? = null,
    val providerName: String? = null,
    val isSaving: Boolean = false,
    val showSavedList: Boolean = false,
    val isSearchingOpen: Boolean = true,
    /** Bumped whenever the map should recentre; the screen watches it as a signal. */
    val mapCenterTrigger: Int = 0,
    /**
     * How tight to zoom on the next recentre.
     *
     * A place the user chose gets street level, because they picked that building. The trip's
     * own area gets the city, because a trip's first stop is only a hint about where to look —
     * zooming to a doorway on a hint means the first thing the user does is zoom out.
     */
    val recenterZoom: Double = STREET_ZOOM,
    /**
     * The rectangle the map is showing, once it has reported one.
     *
     * Null until the user moves the map or the view is seeded. Search reads it so results are
     * ranked from where the user is looking rather than from the middle of the country.
     */
    val viewport: SearchViewport? = null
) {
    /** A place needs coordinates to be saved, and it always has them here. */
    val canConfirm: Boolean get() = !isSaving
}

/** Street level: one building fills the screen. */
const val STREET_ZOOM = 16.0

/** City level: enough of a place to recognise it, which is all a search bias needs. */
const val AREA_ZOOM = 12.0

@OptIn(FlowPreview::class)
@HiltViewModel
class LocationPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationRepository: LocationRepository,
    private val eventRepository: EventRepository,
    private val locationSearchService: LocationSearchService,
    private val timeProvider: TimeProvider
) : ViewModel() {

    /**
     * The trip this place is being picked for, when there is one.
     *
     * Absent when the picker is opened from the places list, which is not about any one trip.
     * Used only to decide where to point the map first — never to restrict what can be found.
     */
    private val tripId: Long? = savedStateHandle.get<String>("tripId")
        ?.toLongOrNull()
        ?.takeIf { it > 0L }

    private val _state = MutableStateFlow(LocationPickerState())
    val state: StateFlow<LocationPickerState> = _state.asStateFlow()

    private val _queryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        // Saved locations, which work with no network at all (§13).
        viewModelScope.launch {
            locationRepository.getAllLocations().collect { locations ->
                _state.update { it.copy(locations = locations) }
            }
        }

        seedFromTrip()

        // Debounce belongs here rather than in the service: it is a property of
        // someone typing, not of searching (§11).
        viewModelScope.launch {
            _queryFlow
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { query -> performSearch(query) }
        }
    }

    /**
     * Points the map at the trip before the user has typed anything.
     *
     * The first search is the one most likely to fail, because it happens when the app knows
     * least: an unbiased query for a common name returns the famous one on the other side of
     * the world. A trip that already has a mapped stop knows roughly where its places are, so
     * the map opens there and the very first search is biased correctly.
     *
     * The *earliest* stop, not an average of them. Averaging two cities lands the map in a
     * field between them, which is a worse bias than none — where a trip begins is a real
     * place, and for a trip that stays in one city it is the right one.
     */
    private fun seedFromTrip() {
        val trip = tripId ?: return
        viewModelScope.launch {
            val anchor = eventRepository.getEventsForTrip(trip).first()
                .sortedBy { it.startTime }
                .mapNotNull { it.locationId }
                .distinct()
                .firstNotNullOfOrNull { id ->
                    locationRepository.getLocationByIdOnce(id)
                        ?.takeIf { GeoUtils.hasPosition(it.latitude, it.longitude) }
                }
                ?: return@launch

            _state.update {
                // Only the camera moves. Name, address and category stay empty: the user came
                // here to pick a new place, and prefilling them with a stop they already have
                // would offer to save a duplicate.
                it.copy(
                    latitude = anchor.latitude,
                    longitude = anchor.longitude,
                    recenterZoom = AREA_ZOOM,
                    mapCenterTrigger = it.mapCenterTrigger + 1
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _state.update { it.copy(searchQuery = query) }
        _queryFlow.value = query
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searchQuery = "", searchState = SearchUiState.Idle) }
        _queryFlow.value = ""
    }

    /** Re-runs the last query, for the retry affordance on a failure. */
    fun retrySearch() {
        performSearch(_state.value.searchQuery)
    }

    private fun performSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < LocationSearchService.MIN_QUERY_LENGTH) {
            searchJob?.cancel()
            _state.update { it.copy(searchState = SearchUiState.Idle) }
            return
        }

        // Cancelling the previous job is what stops a slow earlier request from
        // landing after a faster later one and overwriting fresher results.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searchState = SearchUiState.Searching) }
            val outcome = locationSearchService.searchPlaces(trimmed, _state.value.viewport)
            val next = when (outcome) {
                is LocationSearchOutcome.Results -> SearchUiState.Success(outcome.places)
                LocationSearchOutcome.Empty -> SearchUiState.Empty(trimmed)
                is LocationSearchOutcome.Failed -> SearchUiState.Failed(outcome.error)
            }
            _state.update { it.copy(searchState = next) }
        }
    }

    /**
     * Called as the map settles, with the rectangle it is now showing.
     *
     * Stored rather than acted on: it is read at the moment a search runs, so the bias is
     * always the view the user was looking at when they typed.
     */
    fun updateViewport(north: Double, east: Double, south: Double, west: Double) {
        val next = SearchViewport(north = north, east = east, south = south, west = west)
        // A viewport that makes no sense — zero span before the first layout, or a box across
        // the antimeridian — is dropped rather than stored, so the last good one keeps working.
        if (!next.isUsable) return
        _state.update { it.copy(viewport = next) }
    }

    fun selectSearchResult(result: SearchResultLocation) {
        _state.update {
            it.copy(
                name = result.name,
                address = result.formattedAddress,
                latitude = round4(result.latitude),
                longitude = round4(result.longitude),
                category = result.category,
                providerPlaceId = result.providerPlaceId,
                providerName = result.providerName,
                searchState = SearchUiState.Idle,
                isSearchingOpen = false,
                // Street level: this is a building the user named, not a region to look around.
                recenterZoom = STREET_ZOOM,
                mapCenterTrigger = it.mapCenterTrigger + 1
            )
        }
    }

    fun selectSavedLocation(location: Location) {
        _state.update {
            it.copy(
                name = location.name,
                address = location.address,
                latitude = location.latitude,
                longitude = location.longitude,
                category = location.category,
                providerPlaceId = location.providerPlaceId,
                providerName = location.providerName,
                showSavedList = false,
                isSearchingOpen = false,
                recenterZoom = STREET_ZOOM,
                mapCenterTrigger = it.mapCenterTrigger + 1
            )
        }
    }

    fun updateName(name: String) = _state.update { it.copy(name = name) }

    fun updateAddress(address: String) = _state.update { it.copy(address = address) }

    /**
     * Called as the user drags the map. Rounded to four decimals — about 11 m,
     * finer than anyone can aim a pin and coarse enough that dragging does not
     * emit a new distinct value on every frame.
     */
    fun updateCoordinates(lat: Double, lng: Double) {
        _state.update { it.copy(latitude = round4(lat), longitude = round4(lng)) }
    }

    fun updateCategory(category: String) = _state.update { it.copy(category = category) }

    fun toggleShowSavedList(show: Boolean) = _state.update { it.copy(showSavedList = show) }

    fun toggleSearchOpen(open: Boolean) = _state.update { it.copy(isSearchingOpen = open) }

    /**
     * Saves the pin and hands back its new row id, so the caller can attach it to
     * an event immediately rather than searching for it again.
     */
    fun confirmAndSaveLocation(onLocationConfirmed: (Long, String) -> Unit) {
        val s = _state.value
        if (s.isSaving) return

        // A dropped pin with no name is still a place the user meant. Naming it by
        // its coordinates beats refusing to save it — written through GeoUtils, because
        // a raw `Double` names the place "Point (24.576700000000002, 73.6832999999999)"
        // and that string is then the row's name forever.
        val locName = s.name.trim()
            .ifBlank { "Point ${GeoUtils.formatCoordinates(s.latitude, s.longitude)}" }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val now = timeProvider.now()
            val id = locationRepository.insertLocation(
                Location(
                    name = locName,
                    address = s.address.trim(),
                    latitude = s.latitude,
                    longitude = s.longitude,
                    category = s.category.trim(),
                    providerPlaceId = s.providerPlaceId,
                    providerName = s.providerName,
                    createdAt = now,
                    updatedAt = now
                )
            )
            _state.update { it.copy(isSaving = false) }
            onLocationConfirmed(id, locName)
        }
    }

    fun deleteLocation(id: Long) {
        viewModelScope.launch { locationRepository.deleteLocation(id) }
    }

    private fun round4(value: Double): Double = Math.round(value * 10_000.0) / 10_000.0

    private companion object {
        /** Long enough to skip most intermediate keystrokes, short enough to feel live. */
        const val DEBOUNCE_MS = 400L
    }
}
