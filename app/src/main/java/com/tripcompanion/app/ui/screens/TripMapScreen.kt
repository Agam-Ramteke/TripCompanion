package com.tripcompanion.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EditLocationAlt
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.core.util.ExternalNavigator
import com.tripcompanion.app.domain.engine.TripStateEngine
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.feature.map.MapPin
import com.tripcompanion.app.feature.map.TravelLeg
import com.tripcompanion.app.feature.map.TripMapUiState
import com.tripcompanion.app.feature.map.TripMapViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppFilterChip
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.MapBasemap
import com.tripcompanion.app.ui.components.MapControlButton
import com.tripcompanion.app.ui.components.MapMarker
import com.tripcompanion.app.ui.components.MarkerState
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.StatusBadge
import com.tripcompanion.app.ui.components.TripMap
import com.tripcompanion.app.ui.components.colorIn
import com.tripcompanion.app.ui.components.icon
import com.tripcompanion.app.ui.theme.AppThemeExtended
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The trip as geography.
 *
 * The list screens answer "when"; this one answers "how far apart", and how the day flows from one
 * place to the next. Each stop is a numbered pin — the same number its card carries in the sheet —
 * so map and list are one system, not markers scattered on someone else's map. The pins wear their
 * progress, not their type: a stop behind the traveller is muted, the one they are at or headed to
 * is emphasised, the rest sit quietly ahead. The route is drawn as a cased line through them, and
 * the device's own position, once permission is granted, is a separate dot that could never be
 * mistaken for a stop.
 *
 * The chrome floats over the tiles like a modern maps app: back top-left, a "Day N · trip" pill
 * centred (tap to switch trips) over the day strip, and layers / recenter / locate-me top-right.
 * Tapping a pin — or a stop in the sheet — flies the camera to it. The itinerary rises from the
 * bottom as a floating card: its peek headlines the next stop with Navigate and Details; dragged
 * up, it becomes the numbered day with the travel time, distance and mode between each stop.
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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetExpanded = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded

    // The camera is set once when the map is created, so every later move has to be asked for.
    // Two requests: recenter frames the whole day (day/trip change, or the recenter button); animate
    // flies to one point (a tapped stop, or center-on-me). Each is a counter the map watches.
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

    // Location is optional. The screen works fully without it; granting adds the dot and turns
    // center-on-me from "recenter on the trip" into "fly to where I am". We seed from the current
    // grant so a returning user who already allowed it starts collecting without a second prompt.
    var hasLocation by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // "Precise" or "approximate" — either is enough for a glanceable dot.
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocation = granted
    }
    LaunchedEffect(hasLocation) { if (hasLocation) viewModel.onLocationPermissionGranted() }

    // Where the map opens, and where recenter returns to: the engine's focus stop (current or next,
    // else the first pin), computed in the VM so the map agrees with Home about "next".
    val centreLatitude = state.focusLatitude
    val centreLongitude = state.focusLongitude
    // A single pin can be looked at closely; a whole day is framed by the map's own fit-to-bounds.
    val zoom = if (state.pins.size <= 1) 15.0 else 12.5

    // One status per stop, from the same engine Home uses, so the map, the sheet and the next-up
    // card tell one story. Recomputed only when the stops or the clock tick change.
    val engine = remember { TripStateEngine() }
    val pinStatuses = remember(state.pins, state.now) {
        state.pins.associate { it.event.id to engine.computeEventStatus(it.event, state.now) }
    }

    val markers = remember(state.pins, pinStatuses, state.focusEventId, palette) {
        state.pins.map { pin ->
            val status = pinStatuses[pin.event.id] ?: EventStatus.UPCOMING
            MapMarker(
                id = pin.event.id,
                latitude = pin.place.latitude,
                longitude = pin.place.longitude,
                label = pin.event.title,
                number = pin.orderInDay,
                color = pin.event.type.colorIn(palette),
                state = markerStateOf(status, pin.event.id == state.focusEventId)
            )
        }
    }

    // The line through the visible stops. The VM has already decided its shape — road-following
    // geometry when the routing service answered, straight legs otherwise — so the screen only
    // converts the pairs to the map library's GeoPoint and draws them.
    val route = remember(state.routePolyline) {
        state.routePolyline.map { (lat, lon) -> GeoPoint(lat, lon) }
    }

    var basemap by remember { mutableStateOf(MapBasemap.Standard) }

    // Tapping a stop — on the map or in the list — flies the camera to it and drops the sheet back
    // to its peek so the move is actually visible rather than hidden behind an expanded sheet.
    val focusStop: (MapPin) -> Unit = { pin ->
        animateTo(pin.place.latitude, pin.place.longitude)
        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = SHEET_PEEK,
        // The sheet itself is invisible; the floating card inside it carries the shape and shadow,
        // so it reads as a pill detached from the screen edge (the map shows in the gap beneath).
        sheetContainerColor = Color.Transparent,
        sheetShadowElevation = 0.dp,
        sheetDragHandle = null,
        sheetContent = {
            ItinerarySheet(
                state = state,
                statuses = pinStatuses,
                expanded = sheetExpanded,
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
                onOpenStop = { pin -> onNavigateToEvent(pin.event.id) },
                onNavigateStop = { pin ->
                    ExternalNavigator.navigateTo(
                        context = context,
                        latitude = pin.place.latitude,
                        longitude = pin.place.longitude,
                        label = pin.event.title
                    )
                }
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
                onMarkerClick = { marker -> animateTo(marker.latitude, marker.longitude) },
                routePoints = route,
                routeColor = palette.accent,
                basemap = basemap,
                zoomAlignment = Alignment.BottomEnd,
                // Lift the zoom and attribution clear of the floating itinerary card.
                bottomInset = SHEET_PEEK,
                animateToTrigger = animateTick,
                animateToLatitude = animateLat,
                animateToLongitude = animateLon,
                deviceLatitude = state.deviceLocation?.latitude,
                deviceLongitude = state.deviceLocation?.longitude,
                deviceAccuracyMeters = state.deviceLocation?.accuracyMeters,
                modifier = Modifier.fillMaxSize()
            )

            // Top-left: back. There is no navigation drawer, so the mockup's hamburger would be a
            // door to nowhere — a back arrow is the honest control.
            MapControlButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Back",
                onClick = onNavigateBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = metrics.screenPadding, top = 8.dp)
            )

            // Top-centre: a "Day N · <trip>" pill (tap to switch trips) over the day strip. The map
            // opens on the day being travelled and this is how you move off it, so the day control
            // lives on the map rather than buried in the sheet. The pill names the trip by choice,
            // knowing a day can sit in another city than the trip's name suggests — map, pins, route
            // and sheet all follow the selected day, so only this label is the trip's, deliberately.
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DayTripPill(
                    label = dayTripLabel(state, trip),
                    trips = state.availableTrips,
                    onSelectTrip = viewModel::selectTrip
                )
                if (state.days.size > 1) {
                    MapDaySwitcher(
                        days = state.days,
                        selectedDay = state.selectedDay,
                        onSelectDay = viewModel::selectDay
                    )
                }
            }

            // Top-right: base-map toggle, recenter-on-the-day, and locate-me. Locate-me flies to the
            // device fix when there is one, asks for permission when it has never been granted, and
            // otherwise re-frames the day — so the button always does something honest.
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = metrics.screenPadding, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MapControlButton(
                    icon = Icons.Default.Layers,
                    label = if (basemap == MapBasemap.Satellite) "Standard map" else "Satellite",
                    onClick = {
                        basemap = if (basemap == MapBasemap.Satellite) {
                            MapBasemap.Standard
                        } else {
                            MapBasemap.Satellite
                        }
                    }
                )
                MapControlButton(
                    icon = Icons.Default.CenterFocusStrong,
                    label = "Recenter on the day",
                    onClick = { recenterTick++ }
                )
                MapControlButton(
                    icon = Icons.Default.MyLocation,
                    label = "Center on my location",
                    // A live fix tints the glyph in the travel accent, so the control reads as
                    // "active" only when it can actually take you somewhere.
                    tint = if (state.deviceLocation != null) {
                        palette.accent
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    onClick = {
                        val fix = state.deviceLocation
                        when {
                            fix != null -> animateTo(fix.latitude, fix.longitude)
                            !hasLocation -> permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                            else -> recenterTick++
                        }
                    }
                )
            }
        }
    }
}

/**
 * The "Day N · <trip name>" pill: the shown day, and a tap-to-switch onto another trip.
 *
 * The leading glyph is a neutral map mark, not the mockup's little sun: there is no weather provider
 * behind this screen, and a forecast icon that forecasts nothing is a lie. The chevron and menu
 * appear only when there is another trip to switch to.
 */
@Composable
private fun DayTripPill(
    label: String,
    trips: List<Trip>,
    onSelectTrip: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = AppThemeExtended.metrics
    val canSwitch = trips.size > 1
    var open by remember { mutableStateOf(false) }

    Box(modifier) {
        Surface(
            shape = metrics.chipShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = metrics.cardElevation,
            onClick = { if (canSwitch) open = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 220.dp)
                )
                if (canSwitch) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
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
 * The day filter, as a scrolling strip of pills over the map.
 *
 * "All days" first, then one chip per day that has a pin, numbered to match the sheet. Each chip is
 * its own opaque pill, so it stays legible straight over satellite tiles without a backing panel.
 * Width-capped rather than full-bleed: the strip lives in the gap between the corner controls and
 * scrolls inside it, so a fortnight-long trip cannot push a chip under the layers button.
 */
@Composable
private fun MapDaySwitcher(
    days: List<LocalDate>,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .widthIn(max = 236.dp)
            .horizontalScroll(rememberScrollState()),
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
 * The itinerary, as a floating card that peeks from the bottom.
 *
 * The peek is not just a header: it headlines the next stop (name, how far, Navigate and Details),
 * so a glance answers "where next" without opening anything. Dragged up, it adds the day's progress
 * and the numbered stops with the travel between them. The card keeps a horizontal margin and all
 * four corners rounded so it reads as a pill floating over the map, not a panel bolted to the edge.
 */
@Composable
private fun ItinerarySheet(
    state: TripMapUiState,
    statuses: Map<Long, EventStatus>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFocusStop: (MapPin) -> Unit,
    onOpenStop: (MapPin) -> Unit,
    onNavigateStop: (MapPin) -> Unit
) {
    val metrics = AppThemeExtended.metrics

    Column(Modifier.fillMaxWidth()) {
        // Handle sits above the card, on the transparent sheet, like the mockup.
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .width(36.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = metrics.cardElevationRaised,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .navigationBarsPadding()
        ) {
            Column(Modifier.padding(bottom = 12.dp)) {
                SheetHeader(state = state, expanded = expanded, onToggle = onToggle)

                val focus = state.pins.firstOrNull { it.event.id == state.focusEventId }
                if (focus != null) {
                    NextStopCard(
                        pin = focus,
                        status = statuses[focus.event.id] ?: EventStatus.UPCOMING,
                        metric = nextStopMetric(state, focus),
                        onNavigate = { onNavigateStop(focus) },
                        onDetails = { onOpenStop(focus) }
                    )
                }

                if (expanded) {
                    TripProgressRow(state = state, statuses = statuses)
                    SheetBody(state = state, statuses = statuses, onFocusStop = onFocusStop)
                }
            }
        }
    }
}

/** The always-visible peek row: list icon, "Trip Itinerary", the day summary, and a state chevron. */
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
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = metrics.screenPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Trip Itinerary",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sheetSubtitleLine(state),
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
 * The headline of the sheet: the stop you are at or headed to, with the two things you do next.
 *
 * "HAPPENING NOW" when the focus stop is running, "NEXT STOP" otherwise — the same distinction the
 * pin's Current weight draws on the map. Navigate hands the coordinate to the device's own maps app;
 * Details opens the event. The card is tinted and accent-bordered so it reads as the one thing on
 * the sheet that is about *now*, not the list.
 */
@Composable
private fun NextStopCard(
    pin: MapPin,
    status: EventStatus,
    metric: String,
    onNavigate: () -> Unit,
    onDetails: () -> Unit
) {
    val metrics = AppThemeExtended.metrics
    val palette = AppThemeExtended.colors
    val overline = if (status == EventStatus.ACTIVE) "HAPPENING NOW" else "NEXT STOP"

    AppCard(
        color = palette.infoSoft,
        borderColor = palette.accent,
        borderWidth = metrics.borderWidthStrong,
        // The sheet's own Surface already casts the shadow; a nested elevation here would paint the
        // grey shadow-slab through the fill (see ui-compose rule), so this card stays flat.
        elevation = 0.dp,
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier.padding(horizontal = metrics.screenPadding, vertical = 4.dp)
    ) {
        Text(
            text = overline,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = palette.accent
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = pin.event.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = metric,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                text = "Navigate",
                onClick = onNavigate,
                icon = Icons.Default.Navigation,
                modifier = Modifier.weight(1f)
            )
            SecondaryButton(
                text = "Details",
                onClick = onDetails,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * The day's progress as a quiet line: "k of N places" and a thin track.
 *
 * Only truly-completed stops fill the track — a skipped or missed stop is behind you but not an
 * achievement, so it does not count toward "done". Faint on purpose: it reports progress without
 * competing with the stops themselves.
 */
@Composable
private fun TripProgressRow(
    state: TripMapUiState,
    statuses: Map<Long, EventStatus>
) {
    val metrics = AppThemeExtended.metrics
    val palette = AppThemeExtended.colors
    val total = state.pins.size
    val done = state.pins.count { statuses[it.event.id] == EventStatus.COMPLETED }
    val fraction = if (total > 0) done.toFloat() / total else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.screenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "$done of $total places",
            style = MaterialTheme.typography.labelMedium,
            color = palette.textFaint
        )
        Box(
            Modifier
                .weight(1f)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant)
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(CircleShape)
                    .background(palette.accent)
            )
        }
    }
}

/**
 * The numbered stops of the shown day, with the travel between them.
 *
 * Numbered to match the pins: the number on the card and the pin's place in the route are the same
 * claim about the same stop. Between two cards sits the leg that joins them — time, distance and
 * mode — so the list reads as a journey, not just a set. Bounded in height so the sheet stays a
 * draggable card rather than swallowing the screen; the list scrolls inside it.
 */
@Composable
private fun SheetBody(
    state: TripMapUiState,
    statuses: Map<Long, EventStatus>,
    onFocusStop: (MapPin) -> Unit
) {
    val metrics = AppThemeExtended.metrics
    val palette = AppThemeExtended.colors

    LazyColumn(
        modifier = Modifier.heightIn(max = 340.dp),
        contentPadding = PaddingValues(horizontal = metrics.screenPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(state.pins, key = { _, pin -> pin.event.id }) { index, pin ->
            Column {
                // Leg i-1 joins pin i-1 to this one; drawn above the card it leads into.
                if (index > 0) {
                    state.legs.getOrNull(index - 1)?.let { TravelConnector(it) }
                }
                StopCard(
                    pin = pin,
                    status = statuses[pin.event.id] ?: EventStatus.UPCOMING,
                    isFocus = pin.event.id == state.focusEventId,
                    onClick = { onFocusStop(pin) }
                )
            }
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

/**
 * The travel from the previous stop: a mode glyph and "time · distance".
 *
 * The car glyph is the routing profile the service was asked for (driving), a small honesty about
 * what the time means; when there is no route — no key, or the service was unreachable — the leg is
 * a straight-line distance with no time, and a ruler glyph says so rather than implying a drive.
 * Indented to sit under the number rail, so the eye reads it as the gap between two stops.
 */
@Composable
private fun TravelConnector(leg: TravelLeg) {
    val palette = AppThemeExtended.colors
    val routed = leg.durationSeconds != null
    Row(
        modifier = Modifier.padding(start = 46.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (routed) Icons.Default.DirectionsCar else Icons.Default.Straighten,
            contentDescription = null,
            tint = palette.textFaint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = legText(leg),
            style = MaterialTheme.typography.labelMedium,
            color = palette.textFaint
        )
    }
}

/** One stop: its number (weighted by progress), what it is, when it is, and where it stands. */
@Composable
private fun StopCard(
    pin: MapPin,
    status: EventStatus,
    isFocus: Boolean,
    onClick: () -> Unit
) {
    val metrics = AppThemeExtended.metrics
    val palette = AppThemeExtended.colors
    val eventColor = pin.event.type.colorIn(palette)
    val emphasised = status == EventStatus.ACTIVE || isFocus
    val badgeColor = badgeColorOf(status, isFocus, eventColor, palette.accent, palette.statusCompleted)

    AppCard(
        onClick = onClick,
        // Flat inside the sheet's elevated Surface — an elevation here would render as a grey slab.
        elevation = 0.dp,
        borderColor = if (emphasised) palette.accent else MaterialTheme.colorScheme.outline,
        borderWidth = if (emphasised) metrics.borderWidthStrong else metrics.borderWidth,
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
                        tint = eventColor,
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
            StatusBadge(status = status)
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

/** The pill's text: the shown day's number with the trip's name, or the whole trip. */
private fun dayTripLabel(state: TripMapUiState, trip: Trip): String {
    val day = state.selectedDay ?: return "All days · ${trip.name}"
    val index = state.days.indexOf(day)
    val number = if (index >= 0) index + 1 else 1
    return "Day $number · ${trip.name}"
}

/** The sheet header's second line: which day, its weekday, and how many places it holds. */
private fun sheetSubtitleLine(state: TripMapUiState): String {
    val places = state.pins.size.let { if (it == 1) "1 place" else "$it places" }
    val day = state.selectedDay ?: return "Whole trip · $places"
    val index = state.days.indexOf(day)
    val number = if (index >= 0) index + 1 else 1
    return "Day $number · ${DateTimeUtils.formatWeekdayShort(day)} · $places"
}

/**
 * The NEXT STOP card's distance line, in the most useful terms available.
 *
 * If the device knows where it is, "how far is it from me" is what a traveller actually wants, so
 * that wins — a straight-line estimate, marked with ≈ because it is as-the-crow-flies, not a route.
 * Otherwise the leg from the previous stop, if the day has one; failing that, just when it starts.
 */
private fun nextStopMetric(state: TripMapUiState, focus: MapPin): String {
    val fix = state.deviceLocation
    if (fix != null) {
        val metres = straightLineMeters(
            fix.latitude, fix.longitude, focus.place.latitude, focus.place.longitude
        )
        return "≈ ${formatKm(metres)} away"
    }
    val i = state.pins.indexOfFirst { it.event.id == state.focusEventId }
    val leg = if (i > 0) state.legs.getOrNull(i - 1) else null
    if (leg != null) return "${legText(leg)} from the last stop"
    return DateTimeUtils.formatTime(focus.event.startTime)
}

/**
 * Whether the app may already read the device's position, so the map can seed the dot without a
 * fresh prompt for a returning user who has granted it before. Either precision counts — an
 * approximate fix is enough for a glanceable "you are here".
 */
private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * How prominently a pin is drawn, from its computed status (§3 — progress, never a place name).
 *
 * Behind the traveller (done, skipped, missed) recedes; where they are ([EventStatus.ACTIVE]) or
 * headed next (the focus stop) is emphasised; everything else sits quietly ahead.
 */
private fun markerStateOf(status: EventStatus, isFocus: Boolean): MarkerState = when (status) {
    EventStatus.COMPLETED, EventStatus.SKIPPED, EventStatus.MISSED -> MarkerState.Completed
    EventStatus.ACTIVE -> MarkerState.Current
    else -> if (isFocus) MarkerState.Current else MarkerState.Upcoming
}

/** The number badge's fill, matching the pin's weight: muted behind, accent at/next, else its type. */
private fun badgeColorOf(
    status: EventStatus,
    isFocus: Boolean,
    eventColor: Color,
    accent: Color,
    completed: Color
): Color = when (status) {
    EventStatus.COMPLETED, EventStatus.SKIPPED, EventStatus.MISSED -> completed
    EventStatus.ACTIVE -> accent
    else -> if (isFocus) accent else eventColor
}

/** A leg as "time · distance" when it was routed, or just the straight-line distance when it wasn't. */
private fun legText(leg: TravelLeg): String =
    if (leg.durationSeconds != null) {
        "${formatDriveMinutes(leg.durationSeconds)} · ${formatKm(leg.distanceMeters)}"
    } else {
        formatKm(leg.distanceMeters)
    }

/** Metres as "850 m" below a kilometre, "4.8 km" above, so short hops keep their precision. */
private fun formatKm(meters: Double): String {
    if (meters < 950) return "${meters.roundToInt()} m"
    val tenths = (meters / 100).roundToInt()
    return "${tenths / 10}.${tenths % 10} km"
}

/** Seconds to a minute-grained label, never rounding a real leg down to "0 min". */
private fun formatDriveMinutes(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt().coerceAtLeast(1)
    return DateTimeUtils.formatMinutes(minutes.toLong())
}

/** Great-circle distance in metres — the crow's route, for a rough "how far from me". */
private fun straightLineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusM = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val sinLat = sin(dLat / 2)
    val sinLon = sin(dLon / 2)
    val a = sinLat * sinLat +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sinLon * sinLon
    return earthRadiusM * 2 * atan2(sqrt(a), sqrt(1 - a))
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

/** The floating itinerary card's peek: enough to show the header and the whole NEXT STOP card. */
private val SHEET_PEEK = 236.dp
