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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.feature.train.TrainFilter
import com.tripcompanion.app.feature.train.TrainListItem
import com.tripcompanion.app.feature.train.TrainsUiState
import com.tripcompanion.app.feature.train.TrainsViewModel
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.bookingSummary
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.SegmentedControl
import com.tripcompanion.app.ui.components.TrainCard
import com.tripcompanion.app.ui.theme.AppThemeExtended
import java.time.LocalDateTime

/**
 * Every train booking, in three lists.
 *
 * The cards carry only what the booking itself knows. Live running status costs a network
 * request per train, so it belongs to the one journey the user opens rather than to a list of
 * twelve — see `TrainPhase` for the reasoning.
 */
@Composable
fun TrainsScreen(
    onNavigateToTrainDetail: (Long) -> Unit,
    onNavigateToTicket: (Long) -> Unit,
    onNavigateToAddTrain: (Long) -> Unit,
    onNavigateToEditTrain: (Long, Long) -> Unit,
    onNavigateToNewTrip: () -> Unit,
    viewModel: TrainsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    var pendingDelete by remember { mutableStateOf<Train?>(null) }
    var pickingTrip by remember { mutableStateOf(false) }

    // One trip needs no question; several do, and the answer decides which trip the booking
    // hangs off, so it cannot be guessed.
    val startAdd = {
        val trips = state.trips
        when {
            trips.size == 1 -> onNavigateToAddTrain(trips.first().id)
            trips.size > 1 -> pickingTrip = true
            else -> onNavigateToNewTrip()
        }
    }

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
            bottom = metrics.navHeight + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item("header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Trains",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = trackingNote(state.isLive, state.providerName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "Add a train",
                    onClick = startAdd,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    background = MaterialTheme.colorScheme.primary,
                    borderColor = null,
                    size = 44.dp
                )
            }
        }

        if (!state.hasAnyTrain) {
            item("empty") {
                Spacer(Modifier.height(24.dp))
                if (state.hasTrips) {
                    EmptyState(
                        icon = Icons.Default.DirectionsTransit,
                        title = "No trains yet",
                        message = "Add a booking and you get its ticket, its route and — with " +
                            "an API key — live running status on the day.",
                        actionLabel = "Add a train",
                        onAction = startAdd
                    )
                } else {
                    EmptyState(
                        icon = Icons.Default.Luggage,
                        title = "No trains yet",
                        message = "A train belongs to a trip. Create a trip first and you can " +
                            "attach the journey to it.",
                        actionLabel = "Plan a trip",
                        onAction = onNavigateToNewTrip
                    )
                }
            }
            return@LazyColumn
        }

        item("tabs") {
            SegmentedControl(
                options = TrainFilter.entries.map { tabLabel(it, state) },
                selected = tabLabel(state.filter, state),
                onSelect = { label ->
                    TrainFilter.entries.firstOrNull { tabLabel(it, state) == label }
                        ?.let(viewModel::setFilter)
                }
            )
        }

        if (state.items.isEmpty()) {
            item("empty-tab") {
                Spacer(Modifier.height(16.dp))
                EmptyState(
                    icon = Icons.Default.DirectionsTransit,
                    title = if (state.filter == TrainFilter.PAST) {
                        "No journeys behind you"
                    } else {
                        "Nothing coming up"
                    },
                    message = if (state.filter == TrainFilter.PAST) {
                        "A booking moves here once its arrival time has passed."
                    } else {
                        "Your ${state.pastCount} past " +
                            (if (state.pastCount == 1) "journey is" else "journeys are") +
                            " under Past."
                    },
                    actionLabel = if (state.filter == TrainFilter.PAST) null else "Add a train",
                    onAction = if (state.filter == TrainFilter.PAST) null else startAdd
                )
            }
            return@LazyColumn
        }

        items(state.items, key = { it.train.id }) { item ->
            TrainRow(
                item = item,
                now = state.now,
                onOpen = { onNavigateToTrainDetail(item.train.id) },
                onTicket = { onNavigateToTicket(item.train.id) },
                onEdit = { onNavigateToEditTrain(item.train.tripId, item.train.id) },
                onDelete = { pendingDelete = item.train }
            )
        }
    }

    pendingDelete?.let { train ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete train ${train.number}?") },
            text = {
                Text(
                    "Its ticket details, saved route and cached running status go with it. " +
                        "The trip and its itinerary stay."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTrain(train.id)
                    pendingDelete = null
                }) {
                    Text("Delete", color = AppThemeExtended.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Keep it") }
            }
        )
    }

    if (pickingTrip) {
        AlertDialog(
            onDismissRequest = { pickingTrip = false },
            title = { Text("Which trip is this train for?") },
            text = {
                Column {
                    state.trips.forEach { trip ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    pickingTrip = false
                                    onNavigateToAddTrain(trip.id)
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = trip.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = DateTimeUtils.formatDateRange(trip.startDate, trip.endDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickingTrip = false }) { Text("Cancel") }
            }
        )
    }
}

/** A card plus the three things you do to a booking. */
@Composable
private fun TrainRow(
    item: TrainListItem,
    now: LocalDateTime,
    onOpen: () -> Unit,
    onTicket: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val train = item.train
    Column {
        TrainCard(
            trainNumber = train.number,
            trainName = trainDisplayName(train),
            originName = train.originName.ifBlank { train.originCode },
            originTime = DateTimeUtils.formatTime(train.departureTime),
            destinationName = train.destinationName.ifBlank { train.destinationCode },
            destinationTime = DateTimeUtils.formatTime(train.arrivalTime),
            dateLabel = DateTimeUtils.formatDayAndDate(train.departureTime.toLocalDate()),
            duration = DateTimeUtils.formatDuration(train.departureTime, train.arrivalTime),
            seatSummary = train.bookingSummary(),
            statusLabel = trainStatusLabel(train, now),
            statusTone = trainStatusTone(train, now),
            onClick = onOpen
        )
        if (item.tripName.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Luggage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.tripName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Ticket",
                onClick = onTicket,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.ConfirmationNumber
            )
            SecondaryButton(
                text = "Edit",
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Edit
            )
            AppIconButton(
                icon = Icons.Default.DeleteOutline,
                contentDescription = "Delete train ${train.number}",
                onClick = onDelete,
                tint = AppThemeExtended.colors.danger,
                background = AppThemeExtended.colors.dangerSoft,
                borderColor = null,
                size = 48.dp
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * What the header says under "Trains".
 *
 * Says which provider is answering rather than promising live tracking the build cannot do.
 */
private fun trackingNote(isLive: Boolean, providerName: String): String = when {
    isLive && providerName.isNotBlank() -> "Live tracking via $providerName"
    isLive -> "Live tracking on"
    else -> "Times from your bookings · add an API key for live tracking"
}

private fun tabLabel(filter: TrainFilter, state: TrainsUiState): String {
    val count = when (filter) {
        TrainFilter.UPCOMING -> state.upcomingCount
        TrainFilter.PAST -> state.pastCount
        TrainFilter.ALL -> state.totalCount
    }
    return if (count == 0) filter.label else "${filter.label} $count"
}
