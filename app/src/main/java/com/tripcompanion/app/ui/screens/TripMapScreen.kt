package com.tripcompanion.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.feature.map.MapPin
import com.tripcompanion.app.feature.map.TripMapUiState
import com.tripcompanion.app.feature.map.TripMapViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppFilterChip
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.FilterChipRow
import com.tripcompanion.app.ui.components.MapMarker
import com.tripcompanion.app.ui.components.StatusBadge
import com.tripcompanion.app.ui.components.TextActionButton
import com.tripcompanion.app.ui.components.TripMap
import com.tripcompanion.app.ui.components.colorIn
import com.tripcompanion.app.ui.components.icon
import com.tripcompanion.app.ui.theme.AppThemeExtended
import java.time.LocalDate

/**
 * The trip as geography.
 *
 * The list screens answer "when"; this one answers "how far apart". The pins carry their
 * visiting number rather than a plain dot, because the question a day's map is asked is which
 * of these do I reach first — and the sheet under it is the same day in words, so tapping a pin
 * and reading the plan are the same gesture.
 *
 * There is no crosshair here. A crosshair means "you are choosing a point", which is what the
 * location picker does; this map reports places already chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEvent: (Long) -> Unit,
    onNavigateToItinerary: (Long) -> Unit,
    viewModel: TripMapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    val palette = AppThemeExtended.colors

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val trip = state.trip
    if (trip == null) {
        MapShell(onNavigateBack = onNavigateBack, title = "Map") {
            EmptyState(
                icon = Icons.Default.Map,
                title = "No trip to map",
                message = "Plan a trip, add a few places to its days, and they appear here with " +
                    "the walk between them.",
                actionLabel = "Go back",
                onAction = onNavigateBack
            )
        }
        return
    }

    if (!state.hasPins) {
        MapShell(onNavigateBack = onNavigateBack, title = trip.name) {
            EmptyState(
                icon = Icons.Default.EditLocationAlt,
                title = "Nothing on the map yet",
                message = emptyMapMessage(state.unmappedCount),
                actionLabel = "Open the itinerary",
                onAction = { onNavigateToItinerary(trip.id) }
            )
        }
        return
    }

    // The camera is set once when the map is created, so every later move — including picking a
    // day — has to be asked for. The tick is that request.
    var recenterTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.selectedDay) { recenterTick++ }

    val focus = state.pins.firstOrNull()?.place
    val centreLatitude = focus?.latitude ?: state.focusLatitude
    val centreLongitude = focus?.longitude ?: state.focusLongitude
    // A single pin can be looked at closely; a whole day has to fit on the screen.
    val zoom = if (state.pins.size <= 1) 15.0 else 12.5

    val markers = remember(state.pins, state.now, palette) {
        state.pins.map { pin ->
            MapMarker(
                id = pin.event.id,
                latitude = pin.place.latitude,
                longitude = pin.place.longitude,
                label = pin.event.title,
                number = pin.orderInDay,
                color = pin.event.type.colorIn(palette),
                isCurrent = isHappeningNow(pin, state)
            )
        }
    }

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(),
        sheetPeekHeight = 168.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        // Only the top corners: the bottom edge is the screen's edge, and rounding it would
        // show a sliver of map under the sheet.
        sheetShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        sheetContent = {
            PinSheet(
                state = state,
                onSelectDay = viewModel::selectDay,
                onPinClick = { pin -> onNavigateToEvent(pin.event.id) }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            TripMap(
                latitude = centreLatitude,
                longitude = centreLongitude,
                recenterTrigger = recenterTick,
                initialZoom = zoom,
                focusZoom = zoom,
                showCrosshair = false,
                markers = markers,
                onMarkerClick = { marker -> onNavigateToEvent(marker.id) },
                // The controls sit clear of the day chips at the top of the map.
                controlsPadding = PaddingValues(top = 84.dp, end = 12.dp),
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = metrics.screenPadding),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onNavigateBack,
                        tint = MaterialTheme.colorScheme.onSurface,
                        background = MaterialTheme.colorScheme.surface,
                        borderColor = null,
                        size = 44.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        shape = metrics.chipShape,
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = metrics.cardElevation
                    ) {
                        Text(
                            text = trip.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                // One chip per day that actually has a pin, plus the whole trip. A day with
                // nothing mapped is not offered, because tapping it would clear the map.
                if (state.days.size > 1) {
                    FilterChipRow(
                        contentPadding = PaddingValues(horizontal = metrics.screenPadding)
                    ) {
                        AppFilterChip(
                            label = "All days",
                            selected = state.selectedDay == null,
                            onClick = { viewModel.selectDay(null) }
                        )
                        state.days.forEachIndexed { index, day ->
                            AppFilterChip(
                                label = "Day ${index + 1}",
                                selected = state.selectedDay == day,
                                onClick = { viewModel.selectDay(day) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The plan for whatever the map is showing.
 *
 * Numbered to match the pins: the number on the card and the number on the map are the same
 * claim about the same stop, which is the only reason a numbered list belongs here.
 */
@Composable
private fun PinSheet(
    state: TripMapUiState,
    onSelectDay: (LocalDate?) -> Unit,
    onPinClick: (MapPin) -> Unit
) {
    val metrics = AppThemeExtended.metrics
    val palette = AppThemeExtended.colors

    Column(Modifier.padding(bottom = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.screenPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = sheetTitle(state),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = sheetSubtitle(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.selectedDay != null) {
                TextActionButton(text = "Whole trip", onClick = { onSelectDay(null) })
            }
        }

        Spacer(Modifier.height(12.dp))

        // Bounded so the sheet can be dragged rather than becoming the screen; the list scrolls
        // inside it once a day has more stops than fit.
        LazyColumn(
            modifier = Modifier.heightIn(max = 360.dp),
            contentPadding = PaddingValues(horizontal = metrics.screenPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.pins, key = { it.event.id }) { pin ->
                PinRow(
                    pin = pin,
                    color = pin.event.type.colorIn(palette),
                    isCurrent = isHappeningNow(pin, state),
                    onClick = { onPinClick(pin) }
                )
            }

            if (state.unmappedCount > 0) {
                item("unmapped") {
                    Text(
                        text = unmappedNote(state.unmappedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textFaint,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

/** One stop: its number, what it is, when it is, and where it stands. */
@Composable
private fun PinRow(
    pin: MapPin,
    color: Color,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val metrics = AppThemeExtended.metrics
    AppCard(
        onClick = onClick,
        borderColor = if (isCurrent) color else MaterialTheme.colorScheme.outline,
        borderWidth = if (isCurrent) metrics.borderWidthStrong else metrics.borderWidth,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = pin.orderInDay.toString(),
                    style = AppThemeExtended.text.badge,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = pin.event.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        pin.event.type.icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = DateTimeUtils.formatTime(pin.event.startTime) + " · " +
                            pin.place.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(status = pin.event.status)
        }
    }
}

/**
 * The header and a centred slot, for the states where there is no map to draw.
 *
 * An empty map is worse than no map: a grey rectangle of another country says nothing, so these
 * states get the reason and a way out instead of tiles.
 */
@Composable
private fun MapShell(
    onNavigateBack: () -> Unit,
    title: String,
    content: @Composable () -> Unit
) {
    val metrics = AppThemeExtended.metrics
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = metrics.screenPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onNavigateBack,
                size = 44.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

private fun sheetTitle(state: TripMapUiState): String {
    val day = state.selectedDay ?: return "Whole trip"
    val index = state.days.indexOf(day)
    val number = if (index >= 0) index + 1 else 1
    return "Day $number · ${DateTimeUtils.formatDayAndDate(day)}"
}

private fun sheetSubtitle(state: TripMapUiState): String {
    val shown = state.pins.size
    val places = if (shown == 1) "1 place" else "$shown places"
    return if (state.selectedDay == null || shown == state.totalPinCount) {
        places
    } else {
        "$places of ${state.totalPinCount} on the trip"
    }
}

/**
 * Why the map is empty, in the terms of whichever cause it is.
 *
 * "Add a place" and "your places have no coordinates" are different problems with different
 * fixes, and one message covering both would help with neither.
 */
private fun emptyMapMessage(unmappedCount: Int): String = when (unmappedCount) {
    0 -> "This trip has no activities yet. Add one with a place and it lands on the map."
    1 -> "The one activity on this trip has no place attached, so there is nothing to draw. " +
        "Open it and pick a place."
    else -> "None of the $unmappedCount activities on this trip has a place attached, so there " +
        "is nothing to draw. Open one and pick a place."
}

/** Said plainly, because a map that shows six of nine stops should admit it. */
private fun unmappedNote(unmappedCount: Int): String = if (unmappedCount == 1) {
    "1 more activity has no place attached, so it isn't on the map."
} else {
    "$unmappedCount more activities have no place attached, so they aren't on the map."
}

/** Between its start and its end, by the same clock the rest of the app reads. */
private fun isHappeningNow(pin: MapPin, state: TripMapUiState): Boolean =
    !state.now.isBefore(pin.event.startTime) && state.now.isBefore(pin.event.endTime)
