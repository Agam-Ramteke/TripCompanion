package com.tripcompanion.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.feature.place.PlaceTab
import com.tripcompanion.app.feature.place.PlacesUiState
import com.tripcompanion.app.feature.place.PlacesViewModel
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.AppSearchBar
import com.tripcompanion.app.ui.components.BadgeTone
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.PlaceCard
import com.tripcompanion.app.ui.components.SegmentedControl
import com.tripcompanion.app.ui.components.TextActionButton
import com.tripcompanion.app.ui.theme.AppThemeExtended

/**
 * Every place, in the three lists a traveller actually keeps.
 *
 * The screen this replaces held eight hardcoded literals and a bookmark that did nothing.
 * Both toggles here write a column, so a place saved on this screen is still saved after a
 * reboot — and the counts on the tabs come from the same three queries that fill them.
 */
@Composable
fun PlacesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlaceDetail: (Long) -> Unit,
    onNavigateToAddPlace: () -> Unit,
    viewModel: PlacesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics

    // Held here rather than in the row so the confirm survives the row scrolling off screen,
    // and so only one place can be mid-removal at a time.
    var pendingRemove by remember { mutableStateOf<Location?>(null) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.screenPadding,
            end = metrics.screenPadding,
            top = 12.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item("header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onNavigateBack,
                    size = 44.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Places",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = subtitleFor(state),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Add a place",
                    onClick = onNavigateToAddPlace,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    background = MaterialTheme.colorScheme.primary,
                    borderColor = null,
                    size = 44.dp
                )
            }
        }

        // Nothing stored at all is a different problem from an empty tab, and it gets a
        // different answer: one route in, no search box to stare at.
        if (state.totalCount == 0 && state.savedCount == 0) {
            item("empty") {
                Spacer(Modifier.height(24.dp))
                EmptyState(
                    icon = Icons.Default.Explore,
                    title = "No places yet",
                    message = "Search for somewhere you want to go and it lands here, with its " +
                        "hours, its position on the map and anywhere near it you already saved.",
                    actionLabel = "Find a place",
                    onAction = onNavigateToAddPlace
                )
            }
            return@LazyColumn
        }

        item("search") {
            AppSearchBar(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                placeholder = "Search your places"
            )
        }

        item("tabs") {
            SegmentedControl(
                options = PlaceTab.entries.map { tabLabel(it, state) },
                selected = tabLabel(state.tab, state),
                onSelect = { label ->
                    PlaceTab.entries.firstOrNull { tabLabel(it, state) == label }
                        ?.let(viewModel::setTab)
                }
            )
        }

        if (state.places.isEmpty()) {
            item("empty-tab") {
                Spacer(Modifier.height(16.dp))
                if (state.isSearching) {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "Nothing matched",
                        message = "No place in ${state.tab.label.lowercase()} matches " +
                            "\"${state.query}\". Names, categories and addresses are all searched.",
                        actionLabel = "Clear the search",
                        onAction = { viewModel.setQuery("") }
                    )
                } else {
                    EmptyState(
                        icon = Icons.Default.Place,
                        title = emptyTabTitle(state.tab),
                        message = emptyTabMessage(state.tab),
                        actionLabel = if (state.tab == PlaceTab.TO_VISIT) "Find a place" else null,
                        onAction = if (state.tab == PlaceTab.TO_VISIT) onNavigateToAddPlace else null
                    )
                }
            }
            return@LazyColumn
        }

        items(state.places, key = { it.id }) { place ->
            PlaceRow(
                place = place,
                onOpen = { onNavigateToPlaceDetail(place.id) },
                onToggleSaved = { viewModel.toggleSaved(place) },
                onToggleVisited = { viewModel.toggleVisited(place) },
                onRemove = { pendingRemove = place }
            )
        }
    }

    pendingRemove?.let { place ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remove ${place.name}?") },
            text = {
                Text(
                    "It leaves every list — to visit, visited and saved. Anything on your " +
                        "itinerary that points here keeps its plan but loses its pin on the " +
                        "map. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(place)
                    pendingRemove = null
                }) {
                    Text("Remove", color = AppThemeExtended.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) { Text("Keep it") }
            }
        )
    }
}

/**
 * A card plus the one line that changes its list.
 *
 * "Been there" is the whole point of the Visited tab, and burying it in the detail screen
 * means a place gets ticked off days later or never.
 */
@Composable
private fun PlaceRow(
    place: Location,
    onOpen: () -> Unit,
    onToggleSaved: () -> Unit,
    onToggleVisited: () -> Unit,
    onRemove: () -> Unit
) {
    Column {
        PlaceCard(
            name = place.name,
            imageUri = place.photoUri,
            category = place.category.takeIf { it.isNotBlank() },
            rating = place.rating,
            openingHours = place.openingHours.takeIf { it.isNotBlank() },
            isSaved = place.isSaved,
            onToggleSaved = onToggleSaved,
            statusLabel = if (place.isVisited) "Visited" else null,
            statusTone = BadgeTone.POSITIVE,
            onClick = onOpen
        )
        Spacer(Modifier.height(2.dp))
        // The two things you do to a place from the list: move it between lists, or take it
        // off them for good. Remove is set apart on the right and coloured as a warning so
        // it isn't tapped by reflex on the way to "Been there".
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextActionButton(
                text = if (place.isVisited) "Not been there after all" else "Been there",
                onClick = onToggleVisited,
                icon = if (place.isVisited) Icons.AutoMirrored.Filled.Undo else Icons.Default.CheckCircleOutline
            )
            TextActionButton(
                text = "Remove",
                onClick = onRemove,
                icon = Icons.Default.DeleteOutline,
                color = AppThemeExtended.colors.dangerText
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** The one line under the title: how far through the list the traveller is. */
private fun subtitleFor(state: PlacesUiState): String = when {
    state.totalCount == 0 && state.savedCount == 0 -> "Nowhere yet"
    state.totalCount == 0 -> "${state.savedCount} saved for later"
    state.visitedCount == 0 && state.toVisitCount == 1 -> "1 place to go"
    state.visitedCount == 0 -> "${state.toVisitCount} places to go"
    state.toVisitCount == 0 -> "All ${state.visitedCount} visited"
    else -> "${state.visitedCount} of ${state.totalCount} visited"
}

private fun tabLabel(tab: PlaceTab, state: PlacesUiState): String {
    val count = state.countFor(tab)
    return if (count == 0) tab.label else "${tab.label} $count"
}

private fun emptyTabTitle(tab: PlaceTab): String = when (tab) {
    PlaceTab.TO_VISIT -> "Nothing left to visit"
    PlaceTab.VISITED -> "Nowhere visited yet"
    PlaceTab.SAVED -> "Nothing saved"
}

private fun emptyTabMessage(tab: PlaceTab): String = when (tab) {
    PlaceTab.TO_VISIT -> "Every place on your list is ticked off. Add another and it appears here."
    PlaceTab.VISITED -> "Tap \"Been there\" on a place and it moves into this list."
    PlaceTab.SAVED -> "The bookmark on a card saves it here — for the places you want to keep " +
        "whether or not they make this trip."
}
