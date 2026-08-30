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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Hotel
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.core.util.TimeDeviationUtils
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.StayDetails
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.feature.trip.TimelineDay
import com.tripcompanion.app.feature.trip.TimelineDayEvent
import com.tripcompanion.app.feature.trip.TimelineUiState
import com.tripcompanion.app.feature.trip.TimelineViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppDivider
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.AppProgressBar
import com.tripcompanion.app.ui.components.BadgeTone
import com.tripcompanion.app.ui.components.bookingSummary
import com.tripcompanion.app.ui.components.DaySelector
import com.tripcompanion.app.ui.components.DayTab
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.Eyebrow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.tripcompanion.app.ui.components.Eyebrow
import com.tripcompanion.app.ui.components.HotelCard
import com.tripcompanion.app.ui.components.PhotoBackdrop
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.tripcompanion.app.ui.components.rememberScrollAwareNestedScrollConnection
import com.tripcompanion.app.ui.theme.LocalReducedMotion
import com.tripcompanion.app.ui.theme.MotionTokens
import kotlinx.coroutines.launch

/**
 * The itinerary: one day of the trip drawn as a vertical rail of stops.
 *
 * A day is a sequence, which is why this is the one screen in the app that uses a connected
 * timeline and ordinal position — here the order carries information the reader needs.
 * Everything drawn here comes from the user's own events; there is no sample data in this file.
 *
 * Horizontal paging enables smooth date-to-date swiping with full day-tab synchronization.
 */
@Composable
fun TimelineScreen(
    onNavigateToEventDetail: (Long) -> Unit,
    onNavigateToAddEvent: (Long, String?) -> Unit,
    onNavigateToEditEvent: (Long, Long) -> Unit,
    onNavigateToMap: (Long) -> Unit,
    onNavigateToTrainDetail: (Long) -> Unit,
    onNavigateToEditTrain: (Long, Long) -> Unit,
    onNavigateToNewTrip: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    val reducedMotion = LocalReducedMotion.current
    val coroutineScope = rememberCoroutineScope()

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

    val trip = state.trip
    if (trip == null) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
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

    val days = state.days
    val initialIndex = remember(days, state.selectedDay) {
        val idx = days.indexOfFirst { it.date == state.selectedDay }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(initialPage = initialIndex) { days.size }

    // Sync swiped page to ViewModel selectedDay once scroll settles
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress, days) {
        if (!pagerState.isScrollInProgress && days.isNotEmpty() && pagerState.currentPage in days.indices) {
            val pageDate = days[pagerState.currentPage].date
            if (pageDate != state.selectedDay) {
                viewModel.selectDay(pageDate)
            }
        }
    }

    // Sync programmatic date selection to pager
    LaunchedEffect(state.selectedDay, days) {
        val targetIndex = days.indexOfFirst { it.date == state.selectedDay }
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage && !pagerState.isScrollInProgress) {
            if (reducedMotion) {
                pagerState.scrollToPage(targetIndex)
            } else {
                pagerState.animateScrollToPage(
                    targetIndex,
                    animationSpec = tween(
                        durationMillis = MotionTokens.PAGE_TRANSITION_DURATION,
                        easing = MotionTokens.StandardEasing
                    )
                )
            }
        }
    }

    var isHeaderCollapsed by remember { mutableStateOf(false) }

    val nestedScrollConnection = rememberScrollAwareNestedScrollConnection(
        hideThresholdDp = 32.dp,
        showThresholdDp = 20.dp,
        onScrollDown = {
            if (!isHeaderCollapsed) {
                isHeaderCollapsed = true
            }
        },
        onScrollUp = {
            if (isHeaderCollapsed) {
                isHeaderCollapsed = false
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .nestedScroll(nestedScrollConnection)
    ) {
        AnimatedVisibility(
            visible = !isHeaderCollapsed,
            enter = expandVertically(
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing
                )
            ) + fadeIn(
                animationSpec = tween(durationMillis = 180)
            ),
            exit = shrinkVertically(
                animationSpec = tween(
                    durationMillis = 220,
                    easing = FastOutSlowInEasing
                )
            ) + fadeOut(
                animationSpec = tween(durationMillis = 150)
            )
        ) {
            ItineraryHeader(
                state = state,
                tripName = trip.name,
                onAdd = { onNavigateToAddEvent(trip.id, state.selectedDay?.toString()) },
                onMap = { onNavigateToMap(trip.id) },
                onToggleCompleted = viewModel::setShowCompleted
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = if (isHeaderCollapsed) 2.dp else 0.dp
        ) {
            Column {
                DaySelector(
                    days = days.map { it.toTab() },
                    selected = state.selectedDay,
                    onSelect = { date ->
                        viewModel.selectDay(date)
                    },
                    contentPadding = PaddingValues(horizontal = metrics.screenPadding)
                )
                Spacer(Modifier.height(metrics.rowGap))
            }
        }

        if (days.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = metrics.screenPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.EventNote,
                    title = "Nothing planned yet",
                    message = "Add where you're going and what you're doing.",
                    actionLabel = "Add an activity",
                    onAction = { onNavigateToAddEvent(trip.id, null) }
                )
            }
        } else {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val day = days.getOrNull(pageIndex) ?: return@HorizontalPager
                val dayEvents = state.allDayEvents[day.date].orEmpty()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = metrics.navHeight + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
                ) {
                    if (dayEvents.isEmpty()) {
                        item("empty-day-${day.date}") {
                            Box(Modifier.padding(horizontal = metrics.screenPadding)) {
                                if (state.hiddenCompletedCount > 0 && day.date == state.selectedDay) {
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
                                        onAction = { onNavigateToAddEvent(trip.id, day.date.toString()) }
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = dayEvents,
                            key = { _, item ->
                                "stop-${item.event.id}-${if (item.isCheckIn) "in" else if (item.isCheckOut) "out" else if (item.isTrainDeparture) "dep" else if (item.isTrainArrival) "arr" else "act"}"
                            }
                        ) { index, item ->
                            val train = state.allTrains[item.event.id]
                            val stayDetails = state.allStayDetails[item.event.id]

                            val isDepartureLeg = item.isTrainDeparture || (train != null && !item.isTrainArrival && train.departureTime.toLocalDate() == day.date)
                            val isArrivalLeg = item.isTrainArrival || (train != null && !item.isTrainDeparture && train.arrivalTime.toLocalDate() == day.date)
                            val isBoarded = train != null && (train.actualBoardingTime != null || item.event.actualStartTime != null)
                            val isArrived = train != null && (train.actualArrivalTime != null || item.event.actualEndTime != null || item.event.status == EventStatus.COMPLETED)
                            val isCheckedIn = stayDetails?.actualCheckIn != null || item.event.actualStartTime != null
                            val isCheckedOut = stayDetails?.actualCheckOut != null || item.event.actualEndTime != null || item.event.status == EventStatus.COMPLETED

                            val eventLabel = when {
                                train != null && isDepartureLeg && !isArrivalLeg -> "DEPARTURE"
                                train != null && isArrivalLeg && !isDepartureLeg -> "ARRIVAL"
                                train != null -> "JOURNEY"
                                item.event.type == EventType.STAY && item.isCheckIn -> "CHECK-IN"
                                item.event.type == EventType.STAY && item.isCheckOut -> "CHECK-OUT"
                                item.event.type == EventType.STAY -> "STAY"
                                else -> item.event.type.label.uppercase()
                            }

                            val (statusText, statusTone) = when {
                                train != null && isDepartureLeg && isBoarded -> {
                                    val stamp = train.actualBoardingTime ?: item.event.actualStartTime ?: train.departureTime
                                    TimeDeviationUtils.formatLifecycleBadge("Boarded", stamp, train.departureTime) to BadgeTone.POSITIVE
                                }
                                train != null && isArrivalLeg && isArrived -> {
                                    val stamp = train.actualArrivalTime ?: item.event.actualEndTime ?: train.arrivalTime
                                    TimeDeviationUtils.formatLifecycleBadge("Arrived", stamp, train.arrivalTime) to BadgeTone.POSITIVE
                                }
                                train != null && isArrivalLeg && isBoarded -> "In transit" to BadgeTone.INFO
                                train != null && isDepartureLeg && !isBoarded -> "Upcoming" to BadgeTone.INFO
                                train != null && isArrivalLeg -> "Upcoming" to BadgeTone.INFO
                                item.event.type == EventType.STAY && item.isCheckIn && isCheckedIn -> {
                                    val stamp = stayDetails?.actualCheckIn ?: item.event.actualStartTime ?: item.event.startTime
                                    TimeDeviationUtils.formatLifecycleBadge("Checked in", stamp, item.event.startTime) to BadgeTone.POSITIVE
                                }
                                item.event.type == EventType.STAY && item.isCheckOut && isCheckedOut -> {
                                    val stamp = stayDetails?.actualCheckOut ?: item.event.actualEndTime ?: item.event.endTime
                                    TimeDeviationUtils.formatLifecycleBadge("Checked out", stamp, item.event.endTime) to BadgeTone.POSITIVE
                                }
                                item.event.isDecided -> statusLabel(item.event.status) to statusTone(item.event.status)
                                else -> "Upcoming" to BadgeTone.INFO
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = metrics.screenPadding)
                                    .animateItem()
                            ) {
                                TimelineItem(
                                    time = DateTimeUtils.formatTime(item.displayTime),
                                    type = item.event.type,
                                    eventLabel = eventLabel,
                                    statusLabel = statusText,
                                    statusTone = statusTone,
                                    isFirst = index == 0,
                                    isLast = index == dayEvents.lastIndex
                                ) {
                                    ItineraryStop(
                                        dayEvent = item,
                                        event = item.event,
                                        place = item.event.locationId?.let { state.allPlaces[it] },
                                        stayDetails = stayDetails,
                                        train = train,
                                        isCurrent = item.event.id == state.currentEventId,
                                        now = state.now,
                                        onOpen = {
                                            if (train != null) {
                                                onNavigateToTrainDetail(train.id)
                                            } else {
                                                onNavigateToEventDetail(item.event.id)
                                            }
                                        },
                                        onEdit = {
                                            if (train != null) {
                                                onNavigateToEditTrain(train.tripId, train.id)
                                            } else {
                                                onNavigateToEditEvent(trip.id, item.event.id)
                                            }
                                        },
                                        onDone = { viewModel.completeEvent(item.event) },
                                        onSkip = { viewModel.skipEvent(item.event) },
                                        onReopen = { viewModel.reopenEvent(item.event) },
                                        onBoardTrain = { tr, ev -> viewModel.boardTrain(tr, ev) },
                                        onUndoBoardTrain = { tr, ev -> viewModel.undoBoardTrain(tr, ev) },
                                        onArriveTrain = { tr, ev -> viewModel.arriveTrain(tr, ev) },
                                        onUndoArriveTrain = { tr, ev -> viewModel.undoArriveTrain(tr, ev) },
                                        onCheckInStay = { st, ev -> viewModel.checkInStay(st, ev) },
                                        onUndoCheckInStay = { st, ev -> viewModel.undoCheckInStay(st, ev) },
                                        onCheckOutStay = { st, ev -> viewModel.checkOutStay(st, ev) },
                                        onUndoCheckOutStay = { st, ev -> viewModel.undoCheckOutStay(st, ev) }
                                    )
                                }
                            }
                        }

                        item("add-${day.date}") {
                            Box(Modifier.padding(horizontal = metrics.screenPadding)) {
                                SecondaryButton(
                                    text = "Add to this day",
                                    onClick = { onNavigateToAddEvent(trip.id, day.date.toString()) },
                                    icon = Icons.Default.Add
                                )
                            }
                        }
                    }
                }
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Add activity",
                onClick = onAdd,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.hasEvents) {
            Spacer(Modifier.height(14.dp))
            AppProgressBar(
                fraction = state.progressFraction,
                color = AppThemeExtended.colors.success
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${state.completedEventCount} of ${state.totalEventCount} done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextActionButton(
                    text = if (state.showCompleted) "Hide done" else "Show done",
                    onClick = { onToggleCompleted(!state.showCompleted) }
                )
            }
        }
    }
}

/**
 * The placeholder when every event on this day is completed and the user has chosen to hide
 * completed events. A day with completed items is not "empty" in the same sense as an un-planned
 * day, so it gets its own wording and a one-tap way to show them again.
 */
@Composable
private fun AllDoneCard(
    hiddenCount: Int,
    onShowCompleted: () -> Unit
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = AppThemeExtended.colors.success,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "All caught up for this day",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$hiddenCount completed ${if (hiddenCount == 1) "stop" else "stops"} hidden",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextActionButton(text = "Show", onClick = onShowCompleted)
        }
    }
}/**
 * One stop in the day's itinerary rail:
 * - A journey with a train booking is drawn as [ItineraryTrainCard] (clean endpoints, schedule & seats).
 * - A stay is drawn as [ItineraryStayCard] (neutral card, clean check-in/out stamps, no blurred photo).
 * - Everything else is drawn as [ItineraryActivityCard] (neutral card with title, place, time range).
 */
@Composable
private fun ItineraryStop(
    dayEvent: TimelineDayEvent,
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
    onReopen: () -> Unit,
    onBoardTrain: (Train, Event) -> Unit = { _, _ -> },
    onUndoBoardTrain: (Train, Event) -> Unit = { _, _ -> },
    onArriveTrain: (Train, Event) -> Unit = { _, _ -> },
    onUndoArriveTrain: (Train, Event) -> Unit = { _, _ -> },
    onCheckInStay: (StayDetails?, Event) -> Unit = { _, _ -> },
    onUndoCheckInStay: (StayDetails?, Event) -> Unit = { _, _ -> },
    onCheckOutStay: (StayDetails?, Event) -> Unit = { _, _ -> },
    onUndoCheckOutStay: (StayDetails?, Event) -> Unit = { _, _ -> }
) {
    val colors = AppThemeExtended.colors
    val metrics = AppThemeExtended.metrics
    val borderColor = if (isCurrent) colors.accent else MaterialTheme.colorScheme.outline
    val borderWidth = if (isCurrent) metrics.borderWidthStrong else metrics.borderWidth

    if (train != null) {
        val isDepartureLeg = dayEvent.isTrainDeparture || (!dayEvent.isTrainArrival && train.departureTime.toLocalDate() == dayEvent.event.startTime.toLocalDate())
        val isArrivalLeg = dayEvent.isTrainArrival || (!dayEvent.isTrainDeparture && train.arrivalTime.toLocalDate() == dayEvent.event.endTime.toLocalDate())

        val isBoarded = train.actualBoardingTime != null || event.actualStartTime != null
        val isArrived = train.actualArrivalTime != null || event.actualEndTime != null || event.status == EventStatus.COMPLETED

        val actionConfig = when {
            isDepartureLeg && !isArrivalLeg -> {
                if (isBoarded) {
                    StopActionConfig(label = null, isDone = true, onUndo = { onUndoBoardTrain(train, event) })
                } else {
                    StopActionConfig(label = "Boarded", icon = Icons.Default.DirectionsTransit, isDone = false, onAction = { onBoardTrain(train, event) })
                }
            }
            isArrivalLeg && !isDepartureLeg -> {
                if (isArrived) {
                    StopActionConfig(label = null, isDone = true, onUndo = { onUndoArriveTrain(train, event) })
                } else {
                    StopActionConfig(label = "Arrived", icon = Icons.Default.CheckCircle, isDone = false, onAction = { onArriveTrain(train, event) })
                }
            }
            else -> {
                // Single-day train journey
                if (!isBoarded) {
                    StopActionConfig(label = "Boarded", icon = Icons.Default.DirectionsTransit, isDone = false, onAction = { onBoardTrain(train, event) })
                } else if (!isArrived) {
                    StopActionConfig(label = "Arrived", icon = Icons.Default.CheckCircle, isDone = false, onAction = { onArriveTrain(train, event) }, onUndo = { onUndoBoardTrain(train, event) })
                } else {
                    StopActionConfig(label = null, isDone = true, onUndo = { onUndoArriveTrain(train, event) })
                }
            }
        }

        Column(Modifier.padding(bottom = 2.dp)) {
            ItineraryTrainCard(
                train = train,
                borderColor = borderColor,
                borderWidth = borderWidth,
                onClick = onOpen
            )
            if (event.whatWeAreDoing.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = event.whatWeAreDoing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(6.dp))
            StopActions(
                event = event,
                showSkip = false,
                customActionLabel = actionConfig.label,
                customActionIcon = actionConfig.icon,
                isCustomDone = actionConfig.isDone,
                onCustomAction = actionConfig.onAction,
                onCustomUndo = actionConfig.onUndo,
                onEdit = onEdit,
                onDone = onDone,
                onSkip = onSkip,
                onReopen = onReopen
            )
        }
        return
    }

    if (event.type == EventType.STAY) {
        val isCheckedIn = stayDetails?.actualCheckIn != null || event.actualStartTime != null
        val isCheckedOut = stayDetails?.actualCheckOut != null || event.actualEndTime != null || event.status == EventStatus.COMPLETED

        val stayActionConfig = when {
            dayEvent.isCheckIn && !dayEvent.isCheckOut -> {
                if (isCheckedIn) {
                    StopActionConfig(label = null, isDone = true, onUndo = { onUndoCheckInStay(stayDetails, event) })
                } else {
                    StopActionConfig(label = "Check in", icon = Icons.Default.Hotel, isDone = false, onAction = { onCheckInStay(stayDetails, event) })
                }
            }
            dayEvent.isCheckOut && !dayEvent.isCheckIn -> {
                if (isCheckedOut) {
                    StopActionConfig(label = null, isDone = true, onUndo = { onUndoCheckOutStay(stayDetails, event) })
                } else {
                    StopActionConfig(label = "Check out", icon = Icons.Default.CheckCircle, isDone = false, onAction = { onCheckOutStay(stayDetails, event) })
                }
            }
            else -> {
                if (dayEvent.isCheckIn) {
                    if (isCheckedIn) {
                        StopActionConfig(label = null, isDone = true, onUndo = { onUndoCheckInStay(stayDetails, event) })
                    } else {
                        StopActionConfig(label = "Check in", icon = Icons.Default.Hotel, isDone = false, onAction = { onCheckInStay(stayDetails, event) })
                    }
                } else if (dayEvent.isCheckOut) {
                    if (isCheckedOut) {
                        StopActionConfig(label = null, isDone = true, onUndo = { onUndoCheckOutStay(stayDetails, event) })
                    } else {
                        StopActionConfig(label = "Check out", icon = Icons.Default.CheckCircle, isDone = false, onAction = { onCheckOutStay(stayDetails, event) })
                    }
                } else {
                    if (!isCheckedIn) {
                        StopActionConfig(label = "Check in", icon = Icons.Default.Hotel, isDone = false, onAction = { onCheckInStay(stayDetails, event) })
                    } else if (!isCheckedOut) {
                        StopActionConfig(label = "Check out", icon = Icons.Default.CheckCircle, isDone = false, onAction = { onCheckOutStay(stayDetails, event) }, onUndo = { onUndoCheckInStay(stayDetails, event) })
                    } else {
                        StopActionConfig(label = null, isDone = true, onUndo = { onUndoCheckOutStay(stayDetails, event) })
                    }
                }
            }
        }

        Column(Modifier.padding(bottom = 2.dp)) {
            ItineraryStayCard(
                event = event,
                stayDetails = stayDetails,
                place = place,
                isCheckIn = dayEvent.isCheckIn,
                isCheckOut = dayEvent.isCheckOut,
                borderColor = borderColor,
                borderWidth = borderWidth,
                onClick = onOpen
            )

            if (event.whatWeAreDoing.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = event.whatWeAreDoing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(6.dp))
            StopActions(
                event = event,
                showSkip = false,
                customActionLabel = stayActionConfig.label,
                customActionIcon = stayActionConfig.icon,
                isCustomDone = stayActionConfig.isDone,
                onCustomAction = stayActionConfig.onAction,
                onCustomUndo = stayActionConfig.onUndo,
                onEdit = onEdit,
                onDone = onDone,
                onSkip = onSkip,
                onReopen = onReopen
            )
        }
        return
    }

    // Compact Activity Card (Sightseeing, Food, Custom, etc.)
    Column(Modifier.padding(bottom = 2.dp)) {
        ItineraryActivityCard(
            event = event,
            place = place,
            borderColor = borderColor,
            borderWidth = borderWidth,
            onClick = onOpen
        )

        if (event.whatWeAreDoing.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = event.whatWeAreDoing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(6.dp))
        StopActions(
            event = event,
            showSkip = true,
            onEdit = onEdit,
            onDone = onDone,
            onSkip = onSkip,
            onReopen = onReopen
        )
    }
}

/**
 * A clean, travel-first Train Card for the Itinerary timeline.
 * Communicates: Train Name & Number -> Endpoints, Times & Duration -> Booking summary.
 */
@Composable
private fun ItineraryTrainCard(
    train: Train,
    borderColor: Color,
    borderWidth: Dp,
    onClick: () -> Unit
) {
    val colors = AppThemeExtended.colors

    AppCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        onClick = onClick,
        borderColor = borderColor,
        borderWidth = borderWidth,
        contentPadding = PaddingValues(14.dp)
    ) {
        // Header: Train Name (prominent) & Train Number
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = trainDisplayName(train),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = train.number,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Station & Schedule Row: 15:25 Kazipet Jn → 04:25 Agra Cantt
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = DateTimeUtils.formatTime(train.departureTime),
                    style = AppThemeExtended.text.timeLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = train.originName.ifBlank { train.originCode },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "to",
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = DateTimeUtils.formatDuration(train.departureTime, train.arrivalTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textFaint
                )
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = DateTimeUtils.formatTime(train.arrivalTime),
                    style = AppThemeExtended.text.timeLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = train.destinationName.ifBlank { train.destinationCode },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Booking Summary: SL · Coach S3 · Berths 56, 16
        val summary = train.bookingSummary()
        if (summary != null) {
            Spacer(Modifier.height(10.dp))
            AppDivider()
            Spacer(Modifier.height(8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A clean, neutral Hotel/Stay Card for the Itinerary timeline.
 * Communicates: Hotel Name & Location -> Event-Specific Check-in/Check-out Info.
 */
@Composable
private fun ItineraryStayCard(
    event: Event,
    stayDetails: StayDetails?,
    place: Location?,
    isCheckIn: Boolean,
    isCheckOut: Boolean,
    borderColor: Color,
    borderWidth: Dp,
    onClick: () -> Unit
) {
    val colors = AppThemeExtended.colors
    val address = stayAddress(stayDetails, place) ?: place?.name
    val isSameDay = event.startTime.toLocalDate() == event.endTime.toLocalDate()

    AppCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        onClick = onClick,
        borderColor = borderColor,
        borderWidth = borderWidth,
        contentPadding = PaddingValues(14.dp)
    ) {
        // Hotel Name
        Text(
            text = event.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Location / Address
        if (address != null) {
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = colors.stay,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Reservation Information container
        Surface(
            shape = AppThemeExtended.metrics.controlShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (isCheckIn && !isCheckOut) {
                    // Check-in event: highlight Check-in time, keep check-out secondary
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Eyebrow("Check-in", color = colors.stay)
                            Text(
                                text = if (isSameDay) DateTimeUtils.formatTime(event.startTime) else "${DateTimeUtils.formatShortDate(event.startTime.toLocalDate())}, ${DateTimeUtils.formatTime(event.startTime)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Eyebrow("Check-out")
                            Text(
                                text = if (isSameDay) DateTimeUtils.formatTime(event.endTime) else "${DateTimeUtils.formatShortDate(event.endTime.toLocalDate())}, ${DateTimeUtils.formatTime(event.endTime)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (isCheckOut && !isCheckIn) {
                    // Check-out event: show stay summary
                    val nights = stayNights(event)
                    val stayDurationText = if (isSameDay) {
                        "Stayed ${DateTimeUtils.formatTime(event.startTime)} → ${DateTimeUtils.formatTime(event.endTime)}"
                    } else {
                        "Stayed ${DateTimeUtils.formatShortDate(event.startTime.toLocalDate())} → ${DateTimeUtils.formatShortDate(event.endTime.toLocalDate())} (${stayNightsLabel(nights)})"
                    }
                    Text(
                        text = stayDurationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    // Default / fallback
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Eyebrow("Check-in")
                            Text(
                                text = DateTimeUtils.formatTime(event.startTime),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Eyebrow("Check-out")
                            Text(
                                text = DateTimeUtils.formatTime(event.endTime),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A clean, neutral Activity Card for the Itinerary timeline.
 */
@Composable
private fun ItineraryActivityCard(
    event: Event,
    place: Location?,
    borderColor: Color,
    borderWidth: Dp,
    onClick: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        onClick = onClick,
        borderColor = borderColor,
        borderWidth = borderWidth,
        contentPadding = PaddingValues(14.dp)
    ) {
        Column {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateTimeUtils.formatTimeRange(event.startTime, event.endTime),
                    style = AppThemeExtended.text.time,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (place != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "·",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = event.type.color,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Helper data holder for custom button definitions. */
private data class StopActionConfig(
    val label: String? = null,
    val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.CheckCircle,
    val isDone: Boolean = false,
    val onAction: (() -> Unit)? = null,
    val onUndo: (() -> Unit)? = null
)

/**
 * Whether the user has already ruled on this stop, rather than the clock having.
 *
 * Shared with Home, which draws the same decision on its next-up card: a stop the user has marked
 * done says "Done" there too, instead of the booking's own live status contradicting them.
 */
internal val Event.isDecided: Boolean
    get() = status == EventStatus.COMPLETED || status == EventStatus.SKIPPED

/**
 * The primary and secondary actions for a stop.
 *
 * Trains and hotel check-ins omit the Skip action because missing or skipping a train/hotel is
 * fundamentally different from skipping a flexible sightseeing activity.
 */
@Composable
private fun StopActions(
    event: Event,
    showSkip: Boolean = true,
    customActionLabel: String? = null,
    customActionIcon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.CheckCircle,
    isCustomDone: Boolean = false,
    onCustomAction: (() -> Unit)? = null,
    onCustomUndo: (() -> Unit)? = null,
    onEdit: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onReopen: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCustomDone) {
            SecondaryButton(
                text = "Reopen",
                onClick = { onCustomUndo?.invoke() ?: onReopen() },
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.Undo
            )
        } else if (event.isDecided) {
            SecondaryButton(
                text = "Reopen",
                onClick = onReopen,
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.Undo
            )
        } else if (customActionLabel != null && onCustomAction != null) {
            PrimaryButton(
                text = customActionLabel,
                onClick = onCustomAction,
                modifier = Modifier.weight(1f),
                icon = customActionIcon
            )
            if (onCustomUndo != null) {
                SecondaryButton(
                    text = "Undo",
                    onClick = onCustomUndo,
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.Undo
                )
            }
        } else if (!showSkip) {
            // Fixed events (trains, check-ins) only have Done
            PrimaryButton(
                text = "Done",
                onClick = onDone,
                modifier = Modifier.weight(1f),
                icon = Icons.Default.CheckCircle
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
