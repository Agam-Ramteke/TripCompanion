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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.engine.TripPhase
import com.tripcompanion.app.domain.engine.TripStats
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.feature.trip.HomeUiState
import com.tripcompanion.app.feature.trip.HomeViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppFilterChip
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.AppMediaCard
import com.tripcompanion.app.ui.components.AppProgressBar
import com.tripcompanion.app.ui.components.BadgeTone
import com.tripcompanion.app.ui.components.bookingSummary
import com.tripcompanion.app.ui.components.CountdownBadge
import com.tripcompanion.app.ui.components.DayPlanStrip
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.FilterChipRow
import com.tripcompanion.app.ui.components.HeroImage
import com.tripcompanion.app.ui.components.PlanStepItem
import com.tripcompanion.app.ui.components.PhotoBackdrop
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.QuickActionCard
import com.tripcompanion.app.ui.components.SectionHeader
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.StatCard
import com.tripcompanion.app.ui.components.StatusBadge
import com.tripcompanion.app.ui.components.TrainCard
import com.tripcompanion.app.ui.components.color
import com.tripcompanion.app.ui.components.icon
import com.tripcompanion.app.ui.components.label
import com.tripcompanion.app.ui.components.softColor
import com.tripcompanion.app.ui.components.statusLabel
import com.tripcompanion.app.ui.components.statusTone
import com.tripcompanion.app.ui.theme.AppThemeExtended
import java.time.LocalDateTime
import kotlin.math.roundToInt
import java.time.temporal.ChronoUnit

/**
 * The trip command centre.
 *
 * Everything here comes from [HomeUiState], which resolves to one instant — so the countdown,
 * the plan strip's current marker and the four stat cards all describe the same minute. The
 * screen changes shape with the trip's phase (§29) rather than branching on anything about a
 * particular trip (§4): a trip that has not started shows what is coming, one in progress
 * shows what is happening, and a finished one shows what happened.
 */
@Composable
fun HomeScreen(
    onNavigateToItinerary: (Long) -> Unit,
    onNavigateToEventDetail: (Long) -> Unit,
    onNavigateToAddEvent: (Long) -> Unit,
    onNavigateToNewTrip: () -> Unit,
    onNavigateToTrips: () -> Unit,
    onNavigateToPlaces: () -> Unit,
    onNavigateToTrains: () -> Unit,
    onNavigateToTrainDetail: (Long) -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToHotel: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics

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
        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap)
    ) {
        item("greeting") {
            GreetingHeader(
                name = state.travellerName,
                now = state.now,
                onSettingsClick = onNavigateToSettings
            )
        }

        val trip = state.trip
        if (trip == null) {
            item("empty") {
                EmptyState(
                    icon = Icons.Default.Luggage,
                    title = "No trips yet",
                    message = "Plan a trip and this screen becomes your command centre: " +
                        "what's next, where you're staying, and how far along you are.",
                    actionLabel = "Plan a trip",
                    onAction = onNavigateToNewTrip,
                    secondaryActionLabel = if (state.isSeeding) "Loading sample trip…" else "Load a sample trip",
                    onSecondaryAction = { if (!state.isSeeding) viewModel.loadSampleTrip() }
                )
            }
            return@LazyColumn
        }

        if (state.trips.size > 1) {
            item("switcher") {
                TripSwitcher(
                    trips = state.trips,
                    selectedId = trip.id,
                    onSelect = viewModel::selectTrip
                )
            }
        }

        item("hero") {
            TripHero(
                trip = trip,
                phase = state.tripState.currentTripState,
                stats = state.stats,
                progressFraction = state.tripState.progressPercent,
                today = state.now,
                onClick = { onNavigateToItinerary(trip.id) }
            )
        }

        val phase = state.tripState.currentTripState
        val focus = state.focusEvent

        if (phase == TripPhase.COMPLETED || phase == TripPhase.CANCELLED) {
            item("wrap") {
                TripWrapUp(
                    stats = state.stats,
                    onOpenItinerary = { onNavigateToItinerary(trip.id) }
                )
            }
        } else if (focus != null) {
            item("nextup") {
                val train = state.focusTrain
                NextUpSection(
                    event = focus,
                    train = train,
                    imageUri = state.nextUpImageUri,
                    isNow = state.tripState.currentEvent != null,
                    place = if (state.tripState.currentEvent != null) state.currentLocation else state.nextLocation,
                    now = state.now,
                    // A ticket-shaped card opens the ticket, the same as on the itinerary.
                    onOpen = {
                        if (train != null) {
                            onNavigateToTrainDetail(train.id)
                        } else {
                            onNavigateToEventDetail(focus.id)
                        }
                    },
                    onDone = { viewModel.completeEvent(focus) },
                    onSkip = { viewModel.skipEvent(focus) }
                )
            }
        } else {
            item("noplan") {
                AppCard {
                    Text(
                        text = "Nothing scheduled yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Add the first thing you're doing and it will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Add an activity",
                        onClick = { onNavigateToAddEvent(trip.id) },
                        icon = Icons.Default.AddCircleOutline
                    )
                }
            }
        }

        // "Your train" is for the journey that is *not* the next thing — the one later today, or
        // the one that got you here. When the next thing is the train, the next-up card is already
        // showing this exact booking and a second copy of it reads as a bug.
        state.upcomingTrain
            ?.takeIf { it.id != state.focusTrain?.id }
            ?.let { train ->
                item("train") {
                    TransitSection(
                        train = train,
                        now = state.now,
                        onClick = { onNavigateToTrainDetail(train.id) }
                    )
                }
            }

        item("plan") {
            TodaysPlanSection(
                events = state.focusedDayEvents,
                dayLabel = dayLabel(trip, state),
                currentEventId = state.tripState.currentEvent?.id,
                onSeeAll = { onNavigateToItinerary(trip.id) },
                onStepClick = { index ->
                    state.focusedDayEvents.getOrNull(index)?.let { onNavigateToEventDetail(it.id) }
                },
                onAdd = { onNavigateToAddEvent(trip.id) }
            )
        }

        item("stats") {
            StatsGrid(
                stats = state.stats,
                onDaysClick = { onNavigateToItinerary(trip.id) },
                onPlacesClick = onNavigateToPlaces,
                onActivitiesClick = { onNavigateToItinerary(trip.id) },
                onPhotosClick = { onNavigateToItinerary(trip.id) }
            )
        }

        item("actions") {
            QuickActionsSection(
                onItinerary = { onNavigateToItinerary(trip.id) },
                onMap = onNavigateToMap,
                onPlaces = onNavigateToPlaces,
                onTrains = onNavigateToTrains,
                onHotel = onNavigateToHotel,
                onTrips = onNavigateToTrips
            )
        }
    }
}

/**
 * The greeting.
 *
 * Uses the name from Settings when there is one and stays impersonal when there isn't —
 * "Good evening, traveller" is the kind of filler that makes an app feel generated.
 */
@Composable
private fun GreetingHeader(
    name: String,
    now: LocalDateTime,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (name.isBlank()) greeting(now) else "${greeting(now)}, $name",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = DateTimeUtils.formatDayAndDate(now.toLocalDate()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppIconButton(
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            onClick = onSettingsClick,
            size = 44.dp
        )
    }
}

private fun greeting(now: LocalDateTime): String = when (now.hour) {
    in 0..4 -> "Good night"
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

/** Switches between trips without leaving Home. Only drawn when there is more than one. */
@Composable
private fun TripSwitcher(
    trips: List<Trip>,
    selectedId: Long,
    onSelect: (Long) -> Unit
) {
    FilterChipRow(contentPadding = PaddingValues(0.dp)) {
        trips.forEach { trip ->
            AppFilterChip(
                label = trip.name,
                selected = trip.id == selectedId,
                onClick = { onSelect(trip.id) }
            )
        }
    }
}

/**
 * The trip, as a photograph.
 *
 * The cover is the user's own picture, copied into app storage — there is no stock imagery in
 * this app. Without one the gradient placeholder carries the same text, so the card never
 * shows a broken-image box or collapses to a different height.
 */
@Composable
private fun TripHero(
    trip: Trip,
    phase: TripPhase,
    stats: TripStats,
    /**
     * How much of the itinerary is done, 0f..1f.
     *
     * A fraction rather than a percentage because that is what the engine computes and what the
     * bar draws; rounding to a whole number happens once, where it is written down.
     */
    progressFraction: Float,
    today: LocalDateTime,
    onClick: () -> Unit
) {
    val metrics = AppThemeExtended.metrics
    AppCard(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        shape = metrics.cardShapeLarge
    ) {
        HeroImage(
            uri = trip.coverImageUri,
            contentDescription = trip.name,
            height = 190.dp,
            placeholderIcon = Icons.Default.Luggage,
            overlay = {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    CountdownBadge(
                        text = phaseLabel(trip, phase, stats, today),
                        icon = Icons.Default.Schedule,
                        tone = phaseTone(phase)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = trip.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = DateTimeUtils.formatDateRange(trip.startDate, trip.endDate),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }
        )
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stats.durationLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(progressFraction * 100).roundToInt()}% done",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            AppProgressBar(fraction = progressFraction)
        }
    }
}

/**
 * What the pill on the hero says.
 *
 * Three phases, three different questions being answered: how long until it starts, how far
 * through it we are, and whether it is over.
 */
private fun phaseLabel(
    trip: Trip,
    phase: TripPhase,
    stats: TripStats,
    today: LocalDateTime
): String = when (phase) {
    TripPhase.NOT_STARTED -> {
        val days = ChronoUnit.DAYS.between(today.toLocalDate(), trip.startDate)
        when {
            days <= 0L -> "Starts today"
            days == 1L -> "Tomorrow"
            else -> "$days days to go"
        }
    }
    TripPhase.IN_PROGRESS ->
        if (stats.totalDays > 0) "Day ${stats.dayNumber} of ${stats.totalDays}" else "In progress"
    TripPhase.COMPLETED -> "Trip complete"
    TripPhase.CANCELLED -> "Cancelled"
}

private fun phaseTone(phase: TripPhase): BadgeTone = when (phase) {
    TripPhase.NOT_STARTED -> BadgeTone.INFO
    TripPhase.IN_PROGRESS -> BadgeTone.POSITIVE
    TripPhase.COMPLETED -> BadgeTone.NEUTRAL
    TripPhase.CANCELLED -> BadgeTone.NEGATIVE
}

/**
 * The one thing that matters right now.
 *
 * "What we're doing" is the prominent line rather than the title (§14): the title is a label and
 * this is the sentence the user wrote to remind themselves what the plan actually was.
 *
 * The card takes the shape of whatever the next thing is. A booked journey is drawn as its ticket —
 * the same card the Trains tab and the itinerary use, so one train cannot look like three different
 * things (§4) — and everything else as its own facts.
 */
@Composable
private fun NextUpSection(
    event: Event,
    /** The booking behind [event], when it is a journey someone has actually booked. */
    train: Train?,
    /** The activity's own photo, faded behind the card. Ignored for trains — see below. */
    imageUri: String?,
    isNow: Boolean,
    place: Location?,
    now: LocalDateTime,
    onOpen: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit
) {
    val metrics = AppThemeExtended.metrics
    Column {
        SectionHeader(
            title = if (isNow) "Happening now" else "Next up",
            // Both helpers word their own preposition — "in 20 m", "40 m left" — so the sentence
            // around them supplies the verb and nothing else. Prefixing "In" or "Ends in" reads
            // back as "In in 20 m".
            subtitle = if (isNow) {
                DateTimeUtils.formatTimeRemaining(now, event.endTime)
                    .replaceFirstChar { it.uppercase() }
            } else {
                "Starts " + DateTimeUtils.formatTimeUntil(now, event.startTime)
            }
        )
        Spacer(Modifier.height(12.dp))
        AppMediaCard(onClick = onOpen) {
            Box {
                // A train keeps a solid card so the ticket reads like a printed ticket; an activity
                // wears its own photo — blurred and faded to a wash its ordinary type sits on.
                if (train == null) {
                    PhotoBackdrop(
                        uri = imageUri,
                        modifier = Modifier.matchParentSize()
                    )
                }
                Column {
                    if (train != null) {
                        TrainCard(
                            trainNumber = train.number,
                            trainName = trainDisplayName(train),
                            originName = train.originName.ifBlank { train.originCode },
                            originTime = DateTimeUtils.formatTime(train.departureTime),
                            destinationName = train.destinationName.ifBlank { train.destinationCode },
                            destinationTime = DateTimeUtils.formatTime(train.arrivalTime),
                            dateLabel = DateTimeUtils.formatShortDate(
                                train.departureTime.toLocalDate()
                            ),
                            duration = DateTimeUtils.formatDuration(
                                train.departureTime,
                                train.arrivalTime
                            ),
                            seatSummary = train.bookingSummary(),
                            // A journey the user has ruled on says so; otherwise the badge is the
                            // booking's own, which is what the Trains tab shows for it.
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
                            // Nested: the container owns the border, padding and elevation, so a
                            // second surface with its own ring would read as a card inside a card —
                            // and a transparent surface that still cast a shadow would show that
                            // shadow through its own fill as a grey slab.
                            color = Color.Transparent,
                            borderWidth = 0.dp,
                            elevation = 0.dp
                        )
                    } else {
                        ActivityFacts(
                            event = event,
                            place = place,
                            modifier = Modifier.padding(
                                start = metrics.cardPadding,
                                end = metrics.cardPadding,
                                top = metrics.cardPadding
                            )
                        )
                    }

                    if (!event.isDecided) {
                        Row(
                            modifier = Modifier.padding(
                                start = metrics.cardPadding,
                                end = metrics.cardPadding,
                                bottom = metrics.cardPadding,
                                top = if (train == null) 16.dp else 0.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrimaryButton(
                                text = "Mark done",
                                onClick = onDone,
                                icon = Icons.Default.CheckCircle,
                                modifier = Modifier.weight(1f)
                            )
                            SecondaryButton(
                                text = "Skip",
                                onClick = onSkip,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Spacer(Modifier.height(if (train == null) metrics.cardPadding else 0.dp))
                    }
                }
            }
        }
    }
}

/** What an unbooked activity is: its kind, its title, its hours and where it happens. */
@Composable
private fun ActivityFacts(
    event: Event,
    place: Location?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = event.type.softColor
            ) {
                Icon(
                    event.type.icon,
                    contentDescription = null,
                    tint = event.type.color,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = event.type.label,
                    style = AppThemeExtended.text.eyebrow,
                    color = event.type.color
                )
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusBadge(status = event.status)
        }

        if (event.whatWeAreDoing.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = event.whatWeAreDoing,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "  " + DateTimeUtils.formatTimeRange(event.startTime, event.endTime),
                style = AppThemeExtended.text.time,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (place != null) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "  ${place.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** After the trip: what got done, rather than a countdown to nothing. */
@Composable
private fun TripWrapUp(stats: TripStats, onOpenItinerary: () -> Unit) {
    Column {
        SectionHeader(
            title = "How it went",
            actionLabel = "Itinerary",
            onActionClick = onOpenItinerary
        )
        Spacer(Modifier.height(12.dp))
        AppCard {
            Text(
                text = "${stats.completedCount} of ${stats.activityCount} planned activities done",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            AppProgressBar(
                fraction = stats.completionFraction,
                color = AppThemeExtended.colors.success
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${stats.placeCount} places visited · ${stats.photoPlanCount} photo plans",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The journey card, entirely from the stored booking — no text is asserted here. */
@Composable
private fun TransitSection(train: Train, now: LocalDateTime, onClick: () -> Unit) {
    Column {
        SectionHeader(title = "Your train")
        Spacer(Modifier.height(12.dp))
        TrainCard(
            trainNumber = train.number,
            trainName = trainDisplayName(train),
            originName = train.originName.ifBlank { train.originCode },
            originTime = DateTimeUtils.formatTime(train.departureTime),
            destinationName = train.destinationName.ifBlank { train.destinationCode },
            destinationTime = DateTimeUtils.formatTime(train.arrivalTime),
            dateLabel = DateTimeUtils.formatShortDate(train.departureTime.toLocalDate()),
            duration = DateTimeUtils.formatDuration(train.departureTime, train.arrivalTime),
            seatSummary = train.bookingSummary(),
            // The shared wording, so this card, the Trains tab and the itinerary cannot describe
            // the same booking three different ways.
            statusLabel = trainStatusLabel(train, now),
            statusTone = trainStatusTone(train, now),
            onClick = onClick
        )
    }
}

/**
 * The day at a glance, as a horizontal strip of its steps.
 *
 * The strip is for scanning shape, not reading detail; tapping a step opens the activity.
 */
@Composable
private fun TodaysPlanSection(
    events: List<Event>,
    dayLabel: String,
    currentEventId: Long?,
    onSeeAll: () -> Unit,
    onStepClick: (Int) -> Unit,
    onAdd: () -> Unit
) {
    Column {
        SectionHeader(
            title = "Plan",
            subtitle = dayLabel,
            actionLabel = if (events.isEmpty()) null else "See all",
            onActionClick = if (events.isEmpty()) null else onSeeAll
        )
        Spacer(Modifier.height(12.dp))
        if (events.isEmpty()) {
            AppCard {
                Text(
                    text = "Nothing planned for this day",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "Add something",
                    onClick = onAdd,
                    icon = Icons.Default.AddCircleOutline
                )
            }
        } else {
            DayPlanStrip(
                steps = events.map { event ->
                    PlanStepItem(
                        icon = event.type.icon,
                        label = event.title,
                        time = DateTimeUtils.formatTime(event.startTime),
                        color = event.type.color,
                        softColor = event.type.softColor,
                        isCurrent = event.id == currentEventId
                    )
                },
                onStepClick = onStepClick
            )
        }
    }
}

/** Which day the strip is showing, said plainly rather than assumed to be today. */
private fun dayLabel(trip: Trip, state: HomeUiState): String {
    val focused = state.focusedDay
    val dayNumber = ChronoUnit.DAYS.between(trip.startDate, focused).toInt() + 1
    val date = DateTimeUtils.formatDayAndDate(focused)
    return when {
        focused == state.now.toLocalDate() -> "Today · $date"
        dayNumber in 1..state.stats.totalDays -> "Day $dayNumber · $date"
        else -> date
    }
}

/** Four figures, all counted from stored rows by [TripStats]. None of them is a constant. */
@Composable
private fun StatsGrid(
    stats: TripStats,
    onDaysClick: () -> Unit,
    onPlacesClick: () -> Unit,
    onActivitiesClick: () -> Unit,
    onPhotosClick: () -> Unit
) {
    val colors = AppThemeExtended.colors
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(
            icon = Icons.Default.CalendarMonth,
            value = stats.totalDays.toString(),
            label = "Days",
            accent = colors.info,
            accentSoft = colors.infoSoft,
            onClick = onDaysClick,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Default.Place,
            value = stats.placeCount.toString(),
            label = "Places",
            accent = colors.destination,
            accentSoft = colors.destinationSoft,
            onClick = onPlacesClick,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Default.CheckCircle,
            value = "${stats.completedCount}/${stats.activityCount}",
            label = "Activities",
            accent = colors.success,
            accentSoft = colors.successSoft,
            onClick = onActivitiesClick,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Default.CameraAlt,
            value = stats.photoPlanCount.toString(),
            label = "Photos",
            accent = colors.warning,
            accentSoft = colors.warningSoft,
            onClick = onPhotosClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionsSection(
    onItinerary: () -> Unit,
    onMap: () -> Unit,
    onPlaces: () -> Unit,
    onTrains: () -> Unit,
    onHotel: () -> Unit,
    onTrips: () -> Unit
) {
    val colors = AppThemeExtended.colors
    Column {
        SectionHeader(title = "Quick actions")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard(
                icon = Icons.Default.Schedule,
                label = "Itinerary",
                onClick = onItinerary,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Default.Map,
                label = "Trip map",
                onClick = onMap,
                accent = colors.destination,
                accentSoft = colors.destinationSoft,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Default.Explore,
                label = "Places",
                onClick = onPlaces,
                accent = colors.success,
                accentSoft = colors.successSoft,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Default.DirectionsTransit,
                label = "Trains",
                onClick = onTrains,
                accent = colors.info,
                accentSoft = colors.infoSoft,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard(
                icon = Icons.Default.Hotel,
                label = "Stay",
                onClick = onHotel,
                accent = colors.custom,
                accentSoft = colors.customSoft,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Default.Luggage,
                label = "All trips",
                onClick = onTrips,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.weight(2f))
        }
    }
}
