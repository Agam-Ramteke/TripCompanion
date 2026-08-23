package com.tripcompanion.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.StayDetails
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.feature.trip.TimelineDay
import com.tripcompanion.app.feature.trip.TimelineUiState
import com.tripcompanion.app.feature.trip.TimelineViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.AppProgressBar
import com.tripcompanion.app.ui.components.bookingSummary
import com.tripcompanion.app.ui.components.DaySelector
import com.tripcompanion.app.ui.components.DayTab
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.HotelCard
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.StatusBadge
import com.tripcompanion.app.ui.components.TextActionButton
import com.tripcompanion.app.ui.components.TimelineItem
import com.tripcompanion.app.ui.components.TrainCard
import com.tripcompanion.app.ui.components.color
import com.tripcompanion.app.ui.components.label
import com.tripcompanion.app.ui.components.softColor
import com.tripcompanion.app.ui.components.statusLabel
import com.tripcompanion.app.ui.components.statusTone
import com.tripcompanion.app.ui.theme.AppThemeExtended
import java.time.LocalDateTime

/**
 * The itinerary: one day of the trip drawn as a vertical rail of stops.
 *
 * A day is a sequence, which is why this is the one screen in the app that uses a connected
 * timeline and ordinal position — here the order carries information the reader needs.
 * Everything drawn here comes from the user's own events; there is no sample data in this file.
 */
@Composable
fun TimelineScreen(
    onNavigateToEventDetail: (Long) -> Unit,
    onNavigateToAddEvent: (Long) -> Unit,
    onNavigateToEditEvent: (Long, Long) -> Unit,
    onNavigateToMap: (Long) -> Unit,
    onNavigateToTrainDetail: (Long) -> Unit,
    onNavigateToEditTrain: (Long, Long) -> Unit,
    onNavigateToNewTrip: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val trip = state.trip
    if (trip == null) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.screenPadding),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                icon = Icons.Default.Luggage,
                title = "No trip to show",
                message = "An itinerary belongs to a trip. Create one and its days appear here.",
                actionLabel = "Plan a trip",
                onAction = onNavigateToNewTrip
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = metrics.navHeight + 24.dp),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item("header") {
            ItineraryHeader(
                state = state,
                tripName = trip.name,
                onAdd = { onNavigateToAddEvent(trip.id) },
                onMap = { onNavigateToMap(trip.id) },
                onToggleCompleted = viewModel::setShowCompleted
            )
        }

        item("days") {
            DaySelector(
                days = state.days.map { it.toTab() },
                selected = state.selectedDay,
                onSelect = viewModel::selectDay,
                contentPadding = PaddingValues(horizontal = metrics.screenPadding)
            )
        }

        if (state.dayEvents.isEmpty()) {
            item("empty-day") {
                Box(Modifier.padding(horizontal = metrics.screenPadding)) {
                    if (state.hiddenCompletedCount > 0) {
                        AllDoneCard(
                            hiddenCount = state.hiddenCompletedCount,
                            onShowCompleted = { viewModel.setShowCompleted(true) }
                        )
                    } else {
                        EmptyState(
                            icon = Icons.AutoMirrored.Filled.EventNote,
                            title = "Nothing planned for this day",
                            message = "Add where you're going, what you're doing and when, " +
                                "and it lines up here in order.",
                            actionLabel = "Add an activity",
                            onAction = { onNavigateToAddEvent(trip.id) }
                        )
                    }
                }
            }
            return@LazyColumn
        }

        // One item, not one per event: the rail is a continuous line, and a LazyColumn's
        // vertical arrangement would put a gap in it between every stop.
        item("rail") {
            Column(Modifier.padding(horizontal = metrics.screenPadding)) {
                state.dayEvents.forEachIndexed { index, event ->
                    TimelineItem(
                        time = DateTimeUtils.formatTime(event.startTime),
                        type = event.type,
                        status = event.status,
                        isFirst = index == 0,
                        isLast = index == state.dayEvents.lastIndex
                    ) {
                        val train = state.trains[event.id]
                        ItineraryStop(
                            event = event,
                            place = event.locationId?.let { state.places[it] },
                            stayDetails = state.stayDetails[event.id],
                            train = train,
                            isCurrent = event.id == state.currentEventId,
                            now = state.now,
                            // A ticket-shaped card opens the ticket. Everything else opens the
                            // activity it is.
                            onOpen = {
                                if (train != null) {
                                    onNavigateToTrainDetail(train.id)
                                } else {
                                    onNavigateToEventDetail(event.id)
                                }
                            },
                            onEdit = {
                                // A booked journey is edited as the booking. Its times come from
                                // the ticket, so sending the user to the activity editor would
                                // offer them two fields that the next ticket save overwrites.
                                if (train != null) {
                                    onNavigateToEditTrain(train.tripId, train.id)
                                } else {
                                    onNavigateToEditEvent(trip.id, event.id)
                                }
                            },
                            onDone = { viewModel.completeEvent(event) },
                            onSkip = { viewModel.skipEvent(event) },
                            onReopen = { viewModel.reopenEvent(event) }
                        )
                    }
                }
            }
        }

        item("add") {
            Box(Modifier.padding(horizontal = metrics.screenPadding)) {
                SecondaryButton(
                    text = "Add to this day",
                    onClick = { onNavigateToAddEvent(trip.id) },
                    icon = Icons.Default.Add
                )
            }
        }
    }
}

/** Trip name, how much of the whole plan is done, and the two things you do to a day. */
@Composable
private fun ItineraryHeader(
    state: TimelineUiState,
    tripName: String,
    onAdd: () -> Unit,
    onMap: () -> Unit,
    onToggleCompleted: (Boolean) -> Unit
) {
    val metrics = AppThemeExtended.metrics
    Column(Modifier.padding(horizontal = metrics.screenPadding, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Itinerary",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = tripName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AppIconButton(
                icon = Icons.Default.Map,
                contentDescription = "Trip map",
                onClick = onMap,
                size = 44.dp
            )
            Spacer(Modifier.width(8.dp))
            AppIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Add an activity",
                onClick = onAdd,
                tint = MaterialTheme.colorScheme.onPrimary,
                background = MaterialTheme.colorScheme.primary,
                borderColor = null,
                size = 44.dp
            )
        }
        if (state.hasEvents) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${state.completedEventCount} of ${state.totalEventCount} done " +
                        "across the trip",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                // One control, and its label says what tapping it does. When completed items
                // are hidden it also carries the count, so nothing on the day is silently gone.
                if (!state.showCompleted) {
                    TextActionButton(
                        text = if (state.hiddenCompletedCount > 0) {
                            "Show ${state.hiddenCompletedCount} done"
                        } else {
                            "Show done"
                        },
                        onClick = { onToggleCompleted(true) }
                    )
                } else if (state.completedEventCount > 0) {
                    TextActionButton(
                        text = "Hide done",
                        onClick = { onToggleCompleted(false) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AppProgressBar(
                fraction = state.progressFraction,
                color = AppThemeExtended.colors.success
            )
        }
    }
}

/** A finished day still needs to say so, rather than reading as a day nobody planned. */
@Composable
private fun AllDoneCard(hiddenCount: Int, onShowCompleted: () -> Unit) {
    AppCard {
        Text(
            text = "Everything on this day is done",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (hiddenCount == 1) {
                "One completed activity is hidden while \"Show completed\" is off."
            } else {
                "$hiddenCount completed activities are hidden while \"Show completed\" is off."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        SecondaryButton(text = "Show them", onClick = onShowCompleted)
    }
}

/**
 * One stop on the rail.
 *
 * A stay and a booked journey are drawn as the same cards their own screens draw, because a
 * booking should not look like two different things depending on which screen you reached it from
 * (§4) — the paperwork that makes a stay a stay is the photograph, the address and the two times,
 * and what makes a journey a journey is the two stations, the class and the berths. None of it
 * fits the activity card. Everything else is the activity card: "what we're doing" sits under the
 * title in full (§14) rather than truncated to a line, because it is the reason the entry exists
 * and a plan you cannot read is not a plan.
 *
 * Whichever card is drawn, the three verbs underneath are the same ones.
 */
@Composable
private fun ItineraryStop(
    event: Event,
    place: Location?,
    stayDetails: StayDetails?,
    train: Train?,
    isCurrent: Boolean,
    now: LocalDateTime,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onReopen: () -> Unit
) {
    val colors = AppThemeExtended.colors
    val metrics = AppThemeExtended.metrics
    val borderColor = if (isCurrent) colors.accent else MaterialTheme.colorScheme.outline
    val borderWidth = if (isCurrent) metrics.borderWidthStrong else metrics.borderWidth

    if (train != null) {
        // The Trains screen's shape: a card this dense cannot host a button row, so the verbs go
        // directly beneath it rather than being crammed into the card.
        Column(Modifier.padding(bottom = 4.dp)) {
            TrainCard(
                trainNumber = train.number,
                trainName = trainDisplayName(train),
                originName = train.originName.ifBlank { train.originCode },
                originTime = DateTimeUtils.formatTime(train.departureTime),
                destinationName = train.destinationName.ifBlank { train.destinationCode },
                destinationTime = DateTimeUtils.formatTime(train.arrivalTime),
                // The rail already carries the day, so the date is only worth the line when the
                // train lands on a different one — which the two times alone would hide.
                dateLabel = train.arrivalTime.toLocalDate()
                    .takeIf { it != train.departureTime.toLocalDate() }
                    ?.let { "Arrives " + DateTimeUtils.formatShortDate(it) },
                duration = DateTimeUtils.formatDuration(train.departureTime, train.arrivalTime),
                seatSummary = train.bookingSummary(),
                // Same rule as a stay: a journey the user has ruled on says so, otherwise the
                // badge is the booking's own — which is what the Trains screen shows for it.
                statusLabel = if (event.isDecided) {
                    statusLabel(event.status)
                } else {
                    trainStatusLabel(train, now)
                },
                statusTone = if (event.isDecided) {
                    statusTone(event.status)
                } else {
                    trainStatusTone(train, now)
                },
                borderColor = borderColor,
                borderWidth = borderWidth,
                onClick = onOpen
            )
            if (event.whatWeAreDoing.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = event.whatWeAreDoing,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            StopActions(
                event = event,
                onEdit = onEdit,
                onDone = onDone,
                onSkip = onSkip,
                onReopen = onReopen
            )
        }
        return
    }

    if (event.type == EventType.STAY) {
        // The Trains screen's shape: a media-led card cannot host a button row, so the verbs
        // go directly beneath it rather than being crammed into the card.
        Column(Modifier.padding(bottom = 4.dp)) {
            HotelCard(
                name = event.title,
                imageUri = stayImageUri(stayDetails, place),
                address = stayAddress(stayDetails, place) ?: place?.name,
                checkInLabel = stayStamp(event.startTime),
                checkOutLabel = stayStamp(event.endTime),
                nightsLabel = stayNightsLabel(stayNights(event)),
                // A stay the user has ruled on says so; otherwise the badge is where the stay
                // is in time, which is what the Stay screen shows for the same booking.
                statusLabel = if (event.isDecided) {
                    statusLabel(event.status)
                } else {
                    stayStatusLabel(event, now)
                },
                statusTone = if (event.isDecided) {
                    statusTone(event.status)
                } else {
                    stayStatusTone(event, now)
                },
                borderColor = borderColor,
                borderWidth = borderWidth,
                onClick = onOpen
            )
            if (event.whatWeAreDoing.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = event.whatWeAreDoing,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            StopActions(
                event = event,
                onEdit = onEdit,
                onDone = onDone,
                onSkip = onSkip,
                onReopen = onReopen
            )
        }
        return
    }

    AppCard(
        modifier = Modifier.padding(bottom = 4.dp),
        onClick = onOpen,
        borderColor = borderColor,
        borderWidth = borderWidth
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.type.label,
                    style = AppThemeExtended.text.eyebrow,
                    color = event.type.color
                )
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusBadge(status = event.status)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = DateTimeUtils.formatTimeRange(event.startTime, event.endTime),
            style = AppThemeExtended.text.time,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (place != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = event.type.color,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (event.whatWeAreDoing.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = event.whatWeAreDoing,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(14.dp))
        StopActions(
            event = event,
            onEdit = onEdit,
            onDone = onDone,
            onSkip = onSkip,
            onReopen = onReopen
        )
    }
}

/**
 * Whether the user has already ruled on this stop, rather than the clock having.
 *
 * Shared with Home, which draws the same decision on its next-up card: a stop the user has marked
 * done says "Done" there too, instead of the booking's own live status contradicting them.
 */
internal val Event.isDecided: Boolean
    get() = status == EventStatus.COMPLETED || status == EventStatus.SKIPPED

/**
 * The three things you do to a stop.
 *
 * Identical for every kind of activity, so a stay and a museum visit are marked done the same
 * way — the card above changes, the verbs do not.
 */
@Composable
private fun StopActions(
    event: Event,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onReopen: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (event.isDecided) {
            SecondaryButton(
                text = "Reopen",
                onClick = onReopen,
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.Undo
            )
        } else {
            PrimaryButton(
                text = "Done",
                onClick = onDone,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CheckCircle
            )
            SecondaryButton(
                text = "Skip",
                onClick = onSkip,
                modifier = Modifier.weight(1f)
            )
        }
        AppIconButton(
            icon = Icons.AutoMirrored.Filled.EventNote,
            contentDescription = "Edit ${event.title}",
            onClick = onEdit,
            tint = event.type.color,
            background = event.type.softColor,
            borderColor = null,
            size = 48.dp
        )
    }
}

/** Maps a day to its tab. The wording lives here, in the screen, not in the ViewModel. */
private fun TimelineDay.toTab(): DayTab = DayTab(
    date = date,
    label = DateTimeUtils.formatWeekdayShort(date),
    dayOfMonth = date.dayOfMonth.toString(),
    dayNumber = dayNumber,
    eventCount = eventCount,
    isToday = isToday
)
