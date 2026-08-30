package com.tripcompanion.app.feature.place

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The three lists a traveller keeps: what's left, what's done, what's bookmarked. */
enum class PlaceTab(val label: String) {
    TO_VISIT("To visit"),
    VISITED("Visited"),
    SAVED("Saved")
}

/** The nav argument that opens this screen on a chosen tab. Named once, read in two places. */
const val ARG_TAB = "tab"

data class PlacesUiState(
    val tab: PlaceTab = PlaceTab.TO_VISIT,
    val query: String = "",
    /** The selected tab, already searched. */
    val places: List<Location> = emptyList(),
    val toVisitCount: Int = 0,
    val visitedCount: Int = 0,
    val savedCount: Int = 0,
    val isLoading: Boolean = true
) {
    /** Whether the tab is empty because nothing matched, or because nothing is there at all. */
    val isSearching: Boolean get() = query.isNotBlank()
    val totalCount: Int get() = toVisitCount + visitedCount
    fun countFor(tab: PlaceTab): Int = when (tab) {
        PlaceTab.TO_VISIT -> toVisitCount
        PlaceTab.VISITED -> visitedCount
        PlaceTab.SAVED -> savedCount
    }
}

/**
 * The places list, backed entirely by what is stored.
 *
 * The screen this replaces held eight hardcoded `Location`-shaped literals and a bookmark
 * icon that did nothing. The tabs here are three indexed queries and the bookmark writes a
 * column, so a place saved on this screen is still saved after a reboot.
 */
@HiltViewModel
class PlacesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val locationRepository: LocationRepository
) : ViewModel() {

    /**
     * The tab the caller asked for, if it asked.
     *
     * The hub links straight to Saved, so arriving on "To visit" and making the user tap again
     * would be the screen ignoring the row they pressed. An unrecognised value falls back
     * rather than crashing, because a bad deep link should still show places.
     */
    private val tab = MutableStateFlow(
        savedStateHandle.get<String>(ARG_TAB)
            ?.let { requested -> PlaceTab.entries.firstOrNull { it.name == requested } }
            ?: PlaceTab.TO_VISIT
    )
    private val query = MutableStateFlow("")

    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                locationRepository.getPlacesToVisit(),
                locationRepository.getVisitedPlaces(),
                locationRepository.getSavedPlaces(),
                tab,
                query
            ) { toVisit, visited, saved, selectedTab, text ->
                val source = when (selectedTab) {
                    PlaceTab.TO_VISIT -> toVisit
                    PlaceTab.VISITED -> visited
                    PlaceTab.SAVED -> saved
                }
                PlacesUiState(
                    tab = selectedTab,
                    query = text,
                    places = source.filter { it.matches(text) },
                    toVisitCount = toVisit.size,
                    visitedCount = visited.size,
                    savedCount = saved.size,
                    isLoading = false
                )
            }.collect { next -> _state.update { next } }
        }
    }

    fun setTab(next: PlaceTab) {
        tab.value = next
    }

    fun setQuery(next: String) {
        query.value = next
    }

    /**
     * Both toggles write one column rather than the whole row.
     *
     * A bookmark tap is faster than a round trip, and a read-modify-write would drop the
     * other flag when two taps overlapped — which is why the repository exposes them
     * separately in the first place.
     */
    fun toggleSaved(place: Location) {
        viewModelScope.launch { locationRepository.setSaved(place.id, !place.isSaved) }
    }

    fun toggleVisited(place: Location) {
        viewModelScope.launch { locationRepository.setVisited(place.id, !place.isVisited) }
    }

    /**
     * Forget a place entirely — the way out of a list that filled up with things the
     * traveller never meant to keep.
     *
     * The place is a global row, not owned by any one trip, so this is the only thing that
     * removes it: toggling `saved` or `visited` off just moves it between tabs. An event that
     * pointed here keeps its dangling `locationId` (there is no foreign key on it), so the
     * itinerary is unharmed — the activity simply loses its pin on the map.
     */
    fun delete(place: Location) {
        viewModelScope.launch { locationRepository.deleteLocation(place.id) }
    }

    /**
     * Delete multiple places at once from the selection mode.
     */
    fun deleteMultiple(placeIds: Set<Long>) {
        viewModelScope.launch {
            placeIds.forEach { id ->
                locationRepository.deleteLocation(id)
            }
        }
    }

    /**
     * Name, category or address.
     *
     * Searching the address matters more than it looks: "Old City" is how someone remembers
     * where a place was long after forgetting what it was called.
     */
    private fun Location.matches(text: String): Boolean {
        if (text.isBlank()) return true
        val needle = text.trim()
        return name.contains(needle, ignoreCase = true) ||
            category.contains(needle, ignoreCase = true) ||
            address.contains(needle, ignoreCase = true)
    }
}
