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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Luggage
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.feature.trip.TripBucket
import com.tripcompanion.app.feature.trip.TripListItem
import com.tripcompanion.app.feature.trip.TripListViewModel
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.rememberScrollAwareNestedScrollConnection
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.SegmentedControl
import com.tripcompanion.app.ui.components.TripCard
import com.tripcompanion.app.ui.theme.AppThemeExtended
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Every trip, in three lists.
 *
 * The three lists partition the collection — a trip is upcoming, ongoing or past, never two of
 * those — so this is a segmented control rather than a set of chips.
 */
@Composable
fun TripListScreen(
    onNavigateToTrip: (Long) -> Unit,
    onNavigateToNewTrip: () -> Unit,
    onNavigateToEditTrip: (Long) -> Unit,
    viewModel: TripListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    var pendingDelete by remember { mutableStateOf<Trip?>(null) }

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

    val nestedScrollConnection = rememberScrollAwareNestedScrollConnection(
        hideThresholdDp = 32.dp,
        showThresholdDp = 20.dp
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .nestedScroll(nestedScrollConnection),
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
                        text = "Your trips",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (state.totalCount == 1) "1 trip" else "${state.totalCount} trips",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AppIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "New trip",
                    onClick = onNavigateToNewTrip,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    background = MaterialTheme.colorScheme.primary,
                    borderColor = null,
                    size = 44.dp
                )
            }
        }

        if (!state.hasAnyTrip) {
            item("empty") {
                Spacer(Modifier.height(24.dp))
                EmptyState(
                    icon = Icons.Default.Luggage,
                    title = "No trips yet",
                    message = "A trip holds your days, your places and your bookings. " +
                        "Create one and everything else hangs off it.",
                    actionLabel = "Plan a trip",
                    onAction = onNavigateToNewTrip
                )
            }
            return@LazyColumn
        }

        item("tabs") {
            SegmentedControl(
                options = TripBucket.entries.map { tabLabel(it, state.countFor(it)) },
                selected = tabLabel(state.bucket, state.countFor(state.bucket)),
                onSelect = { label ->
                    TripBucket.entries.firstOrNull { tabLabel(it, state.countFor(it)) == label }
                        ?.let(viewModel::setBucket)
                }
            )
        }

        if (state.items.isEmpty()) {
            item("empty-bucket") {
                Spacer(Modifier.height(16.dp))
                EmptyState(
                    icon = Icons.Default.Luggage,
                    title = emptyBucketTitle(state.bucket),
                    message = emptyBucketMessage(state.bucket),
                    actionLabel = if (state.bucket == TripBucket.PAST) null else "Plan a trip",
                    onAction = if (state.bucket == TripBucket.PAST) null else onNavigateToNewTrip
                )
            }
            return@LazyColumn
        }

        items(state.items, key = { it.trip.id }) { item ->
            TripRow(
                item = item,
                today = state.now.toLocalDate(),
                onOpen = { onNavigateToTrip(item.trip.id) },
                onEdit = { onNavigateToEditTrip(item.trip.id) },
                onDelete = { pendingDelete = item.trip }
            )
        }
    }

    pendingDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${trip.name}?") },
            text = {
                Text(
                    "Its days, activities, photo plans and train bookings go with it. " +
                        "This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTrip(trip.id)
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
}

/**
 * A card plus the two things you do to a trip from a list.
 *
 * Edit and delete live under the card rather than behind a long-press, because a long-press
 * menu is invisible until you already know it's there.
 */
@Composable
private fun TripRow(
    item: TripListItem,
    today: LocalDate,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        TripCard(
            name = item.trip.name,
            dateRange = DateTimeUtils.formatDateRange(item.trip.startDate, item.trip.endDate),
            status = item.trip.status,
            coverUri = item.trip.coverImageUri,
            summary = summaryFor(item),
            progressFraction = if (item.bucket == TripBucket.ONGOING && item.eventCount > 0) {
                item.progressFraction
            } else {
                null
            },
            countdown = countdownFor(item, today),
            onClick = onOpen
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                text = "Edit trip",
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Edit
            )
            AppIconButton(
                icon = Icons.Default.DeleteOutline,
                contentDescription = "Delete ${item.trip.name}",
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

/** "Upcoming 2" — the count belongs on the tab so an empty list is expected, not a surprise. */
private fun tabLabel(bucket: TripBucket, count: Int): String =
    if (count == 0) bucket.label else "${bucket.label} $count"

/** How many days, and how much of the plan is filled in. */
private fun summaryFor(item: TripListItem): String {
    val days = DateTimeUtils.dayCount(item.trip.startDate, item.trip.endDate)
    val dayPart = if (days == 1) "1 day" else "$days days"
    return when {
        item.eventCount == 0 -> "$dayPart · nothing planned yet"
        item.bucket == TripBucket.PAST ->
            "$dayPart · ${item.completedCount} of ${item.eventCount} done"
        else -> "$dayPart · ${item.eventCount} planned"
    }
}

/**
 * The pill on the cover.
 *
 * Answers a different question in each list: how long until it starts, how far into it we are,
 * and how long ago it ended.
 */
private fun countdownFor(item: TripListItem, today: LocalDate): String? = when (item.bucket) {
    TripBucket.UPCOMING -> {
        DateTimeUtils.formatLiveCountdown(
            targetDateTime = item.trip.startDate.atStartOfDay(),
            currentDateTime = LocalDateTime.now(),
            zeroText = "Trip starts now"
        )
    }
    TripBucket.ONGOING -> {
        val day = ChronoUnit.DAYS.between(item.trip.startDate, today).toInt() + 1
        val total = DateTimeUtils.dayCount(item.trip.startDate, item.trip.endDate)
        "Day $day of $total"
    }
    TripBucket.PAST -> {
        when (val days = ChronoUnit.DAYS.between(item.trip.endDate, today)) {
            in Long.MIN_VALUE..0L -> null
            1L -> "Ended yesterday"
            in 2L..30L -> "$days days ago"
            else -> null
        }
    }
}

private fun emptyBucketTitle(bucket: TripBucket): String = when (bucket) {
    TripBucket.UPCOMING -> "Nothing booked ahead"
    TripBucket.ONGOING -> "You're not travelling today"
    TripBucket.PAST -> "No finished trips"
}

private fun emptyBucketMessage(bucket: TripBucket): String = when (bucket) {
    TripBucket.UPCOMING -> "Plan the next one and it will wait here with a countdown."
    TripBucket.ONGOING -> "A trip moves here on the day it starts."
    TripBucket.PAST -> "Trips move here once their last day is behind you."
}
