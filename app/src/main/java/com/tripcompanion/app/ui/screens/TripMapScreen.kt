package com.tripcompanion.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.engine.TripStateEngine
import com.tripcompanion.app.domain.model.DayRouteLeg
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.feature.map.MapPin
import com.tripcompanion.app.feature.map.TripMapUiState
import com.tripcompanion.app.feature.map.TripMapViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppFilterChip
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.TripVectorMap
import com.tripcompanion.app.ui.components.VectorMarker
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.MapBasemap
import com.tripcompanion.app.ui.components.MapControlButton
import com.tripcompanion.app.ui.components.MapMarker
import com.tripcompanion.app.ui.components.MarkerState
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.StatusBadge
import com.tripcompanion.app.ui.components.TripMap
import com.tripcompanion.app.ui.components.colorIn
import com.tripcompanion.app.ui.components.icon
import com.tripcompanion.app.ui.theme.AppThemeExtended
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Day Route Map Screen.
 *
 * Visual itinerary overview: renders the entire day's sequence of stops and calculated route legs
 * on a warm, custom-styled illustrated travel map, accompanied by a synchronized chronological timeline sheet.
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
                message = "Plan a trip, add a few places to its days, and they appear here with the journey between them.",
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

    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded
    val listState = rememberLazyListState()

    // Handle Back Press: collapse sheet first if expanded, otherwise navigate back
    BackHandler(enabled = sheetExpanded) {
        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
    }

    var recenterTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(trip.id, state.selectedDay) { recenterTick++ }

    var animateTick by remember { mutableIntStateOf(0) }
    var animateLat by remember { mutableStateOf<Double?>(null) }
    var animateLon by remember { mutableStateOf<Double?>(null) }
    val animateTo: (Double, Double) -> Unit = { lat, lon ->
        animateLat = lat
        animateLon = lon
        animateTick++
    }

    val centreLatitude = state.focusLatitude
    val centreLongitude = state.focusLongitude
    val zoom = if (state.pins.size <= 1) 15.0 else 12.5

    val engine = remember { TripStateEngine() }
    val pinStatuses = remember(state.pins, state.now) {
        state.pins.associate { it.event.id to engine.computeEventStatus(it.event, state.now) }
    }

    val markers = remember(state.pins, pinStatuses, state.focusEventId, state.selectedPinId) {
        state.pins.map { pin ->
            val status = pinStatuses[pin.event.id] ?: EventStatus.UPCOMING
            val isFocused = pin.event.id == (state.selectedPinId ?: state.focusEventId)
            val placeName = pin.place.name.ifBlank { pin.event.title }
            MapMarker(
                id = pin.event.id,
                latitude = pin.place.latitude,
                longitude = pin.place.longitude,
                label = placeName,
                number = pin.orderInDay,
                color = categoryPinColor(pin.event.type, placeName),
                eventType = pin.event.type,
                state = markerStateOf(status, isFocused)
            )
        }
    }

    val focusStop: (MapPin) -> Unit = { pin ->
        viewModel.selectPin(pin.event.id)
        animateTo(pin.place.latitude, pin.place.longitude)
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = SHEET_PEEK,
        sheetContainerColor = Color.Transparent,
        sheetShadowElevation = 0.dp,
        sheetDragHandle = null,
        sheetContent = {
            ItinerarySheet(
                state = state,
                statuses = pinStatuses,
                expanded = sheetExpanded,
                listState = listState,
                onToggle = {
                    scope.launch {
                        val sheet = scaffoldState.bottomSheetState
                        if (sheet.currentValue == SheetValue.Expanded) {
                            sheet.partialExpand()
                        } else {
                            sheet.expand()
                        }
                    }
                },
                onFocusStop = focusStop,
                onOpenStop = { pin -> onNavigateToEvent(pin.event.id) }
            )
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            TripMap(
                latitude = centreLatitude,
                longitude = centreLongitude,
                recenterTrigger = recenterTick,
                initialZoom = zoom,
                focusZoom = 15.5,
                showCrosshair = false,
                markers = markers,
                legs = state.legs,
                onMarkerClick = { marker ->
                    viewModel.selectPin(marker.id)
                    animateTo(marker.latitude, marker.longitude)
                    // Scroll to stop in sheet
                    val index = state.pins.indexOfFirst { it.event.id == marker.id }
                    if (index >= 0) {
                        scope.launch {
                            listState.animateScrollToItem(index)
                        }
                    }
                },
                routeColor = Color(0xFF26C6DA),
                basemap = MapBasemap.Standard,
                bottomInset = SHEET_PEEK,
                animateToTrigger = animateTick,
                animateToLatitude = animateLat,
                animateToLongitude = animateLon,
                modifier = Modifier.fillMaxSize()
            )

            val refreshTransition = rememberInfiniteTransition(label = "refreshSpin")
            val refreshRotation by refreshTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "refreshRotation"
            )

            // ── Top Navigation & Filter Header ──
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = metrics.screenPadding, end = metrics.screenPadding, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapControlButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        label = "Back",
                        onClick = onNavigateBack
                    )

                    DayTripPill(
                        label = dayTripLabel(state, trip),
                        trips = state.availableTrips,
                        onSelectTrip = viewModel::selectTrip,
                        modifier = Modifier.weight(1f)
                    )

                    MapControlButton(
                        icon = Icons.Default.Refresh,
                        label = "Refresh Locations",
                        onClick = { viewModel.refreshLocations() },
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (state.isRefreshingLocations) refreshRotation else 0f
                        }
                    )
                }

                if (state.days.size > 1) {
                    MapDaySwitcher(
                        days = state.days,
                        selectedDay = state.selectedDay,
                        onSelectDay = viewModel::selectDay,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── Right-Side Floating Map Action Column (Recenter FAB) ──
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = metrics.screenPadding, bottom = SHEET_PEEK + 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MapControlButton(
                    icon = Icons.Default.CenterFocusStrong,
                    label = "Recenter Day",
                    onClick = { recenterTick++ }
                )
            }
        }
    }
}

/**
 * Top pill with day number and trip title.
 */
@Composable
private fun DayTripPill(
    label: String,
    trips: List<Trip>,
    onSelectTrip: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val canSwitch = trips.size > 1
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shadowElevation = 3.dp,
            onClick = { if (canSwitch) open = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = Color(0xFF2F5D50),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (canSwitch) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            trips.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(candidate.name) },
                    onClick = {
                        onSelectTrip(candidate.id)
                        open = false
                    }
                )
            }
        }
    }
}

/**
 * Day filter chips.
 */
@Composable
private fun MapDaySwitcher(
    days: List<LocalDate>,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppFilterChip(
            label = "All days",
            selected = selectedDay == null,
            onClick = { onSelectDay(null) }
        )
        days.forEachIndexed { index, day ->
            AppFilterChip(
                label = "Day ${index + 1}",
                selected = selectedDay == day,
                onClick = { onSelectDay(day) }
            )
        }
    }
}

/**
 * Connected Itinerary Bottom Sheet.
 */
@Composable
private fun ItinerarySheet(
    state: TripMapUiState,
    statuses: Map<Long, EventStatus>,
    expanded: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onToggle: () -> Unit,
    onFocusStop: (MapPin) -> Unit,
    onOpenStop: (MapPin) -> Unit
) {
    val metrics = AppThemeExtended.metrics

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = metrics.cardElevationRaised,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            // Drag handle
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(38.dp)
                        .height(4.5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            SheetHeader(state = state, expanded = expanded, onToggle = onToggle)

            val focus = state.pins.firstOrNull { it.event.id == (state.selectedPinId ?: state.focusEventId) }
                ?: state.pins.firstOrNull()

            if (focus != null) {
                NextStopCard(
                    pin = focus,
                    status = statuses[focus.event.id] ?: EventStatus.UPCOMING,
                    onDetails = { onOpenStop(focus) }
                )
            }

            TripProgressRow(state = state, statuses = statuses)
            SheetBody(
                state = state,
                statuses = statuses,
                listState = listState,
                onFocusStop = onFocusStop
            )
        }
    }
}

@Composable
private fun SheetHeader(
    state: TripMapUiState,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val metrics = AppThemeExtended.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = metrics.screenPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            tint = Color(0xFF2F5D50),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = dayTitle(state),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${state.pins.size} ${if (state.pins.size == 1) "stop" else "stops"} in sequence",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * NEXT stop card in peek view.
 */
@Composable
private fun NextStopCard(
    pin: MapPin,
    status: EventStatus,
    onDetails: () -> Unit
) {
    val metrics = AppThemeExtended.metrics
    val palette = AppThemeExtended.colors
    val isDark = palette.isDark
    val overline = if (status == EventStatus.ACTIVE) "HAPPENING NOW" else "NEXT STOP"
    val durationText = formatDuration(pin.event.startTime, pin.event.endTime)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isDark) Color(0xFF1B2C27) else Color(0xFFE9F3F0),
        border = BorderStroke(1.5.dp, if (isDark) Color(0xFF2F6656) else Color(0xFF388E77)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.screenPadding, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = overline,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isDark) Color(0xFF81C784) else Color(0xFF2F5D50)
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = pin.event.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${DateTimeUtils.formatTime(pin.event.startTime)}${if (durationText.isNotBlank()) " · $durationText" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                onClick = onDetails,
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 1.dp
            ) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Progress indicator row with dots track: ●━━●━━○━━○━━○━━○━━○
 */
@Composable
private fun TripProgressRow(
    state: TripMapUiState,
    statuses: Map<Long, EventStatus>
) {
    val metrics = AppThemeExtended.metrics
    val total = state.pins.size
    val done = state.pins.count { statuses[it.event.id] == EventStatus.COMPLETED }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.screenPadding, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$done of $total places completed",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Progress dots track: ●━━●━━○━━○━━○
            ProgressDotsTrack(total = total, completed = done)
        }
    }
}

@Composable
private fun ProgressDotsTrack(total: Int, completed: Int) {
    if (total <= 1) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until total) {
            val isDone = i < completed
            val isCurrent = i == completed
            Box(
                Modifier
                    .size(if (isCurrent) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isDone -> Color(0xFF7E948E)
                            isCurrent -> Color(0xFF2F5D50)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
            if (i < total - 1) {
                Box(
                    Modifier
                        .width(10.dp)
                        .height(2.dp)
                        .background(
                            if (i < completed) Color(0xFF7E948E) else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
    }
}

/**
 * Connected vertical timeline in expanded sheet.
 */
@Composable
private fun SheetBody(
    state: TripMapUiState,
    statuses: Map<Long, EventStatus>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onFocusStop: (MapPin) -> Unit
) {
    val metrics = AppThemeExtended.metrics

    LazyColumn(
        state = listState,
        modifier = Modifier.heightIn(max = 380.dp),
        contentPadding = PaddingValues(horizontal = metrics.screenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(state.pins, key = { _, pin -> pin.event.id }) { index, pin ->
            Column {
                if (index > 0) {
                    state.legs.getOrNull(index - 1)?.let { leg ->
                        TimelineConnector(leg)
                    }
                }
                TimelineStopCard(
                    pin = pin,
                    status = statuses[pin.event.id] ?: EventStatus.UPCOMING,
                    isSelected = pin.event.id == (state.selectedPinId ?: state.focusEventId),
                    onClick = { onFocusStop(pin) }
                )
            }
        }
    }
}

/**
 * Interconnected route leg connector: ↓ 🚕 15 min · 4.8 km
 */
@Composable
private fun TimelineConnector(leg: DayRouteLeg) {
    val metricsText = buildString {
        if (leg.durationSeconds != null) {
            val mins = (leg.durationSeconds / 60.0).roundToInt().coerceAtLeast(1)
            append("$mins min · ")
        }
        append(formatDistance(leg.distanceMeters))
    }

    Row(
        modifier = Modifier.padding(start = 28.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "↓",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = Color(0xFF2F5D50)
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = leg.mode.icon,
            contentDescription = leg.mode.label,
            tint = Color(0xFF2F5D50),
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${leg.mode.label} · $metricsText",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Numbered Stop Card in timeline.
 */
@Composable
private fun TimelineStopCard(
    pin: MapPin,
    status: EventStatus,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val metrics = AppThemeExtended.metrics
    val palette = AppThemeExtended.colors
    val isCompleted = status == EventStatus.COMPLETED

    val badgeColor = when {
        isSelected -> Color(0xFF2F5D50)
        isCompleted -> Color(0xFF7E948E)
        else -> pin.event.type.colorIn(palette)
    }

    AppCard(
        onClick = onClick,
        elevation = 0.dp,
        borderColor = if (isSelected) Color(0xFF2F5D50) else MaterialTheme.colorScheme.outline,
        borderWidth = if (isSelected) metrics.borderWidthStrong else metrics.borderWidth,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
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
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        pin.event.type.icon,
                        contentDescription = null,
                        tint = pin.event.type.colorIn(palette),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "${DateTimeUtils.formatTime(pin.event.startTime)} · ${pin.place.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(status = status)
        }
    }
}

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
            .statusBarsPadding()
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

private fun dayTitle(state: TripMapUiState): String {
    val trip = state.trip ?: return "Day Route Map"
    val day = state.selectedDay
    return if (day != null) {
        val index = state.days.indexOf(day)
        val num = if (index >= 0) index + 1 else 1
        "DAY $num · ${trip.name.uppercase()}"
    } else {
        trip.name.uppercase()
    }
}

private fun dayTripLabel(state: TripMapUiState, trip: Trip): String {
    val day = state.selectedDay ?: return "All days · ${trip.name}"
    val index = state.days.indexOf(day)
    val number = if (index >= 0) index + 1 else 1
    return "Day $number · ${trip.name}"
}

private fun formatDuration(start: java.time.LocalDateTime, end: java.time.LocalDateTime): String {
    val dur = Duration.between(start, end)
    val hours = dur.toHours()
    val minutes = dur.toMinutes() % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> ""
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters < 950) {
        "${meters.roundToInt()} m"
    } else {
        val tenths = (meters / 100).roundToInt()
        "${tenths / 10}.${tenths % 10} km"
    }
}

private fun markerStateOf(status: EventStatus, isFocus: Boolean): MarkerState = when (status) {
    EventStatus.COMPLETED, EventStatus.SKIPPED, EventStatus.MISSED -> MarkerState.Completed
    EventStatus.ACTIVE -> MarkerState.Current
    else -> if (isFocus) MarkerState.Current else MarkerState.Upcoming
}

private fun emptyMapMessage(unmappedCount: Int): String = when (unmappedCount) {
    0 -> "This trip has no activities yet. Add one with a place and it lands on the map."
    1 -> "The one activity on this trip has no place attached. Open it and pick a place."
    else -> "None of the $unmappedCount activities on this trip has a place attached."
}

private fun categoryPinColor(eventType: EventType, title: String): Color {
    val lower = title.lowercase()
    return when {
        lower.contains("palace") || lower.contains("fort") || lower.contains("castle") || lower.contains("monument") ->
            Color(0xFF10B981) // Emerald Green
        lower.contains("mandir") || lower.contains("temple") || lower.contains("haveli") || lower.contains("museum") ->
            Color(0xFFF59E0B) // Amber Orange
        lower.contains("lake") || lower.contains("photo") || lower.contains("point") || lower.contains("view") || lower.contains("garden") || lower.contains("bagh") ->
            Color(0xFF3B82F6) // Azure Blue
        lower.contains("sajjangarh") || lower.contains("hotel") || lower.contains("resort") || lower.contains("stay") || eventType == EventType.STAY ->
            Color(0xFF8B5CF6) // Royal Purple
        lower.contains("food") || lower.contains("restaurant") || lower.contains("cafe") || lower.contains("lunch") || lower.contains("dinner") || eventType == EventType.FOOD ->
            Color(0xFFF97316) // Warm Red-Orange
        lower.contains("station") || lower.contains("train") || lower.contains("rail") || eventType == EventType.JOURNEY ->
            Color(0xFF0284C7) // Sky Blue
        eventType == EventType.VISIT ->
            Color(0xFF10B981)
        else ->
            Color(0xFF0D9488) // Teal
    }
}

private val SHEET_PEEK = 210.dp



