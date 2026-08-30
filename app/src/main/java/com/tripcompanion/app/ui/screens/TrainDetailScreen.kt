package com.tripcompanion.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainPassenger
import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.model.TrainStopStatus
import com.tripcompanion.app.feature.train.TrainDetailUiState
import com.tripcompanion.app.feature.train.TrainDetailViewModel
import com.tripcompanion.app.feature.train.trainStatusMessage
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppDivider
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.AppProgressBar
import com.tripcompanion.app.ui.components.BadgeTone
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.MetaRow
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.RouteStop
import com.tripcompanion.app.ui.components.RouteTimeline
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.SectionHeader
import com.tripcompanion.app.ui.components.SegmentedControl
import com.tripcompanion.app.ui.components.StatCard
import com.tripcompanion.app.ui.components.StatusBadge
import com.tripcompanion.app.ui.components.noteworthyLabel
import com.tripcompanion.app.ui.components.tone
import com.tripcompanion.app.ui.theme.AppThemeExtended
import java.time.LocalTime

private const val TAB_LIVE = "Live"
private const val TAB_ROUTE = "Route"
private const val TAB_DETAILS = "Details"

/**
 * One train, tracked.
 *
 * The screen's whole job is to be honest about two things at once: where the train is, and how
 * much that answer can be trusted. Every position carries its age, and a position computed from
 * the timetable says so rather than borrowing the authority of a live feed.
 */
@Composable
fun TrainDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTicket: (Long) -> Unit,
    onNavigateToEditTrain: (Long, Long) -> Unit,
    onNavigateToEvent: (Long) -> Unit,
    viewModel: TrainDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    var tab by remember { mutableStateOf(TAB_LIVE) }
    var editingPlatform by remember { mutableStateOf(false) }
    var editingDelay by remember { mutableStateOf(false) }
    var movingPassenger by remember { mutableStateOf<TrainPassenger?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    if (state.isLoading) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val train = state.train
    if (train == null) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = metrics.screenPadding),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                icon = Icons.Default.DirectionsTransit,
                title = "This train is gone",
                message = "It was deleted, or the link that brought you here is stale.",
                actionLabel = "Go back",
                onAction = onNavigateBack
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = metrics.screenPadding,
            end = metrics.screenPadding,
            top = 12.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item("header") {
            TrainHeader(
                train = train,
                isRefreshing = state.isRefreshing,
                onBack = onNavigateBack,
                onRefresh = { viewModel.refresh(force = true) }
            )
        }

        state.notice?.let { notice ->
            item("notice") {
                NoticeCard(text = notice, onDismiss = viewModel::dismissNotice)
            }
        }

        item("tabs") {
            SegmentedControl(
                options = listOf(TAB_LIVE, TAB_ROUTE, TAB_DETAILS),
                selected = tab,
                onSelect = { tab = it }
            )
        }

        when (tab) {
            TAB_LIVE -> liveTab(state, train, viewModel)
            TAB_ROUTE -> routeTab(state, train, viewModel)
            else -> detailsTab(
                state = state,
                train = train,
                onTicket = { onNavigateToTicket(train.id) },
                onEdit = { onNavigateToEditTrain(train.tripId, train.id) },
                onOpenEvent = onNavigateToEvent,
                onEditPlatform = { editingPlatform = true },
                onEditDelay = { editingDelay = true },
                onMovePassenger = { movingPassenger = it },
                onDelete = { confirmingDelete = true }
            )
        }
    }

    if (editingPlatform) {
        TextEntryDialog(
            title = "Platform",
            explanation = "The railway's live feed does not carry a platform number, so this " +
                "one is your own note from the board or the announcement.",
            initial = train.platform,
            keyboardType = KeyboardType.Text,
            onDismiss = { editingPlatform = false },
            onConfirm = {
                viewModel.setPlatform(it)
                editingPlatform = false
            }
        )
    }

    if (editingDelay) {
        TextEntryDialog(
            title = "Known delay",
            explanation = "Minutes late, as announced. Used only when there is no live feed — " +
                "with one, the railway's own figure wins.",
            initial = train.knownDelayMinutes.takeIf { it > 0 }?.toString().orEmpty(),
            keyboardType = KeyboardType.Number,
            onDismiss = { editingDelay = false },
            onConfirm = { entered ->
                viewModel.setKnownDelay(entered.filter { it.isDigit() }.toIntOrNull() ?: 0)
                editingDelay = false
            }
        )
    }

    movingPassenger?.let { passenger ->
        AllotmentDialog(
            passenger = passenger,
            onDismiss = { movingPassenger = null },
            onConfirm = { coach, berth ->
                viewModel.setPassengerAllotment(passenger.id, coach, berth)
                movingPassenger = null
            }
        )
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete train ${train.number}?") },
            text = {
                Text(
                    "Its ticket details, saved route and cached running status go with it. " +
                        "The trip and its itinerary stay."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    viewModel.deleteTrain(onNavigateBack)
                }) {
                    Text("Delete", color = AppThemeExtended.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Keep it") }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Live
// ═══════════════════════════════════════════════════════════════════════════════

private fun LazyListScope.liveTab(
    state: TrainDetailUiState,
    train: Train,
    viewModel: TrainDetailViewModel
) {
    val status = state.status

    if (status == null) {
        item("live-empty") {
            AppCard {
                Text(
                    text = "Nothing to track yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.hasSchedule) {
                        "Tracking starts on the day of the journey."
                    } else {
                        "Fetch the timetable first — position is worked out against the route."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                state.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = trainStatusMessage(it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppThemeExtended.colors.dangerText
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        text = "Check now",
                        onClick = { viewModel.refresh(force = true) },
                        modifier = Modifier.weight(1f),
                        busy = state.isRefreshing,
                        icon = Icons.Default.Refresh
                    )
                    if (!state.hasSchedule) {
                        SecondaryButton(
                            text = "Get timetable",
                            onClick = viewModel::loadSchedule,
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Download
                        )
                    }
                }
            }
        }
        return
    }

    item("position") {
        PositionCard(state = state, status = status, train = train)
    }

    item("live-stats") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(
                icon = Icons.Default.Schedule,
                value = delayValue(status.delayMinutes),
                label = delayLabel(status.delayMinutes),
                modifier = Modifier.weight(1f),
                accent = if (status.isDelayed) AppThemeExtended.colors.danger
                else AppThemeExtended.colors.success,
                accentSoft = if (status.isDelayed) AppThemeExtended.colors.dangerSoft
                else AppThemeExtended.colors.successSoft
            )
            StatCard(
                icon = Icons.Default.Speed,
                // The API carries no speed. This is derived from actual departures and
                // distance, so it is an average — and an em dash when it cannot be worked out.
                value = status.averageSpeedKmph?.let { "${it.toInt()}" } ?: "—",
                label = "Avg speed",
                modifier = Modifier.weight(1f),
                accent = AppThemeExtended.colors.info,
                accentSoft = AppThemeExtended.colors.infoSoft
            )
            StatCard(
                icon = Icons.Default.Straighten,
                value = "${(status.progressFraction * 100).toInt()}%",
                label = "Of route",
                modifier = Modifier.weight(1f),
                accent = AppThemeExtended.colors.destination,
                accentSoft = AppThemeExtended.colors.destinationSoft
            )
        }
    }

    status.nextStop()?.let { next ->
        item("next-stop") {
            NextStopCard(stop = next, eta = status.nextStopEta, delayMinutes = status.delayMinutes)
        }
    }

    if (status.message.isNotBlank()) {
        item("provider-message") {
            AppCard {
                Text(
                    text = status.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    item("live-refresh") {
        SecondaryButton(
            text = "Check again",
            onClick = { viewModel.refresh(force = true) },
            icon = Icons.Default.Refresh
        )
    }
}

/**
 * Where the train is, and how much to trust it.
 *
 * The age of the snapshot and its source sit in the same card as the position, because a
 * position without either is the thing that makes someone miss a train.
 */
@Composable
private fun PositionCard(state: TrainDetailUiState, status: TrainRunStatus, train: Train) {
    val colors = AppThemeExtended.colors

    AppCard(shape = AppThemeExtended.metrics.cardShapeLarge) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = positionHeadline(status),
                    style = AppThemeExtended.text.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = status.currentStationName.ifBlank {
                        train.originName.ifBlank { train.originCode }
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(
                label = if (status.delayMinutes > 0) "${status.delayMinutes} min late"
                else if (status.delayMinutes < 0) "${-status.delayMinutes} min early"
                else "On time",
                tone = if (status.isDelayed) BadgeTone.NEGATIVE else BadgeTone.POSITIVE,
                uppercase = false
            )
        }

        Spacer(Modifier.height(14.dp))
        AppProgressBar(
            fraction = status.progressFraction,
            color = colors.journey,
            height = 8.dp
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Text(
                text = train.originName.ifBlank { train.originCode },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = train.destinationName.ifBlank { train.destinationCode },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(14.dp))
        AppDivider()
        Spacer(Modifier.height(12.dp))
        Text(
            text = freshnessLine(state, status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = trainStatusMessage(it),
                style = MaterialTheme.typography.bodySmall,
                color = colors.dangerText
            )
        }
    }
}

@Composable
private fun NextStopCard(stop: TrainStopStatus, eta: LocalTime?, delayMinutes: Int) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Next stop",
                    style = AppThemeExtended.text.eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stop.stationName.ifBlank { stop.stationCode },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = eta?.let(DateTimeUtils::formatTime)
                        ?: stop.scheduledArrival?.let(DateTimeUtils::formatTime)
                        ?: "—",
                    style = AppThemeExtended.text.timeLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (eta != null && delayMinutes != 0) "expected" else "scheduled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (stop.distanceKm > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${stop.distanceKm} km from the origin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Route
// ═══════════════════════════════════════════════════════════════════════════════

private fun LazyListScope.routeTab(
    state: TrainDetailUiState,
    train: Train,
    viewModel: TrainDetailViewModel
) {
    // Actuals win when a run has been observed; the stored timetable is the fallback so the
    // route still reads on a train that has not started.
    val liveStops = state.status?.stops.orEmpty()
    var rows = if (liveStops.isNotEmpty()) {
        liveStops.map { it.toRouteStop() }
    } else {
        state.stops.map { it.toRouteStop() }
    }
    
    // Filter to show only the journey segment (boarding to deboarding)
    val startIndex = rows.indexOfFirst { it.code.equals(train.originCode, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
    val endIndex = rows.indexOfLast { it.code.equals(train.destinationCode, ignoreCase = true) }.takeIf { it >= 0 } ?: (rows.size - 1)
    
    if (startIndex <= endIndex) {
        rows = rows.subList(startIndex, endIndex + 1)
    }

    if (rows.isEmpty()) {
        item("route-empty") {
            AppCard {
                Text(
                    text = "No timetable stored",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Fetch it once and the route, the stops and every ETA work offline " +
                        "for the rest of the trip.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                PrimaryButton(
                    text = "Get timetable",
                    onClick = viewModel::loadSchedule,
                    busy = state.isLoadingSchedule,
                    icon = Icons.Default.Download
                )
            }
        }
        return
    }

    item("route-header") {
        SectionHeader(
            title = "Route",
            subtitle = "${rows.size} stops" +
                if (liveStops.isEmpty()) " · scheduled times" else " · with actual times"
        )
    }

    item("route") {
        RouteTimeline(stops = rows)
    }

    item("route-refresh") {
        SecondaryButton(
            text = "Refresh timetable",
            onClick = viewModel::loadSchedule,
            icon = Icons.Default.Download
        )
    }
}

/** The observed row: scheduled on the left, actual beside it, delay only when there is one. */
private fun TrainStopStatus.toRouteStop(): RouteStop = RouteStop(
    name = stationName.ifBlank { stationCode },
    code = stationCode,
    scheduledLabel = (scheduledArrival ?: scheduledDeparture)
        ?.let(DateTimeUtils::formatTime) ?: "—",
    actualLabel = (actualArrival ?: actualDeparture)?.let(DateTimeUtils::formatTime),
    distanceLabel = distanceKm.takeIf { it > 0 }?.let { "$it km" },
    delayLabel = delayMinutes?.takeIf { it != 0 }?.let {
        if (it > 0) "+$it min" else "$it min"
    },
    isDeparted = isDeparted,
    isCurrent = isCurrent,
    dayLabel = dayOffset.takeIf { it > 0 }?.let { "Day ${it + 1}" }
)

/** The scheduled-only row: no actuals exist yet, so none are shown. */
private fun TrainStop.toRouteStop(): RouteStop = RouteStop(
    name = stationName.ifBlank { stationCode },
    code = stationCode,
    scheduledLabel = scheduledTime?.let(DateTimeUtils::formatTime) ?: "—",
    distanceLabel = distanceKm.takeIf { it > 0 }?.let { "$it km" },
    dayLabel = dayOffset.takeIf { it > 0 }?.let { "Day ${it + 1}" }
)

// ═══════════════════════════════════════════════════════════════════════════════
//  Details
// ═══════════════════════════════════════════════════════════════════════════════

private fun LazyListScope.detailsTab(
    state: TrainDetailUiState,
    train: Train,
    onTicket: () -> Unit,
    onEdit: () -> Unit,
    onOpenEvent: (Long) -> Unit,
    onEditPlatform: () -> Unit,
    onEditDelay: () -> Unit,
    onMovePassenger: (TrainPassenger) -> Unit,
    onDelete: () -> Unit
) {
    item("journey") {
        AppCard {
            SectionHeader(title = "Journey")
            Spacer(Modifier.height(6.dp))
            MetaRow(
                label = "Date",
                value = DateTimeUtils.formatFullDate(train.departureTime.toLocalDate()),
                icon = Icons.Default.Schedule
            )
            MetaRow(
                label = "Departs",
                value = "${train.originName.ifBlank { train.originCode }} · " +
                    DateTimeUtils.formatTime(train.departureTime)
            )
            MetaRow(
                label = "Arrives",
                value = "${train.destinationName.ifBlank { train.destinationCode }} · " +
                    DateTimeUtils.formatTime(train.arrivalTime)
            )
            MetaRow(
                label = "Duration",
                value = DateTimeUtils.formatDuration(train.departureTime, train.arrivalTime)
            )
        }
    }

    item("booking") {
        AppCard {
            SectionHeader(title = "Your booking")
            Spacer(Modifier.height(6.dp))
            MetaRow(label = "Class", value = train.travelClass.ifBlank { "Not set" })
            MetaRow(label = "PNR", value = train.pnr.ifBlank { "Not set" })
            if (train.quota.isNotBlank()) {
                MetaRow(label = "Quota", value = train.quota)
            }
            if (train.distanceKm > 0) {
                MetaRow(label = "Distance", value = "${train.distanceKm} km")
            }
            if (train.agentName.isNotBlank()) {
                MetaRow(label = "Booked with", value = train.agentName)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "Ticket",
                    onClick = onTicket,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.ConfirmationNumber
                )
                SecondaryButton(
                    text = "Edit booking",
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Edit
                )
            }
        }
    }

    item("passengers") {
        AppCard {
            SectionHeader(
                title = "Who is travelling",
                subtitle = if (train.passengers.isEmpty()) {
                    null
                } else {
                    "Tap anyone to move them — coaches get swapped on the platform."
                }
            )
            if (train.passengers.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "No passengers on this booking yet. Add them in the booking form, " +
                        "or import the e-ticket PDF and they come across with their berths.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Add passengers", onClick = onEdit, icon = Icons.Default.Edit)
            } else {
                train.passengers.forEachIndexed { index, passenger ->
                    if (index > 0) AppDivider()
                    PassengerRow(
                        passenger = passenger,
                        onMove = { onMovePassenger(passenger) }
                    )
                }
            }
        }
    }

    item("your-notes") {
        AppCard {
            SectionHeader(
                title = "What only you know",
                subtitle = "Your personal notes that supplement the live feed."
            )
            Spacer(Modifier.height(6.dp))
            MetaRow(
                label = "Platform",
                value = train.platform.ifBlank { "Not set" }
            )
            MetaRow(
                label = "Known delay",
                value = if (train.knownDelayMinutes > 0) "${train.knownDelayMinutes} min" else "None"
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = "Set platform",
                    onClick = onEditPlatform,
                    modifier = Modifier.weight(1f)
                )
                SecondaryButton(
                    text = "Set delay",
                    onClick = onEditDelay,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    state.linkedEvent?.let { event ->
        item("linked") {
            AppCard(onClick = { onOpenEvent(event.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.EventNote,
                        contentDescription = null,
                        tint = AppThemeExtended.colors.journey,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "On your itinerary",
                            style = AppThemeExtended.text.eyebrow,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (train.notes.isNotBlank()) {
        item("notes") {
            AppCard {
                SectionHeader(title = "Notes")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = train.notes,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    item("source") {
        Text(
            text = if (state.isLive) {
                "Live status from ${state.providerName.ifBlank { "the railway's feed" } }."
            } else {
                "No live source configured, so positions are worked out from the timetable, " +
                    "the clock and your own delay figure."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    item("delete") {
        SecondaryButton(
            text = "Delete this train",
            onClick = onDelete,
            icon = Icons.Default.DeleteOutline,
            contentColor = AppThemeExtended.colors.danger,
            borderColor = AppThemeExtended.colors.danger
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Pieces
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One traveller and where they sit, with the platform-side fix one tap away.
 *
 * The parsed allotment leads and the railway's own string is the fallback: "S4 · 8 · Side
 * upper" is what someone hunting for a berth in a corridor needs, while `CNF/S4/8/SU` is only
 * worth showing when the parse found nothing in it. That order matters because the string is
 * kept verbatim precisely so a column this app failed to understand still reaches the person
 * holding the ticket.
 *
 * A changed allotment gets its own line. Booked on the waitlist and since confirmed is the one
 * case where both columns are news, and it is the case where a single line would be a lie.
 */
@Composable
private fun PassengerRow(
    passenger: TrainPassenger,
    onMove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allotment = passenger.allotment
    val place = listOf(
        allotment.seatLabel.ifBlank { allotment.queueLabel },
        allotment.berthType.label
    ).filter { it.isNotBlank() }.joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onMove)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = passenger.displayLine.ifBlank { "Passenger ${passenger.serialNo}" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = place.ifBlank {
                    passenger.currentStatusText.ifBlank { "No berth allotted yet" }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (passenger.allotmentChanged) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Booked as ${passenger.bookingStatusText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppThemeExtended.colors.textFaint
                )
            }
        }
        allotment.status.noteworthyLabel?.let { label ->
            Spacer(Modifier.width(8.dp))
            StatusBadge(label = label, tone = allotment.status.tone)
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Default.Edit,
            contentDescription = "Change coach or berth",
            tint = AppThemeExtended.colors.textFaint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TrainHeader(
    train: Train,
    isRefreshing: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            size = 44.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = train.number,
                style = AppThemeExtended.text.eyebrow,
                color = AppThemeExtended.colors.journey
            )
            Text(
                text = train.name.ifBlank { "Train ${train.number}" },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isRefreshing) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            AppIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Check the train now",
                onClick = onRefresh,
                size = 44.dp
            )
        }
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    val colors = AppThemeExtended.colors
    AppCard(color = colors.warningSoft, borderColor = colors.warning) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Route,
                contentDescription = null,
                tint = colors.warningText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.warningText,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = colors.warningText)
            }
        }
    }
}

/** One field, one explanation of why the app is asking rather than knowing. */
@Composable
private fun TextEntryDialog(
    title: String,
    explanation: String,
    initial: String,
    keyboardType: KeyboardType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Coach and berth for one person.
 *
 * Two fields and no berth type, because this is the platform-side fix: what changes when a
 * coach is swapped or a berth reallotted is the coach and the number, and the berth type comes
 * with them. Editing the whole booking — who is travelling, ages, statuses — is the editor's
 * job, and this dialog says so by staying small.
 */
@Composable
private fun AllotmentDialog(
    passenger: TrainPassenger,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var coachValue by remember { mutableStateOf(passenger.allotment.coach) }
    var berthValue by remember { mutableStateOf(passenger.allotment.berth) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Coach and berth") },
        text = {
            Column {
                Text(
                    text = passenger.name.ifBlank { "Passenger ${passenger.serialNo}" },
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = coachValue,
                    onValueChange = { coachValue = it },
                    label = { Text("Coach") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = berthValue,
                    onValueChange = { berthValue = it },
                    label = { Text("Berth") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(coachValue, berthValue) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Wording
// ═══════════════════════════════════════════════════════════════════════════════

private fun positionHeadline(status: TrainRunStatus): String = when {
    status.hasArrived -> "Arrived at"
    status.hasStarted -> "Now near"
    else -> "Waiting at"
}

private fun delayValue(minutes: Int): String = when {
    minutes > 0 -> "+$minutes"
    minutes < 0 -> "$minutes"
    else -> "0"
}

private fun delayLabel(minutes: Int): String = when {
    minutes > 0 -> "Min late"
    minutes < 0 -> "Min early"
    else -> "On time"
}

/**
 * "Live · updated 2 min ago", or the same sentence with the word projection in it.
 *
 * Never says "live" about arithmetic. A projection is the timetable plus the clock, and a
 * reader who thinks a railway reported it will trust it further than it deserves.
 */
private fun freshnessLine(state: TrainDetailUiState, status: TrainRunStatus): String {
    val age = state.minutesSinceUpdate
    val ageText = when {
        age == null -> ""
        age <= 0L -> " · updated just now"
        age == 1L -> " · updated 1 min ago"
        else -> " · updated $age min ago"
    }
    val source = when (status.source) {
        TrainRunSource.LIVE -> "Live from ${state.providerName.ifBlank { "the railway" } }"
        TrainRunSource.PROJECTED -> "Worked out from the timetable, not observed"
    }
    return source + ageText
}
