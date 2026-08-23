package com.tripcompanion.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.GeoUtils
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.service.LocationSearchError
import com.tripcompanion.app.feature.location.LocationPickerViewModel
import com.tripcompanion.app.feature.location.SearchUiState
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppDivider
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.TripMap
import com.tripcompanion.app.ui.theme.AppThemeExtended

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    onNavigateBack: () -> Unit,
    onLocationSelected: (Long, String) -> Unit,
    viewModel: LocationPickerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.showSavedList) "Saved places" else "Pick a place",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleShowSavedList(!state.showSavedList) }) {
                        Icon(
                            imageVector = if (state.showSavedList) {
                                Icons.Default.Map
                            } else {
                                Icons.AutoMirrored.Filled.List
                            },
                            contentDescription = if (state.showSavedList) {
                                "Show the map"
                            } else {
                                "Show saved places"
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.showSavedList) {
                SavedPlacesList(
                    locations = state.locations,
                    onPick = { location ->
                        viewModel.selectSavedLocation(location)
                        onLocationSelected(location.id, location.name)
                    },
                    onDelete = viewModel::deleteLocation,
                    onOpenMap = { viewModel.toggleShowSavedList(false) }
                )
            } else {
                // The map fills the screen; search floats over the top edge and the
                // confirmation panel over the bottom. Nothing is layered over the
                // crosshair, which is why it gets bottom padding of its own.
                TripMap(
                    latitude = state.latitude,
                    longitude = state.longitude,
                    recenterTrigger = state.mapCenterTrigger,
                    focusZoom = state.recenterZoom,
                    onCenterChanged = viewModel::updateCoordinates,
                    onViewportChanged = viewModel::updateViewport,
                    crosshairBottomPadding = 230.dp,
                    controlsPadding = PaddingValues(
                        top = 96.dp,
                        end = metrics.screenPadding,
                        bottom = metrics.screenPadding,
                        start = metrics.screenPadding
                    ),
                    modifier = Modifier.fillMaxSize()
                )

                SearchOverlay(
                    query = state.searchQuery,
                    searchState = state.searchState,
                    originLatitude = state.latitude,
                    originLongitude = state.longitude,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onClear = viewModel::clearSearch,
                    onRetry = viewModel::retrySearch,
                    onPickResult = viewModel::selectSearchResult,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = metrics.screenPadding, vertical = 8.dp)
                )

                PinPanel(
                    name = state.name,
                    address = state.address,
                    latitude = state.latitude,
                    longitude = state.longitude,
                    isSaving = state.isSaving,
                    canConfirm = state.canConfirm,
                    onNameChange = viewModel::updateName,
                    onAddressChange = viewModel::updateAddress,
                    onConfirm = {
                        viewModel.confirmAndSaveLocation { id, name ->
                            onLocationSelected(id, name)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(metrics.screenPadding)
                )
            }
        }
    }
}

// ── Saved places (§13: works with no network at all) ──

@Composable
private fun SavedPlacesList(
    locations: List<Location>,
    onPick: (Location) -> Unit,
    onDelete: (Long) -> Unit,
    onOpenMap: () -> Unit
) {
    val metrics = AppThemeExtended.metrics

    if (locations.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(metrics.screenPadding),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                icon = Icons.Default.Place,
                title = "Nothing saved yet",
                message = "Places you confirm on the map are kept here, ready to reuse " +
                    "offline the next time you plan a day.",
                actionLabel = "Open the map",
                onAction = onOpenMap
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.screenPadding,
            end = metrics.screenPadding,
            top = 4.dp,
            bottom = metrics.screenPadding
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        items(locations, key = { it.id }) { location ->
            AppCard(onClick = { onPick(location) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = AppThemeExtended.colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = location.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (location.address.isNotBlank()) {
                            Text(
                                text = location.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = GeoUtils.formatCoordinates(location.latitude, location.longitude),
                            style = AppThemeExtended.text.time.copy(
                                fontSize = MaterialTheme.typography.labelSmall.fontSize
                            ),
                            color = AppThemeExtended.colors.textFaint
                        )
                    }
                    IconButton(onClick = { onDelete(location.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete ${location.name}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Search (§11) ──

@Composable
private fun SearchOverlay(
    query: String,
    searchState: SearchUiState,
    originLatitude: Double,
    originLongitude: Double,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onPickResult: (SearchResultLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val metrics = AppThemeExtended.metrics

    Column(modifier = modifier.fillMaxWidth()) {
        AppCard(
            color = MaterialTheme.colorScheme.surface,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = metrics.controlHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(AppThemeExtended.colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboard?.hide()
                                onRetry()
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (query.isEmpty()) {
                        Text(
                            text = "Search for a place",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppThemeExtended.colors.textFaint
                        )
                    }
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear the search",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = searchState !is SearchUiState.Idle) {
            AppCard(
                color = MaterialTheme.colorScheme.surface,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .padding(top = 6.dp)
                    .heightIn(max = 288.dp)
            ) {
                when (searchState) {
                    SearchUiState.Idle -> Unit

                    SearchUiState.Searching -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(metrics.cardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = AppThemeExtended.colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Searching…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is SearchUiState.Success -> LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(searchState.results) { result ->
                            SearchResultRow(
                                result = result,
                                distanceKm = GeoUtils.distanceKm(
                                    originLatitude,
                                    originLongitude,
                                    result.latitude,
                                    result.longitude
                                ),
                                onClick = {
                                    keyboard?.hide()
                                    onPickResult(result)
                                }
                            )
                            AppDivider()
                        }
                    }

                    is SearchUiState.Empty -> Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(metrics.cardPadding)
                    ) {
                        Text(
                            text = "No matches",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Nothing came back for “${searchState.query}”. " +
                                "Try a shorter name, or drag the map to place the pin yourself.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is SearchUiState.Failed -> SearchFailure(
                        error = searchState.error,
                        onRetry = onRetry
                    )
                }
            }
        }
    }
}

/**
 * A failed search, said plainly (§11 lists the failures that must be handled).
 *
 * Each cause gets its own wording because they call for different reactions —
 * waiting, retrying, or giving up on search and dropping the pin by hand — and a
 * single "search failed" hides which one applies. Every case still points at
 * manual placement, because the screen is never actually blocked: the map works
 * offline.
 */
@Composable
private fun SearchFailure(
    error: LocationSearchError,
    onRetry: () -> Unit
) {
    val metrics = AppThemeExtended.metrics

    val headline = when (error) {
        LocationSearchError.NETWORK_UNAVAILABLE -> "No connection"
        LocationSearchError.TIMEOUT -> "Search timed out"
        LocationSearchError.RATE_LIMITED -> "Too many searches"
        LocationSearchError.PROVIDER_ERROR -> "Place search is down"
        LocationSearchError.MALFORMED_RESPONSE -> "Unreadable answer"
        LocationSearchError.UNKNOWN -> "Search failed"
    }
    val detail = when (error) {
        LocationSearchError.NETWORK_UNAVAILABLE ->
            "Place search needs the internet. Drag the map to place the pin instead."
        LocationSearchError.TIMEOUT ->
            "The place service didn't answer in time."
        LocationSearchError.RATE_LIMITED ->
            "The place service is limiting requests. Wait a moment before trying again."
        LocationSearchError.PROVIDER_ERROR ->
            "The place service returned an error. This one isn't yours to fix."
        LocationSearchError.MALFORMED_RESPONSE ->
            "The place service sent something this app couldn't read."
        LocationSearchError.UNKNOWN ->
            "Something went wrong on the way to the place service."
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(metrics.cardPadding)
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text("Try again", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * One search result.
 *
 * Three facts, in the order they answer "is this the one I meant": what it is called, where it
 * is, and how far that is from the map you are looking at. The distance is what settles the
 * common case — two temples of the same name, one of them four kilometres away and one of them
 * in another state.
 */
@Composable
private fun SearchResultRow(
    result: SearchResultLocation,
    distanceKm: Double,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = GeoUtils.formatDistance(distanceKm),
                    style = AppThemeExtended.text.time.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize
                    ),
                    color = AppThemeExtended.colors.textFaint,
                    maxLines = 1
                )
            }
            val locality = trimLeadingName(result.formattedAddress, result.name)
            if (locality.isNotBlank()) {
                Text(
                    text = locality,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (result.category.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                // The provider's own word for what this is — "museum", "restaurant" — which
                // is often the fastest way to tell two identically-named results apart.
                Surface(
                    shape = AppThemeExtended.metrics.chipShape,
                    color = AppThemeExtended.colors.accentSoft
                ) {
                    Text(
                        text = result.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = AppThemeExtended.colors.accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

// ── The pin, and confirming it (§13) ──

/**
 * The address with its first component dropped when that component is just the name again.
 *
 * Nominatim's `display_name` starts with the place itself — "City Palace, Bhattiyani Chohatta,
 * Udaipur, …" — so printing it under the title repeats the title and pushes the part that
 * actually distinguishes two results onto a third line that is never shown.
 */
private fun trimLeadingName(address: String, name: String): String {
    val trimmed = address.trim()
    if (trimmed.isBlank()) return ""
    val first = trimmed.substringBefore(",").trim()
    return if (first.equals(name.trim(), ignoreCase = true) && trimmed.contains(',')) {
        trimmed.substringAfter(",").trim()
    } else {
        trimmed
    }
}

@Composable
private fun PinPanel(
    name: String,
    address: String,
    latitude: Double,
    longitude: Double,
    isSaving: Boolean,
    canConfirm: Boolean,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = AppThemeExtended.metrics

    AppCard(
        color = MaterialTheme.colorScheme.surface,
        borderWidth = metrics.borderWidthStrong,
        modifier = modifier
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = AppThemeExtended.colors.destination,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "Pin",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Tabular figures, so the digits stop jittering while the map is dragged.
                Text(
                    text = GeoUtils.formatCoordinates(latitude, longitude),
                    style = AppThemeExtended.text.time.copy(
                        fontSize = MaterialTheme.typography.labelMedium.fontSize
                    ),
                    color = AppThemeExtended.colors.accent
                )
            }

            PinTextField(
                value = name,
                onValueChange = onNameChange,
                label = "Name",
                placeholder = "What's this place called?"
            )
            PinTextField(
                value = address,
                onValueChange = onAddressChange,
                label = "Address — optional",
                placeholder = "Street or area"
            )

            PrimaryButton(
                text = "Use this place",
                onClick = onConfirm,
                enabled = canConfirm && !isSaving,
                busy = isSaving,
                icon = Icons.Default.Check
            )
        }
    }
}

/** One field in the pin panel. Not a picker — the user types these two by hand. */
@Composable
private fun PinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    val metrics = AppThemeExtended.metrics
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        shape = metrics.controlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppThemeExtended.colors.accent,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
