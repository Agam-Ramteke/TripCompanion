package com.tripcompanion.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.PlannedPhoto
import com.tripcompanion.app.feature.event.EventDetailViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.HeroImage
import com.tripcompanion.app.ui.components.PhotoCard
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.SectionHeader
import com.tripcompanion.app.ui.components.StatusBadge
import com.tripcompanion.app.ui.components.colorIn
import com.tripcompanion.app.ui.components.icon
import com.tripcompanion.app.ui.components.label
import com.tripcompanion.app.ui.components.softColorIn
import com.tripcompanion.app.ui.theme.AppThemeExtended
import java.time.LocalDateTime

/**
 * One activity, in the order you need it: where you'll be, when, and what you're doing there.
 *
 * The screen opens on the place's photograph when there is one, because a picture answers
 * "which of the three temples was this?" faster than a name does. Everything under it is a
 * card, and "What we're doing" gets the accent surface — §14 is explicit that it is the point
 * of the record rather than a field on a form.
 *
 * Previous and Next at the bottom make this a position in the itinerary rather than a dead end
 * you have to back out of, walking the trip in its one canonical order (§10).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEventEditor: (Long, Long) -> Unit,
    onNavigateToEvent: (Long) -> Unit,
    onNavigateToPhotoEditor: (Long, Long?) -> Unit,
    onNavigateToPlace: (Long) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    val colors = AppThemeExtended.colors
    var inspectedPhoto by remember { mutableStateOf<PlannedPhoto?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.event?.title ?: "Activity",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    state.event?.let { event ->
                        IconButton(onClick = { onNavigateToEventEditor(event.tripId, event.id) }) {
                            Icon(Icons.Default.Edit, "Edit activity")
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, "Delete activity")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val event = state.event

        when {
            state.isLoading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.accent)
            }

            // Not a spinner that never stops: a deleted activity, or a stale deep link,
            // lands here and can still get out.
            event == null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.EventBusy,
                    title = "That activity isn't here",
                    message = "It may have been deleted, or the link that opened this " +
                        "screen has gone stale.",
                    actionLabel = "Go back",
                    onAction = onNavigateBack
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(metrics.screenPadding),
                verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
            ) {
                // The place's own photograph, carrying the two facts that frame everything
                // below it. Without a photo those two facts stand on their own row instead —
                // a grey rectangle would say less than nothing here.
                val photo = state.location?.photoUri
                item {
                    if (!photo.isNullOrBlank()) {
                        HeroImage(
                            uri = photo,
                            contentDescription = state.location?.name,
                            height = 190.dp,
                            shape = metrics.cardShapeLarge
                        ) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusBadge(state.computedStatus, filled = true)
                                Spacer(Modifier.width(10.dp))
                                Icon(
                                    event.type.icon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = event.type.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadge(state.computedStatus)
                            TypeChip(event.type)
                        }
                    }
                }

                // When. The day on one line, the range and how long it runs beneath it, and —
                // while the event still has a future — how far away or how far through it is.
                item {
                    WhenCard(
                        day = DateTimeUtils.formatDayAndDate(event.startTime.toLocalDate()),
                        timeRange = DateTimeUtils.formatTimeRange(event.startTime, event.endTime),
                        duration = DateTimeUtils.formatDuration(event.startTime, event.endTime),
                        relative = relativeTimeLabel(state.computedStatus, event, state.now)
                    )
                }

                // Where.
                state.location?.let { location ->
                    item {
                        AppCard(onClick = { onNavigateToPlace(location.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = metrics.badgeShape,
                                    color = colors.destinationSoft,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Place,
                                            contentDescription = null,
                                            tint = colors.destination,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = location.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (location.address.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = location.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Open this place",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // What we're doing (§14) — the field the spec singles out as the point of the
                // whole record, so it gets the accent surface and the largest body type. Blank
                // is worth an invitation rather than silence: it is the line read on the day.
                item {
                    Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))
                    SectionHeader(title = "What we're doing")
                }
                item {
                    if (event.whatWeAreDoing.isNotBlank()) {
                        AppCard(color = colors.accentSoft, borderWidth = 0.dp) {
                            Text(
                                text = event.whatWeAreDoing,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        AppCard(
                            onClick = { onNavigateToEventEditor(event.tripId, event.id) }
                        ) {
                            Text(
                                text = "Nothing written yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "Write what you'll actually do here — the sentence " +
                                    "you'll want on the day.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Photo plan (§15). The images are the content; a caption is optional and
                // there is deliberately nowhere here for shot specifications.
                item {
                    Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))
                    SectionHeader(
                        title = "Photo plan",
                        subtitle = "Shots to look for while you're there.",
                        actionLabel = "Add",
                        onActionClick = { onNavigateToPhotoEditor(event.id, null) }
                    )
                }

                if (state.plannedPhotos.isEmpty()) {
                    item {
                        AppCard(onClick = { onNavigateToPhotoEditor(event.id, null) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Save a reference photo for this activity",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else {
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(metrics.rowGap),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(state.plannedPhotos, key = { it.id }) { plannedPhoto ->
                                PhotoCard(
                                    uri = plannedPhoto.referenceImageUri,
                                    // §15: a photo with no words at all is a valid plan, so an
                                    // untitled tile shows the image and nothing under it.
                                    caption = plannedPhoto.title.takeIf { it.isNotBlank() },
                                    width = 132.dp,
                                    imageHeight = 160.dp,
                                    placeholderIcon = Icons.Default.AddPhotoAlternate,
                                    onClick = { inspectedPhoto = plannedPhoto }
                                )
                            }
                        }
                    }
                }

                if (event.notes.isNotBlank()) {
                    item {
                        Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))
                        SectionHeader(title = "Notes")
                    }
                    item {
                        AppCard {
                            Text(
                                text = event.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Done / Skip. Both write a terminal status the engine will not recompute,
                // which is why they only appear while one is still possible.
                item {
                    Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))
                    if (state.computedStatus != EventStatus.COMPLETED &&
                        state.computedStatus != EventStatus.SKIPPED
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)
                        ) {
                            PrimaryButton(
                                text = "Done",
                                onClick = viewModel::completeEvent,
                                icon = Icons.Default.Check,
                                // Green rather than the app's blue: "done" is the same fact
                                // here as it is on the timeline and on Home's progress bar.
                                fill = colors.success,
                                contentColor = colors.onSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            SecondaryButton(
                                text = "Skip",
                                onClick = viewModel::skipEvent,
                                icon = Icons.Default.FastForward,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        SecondaryButton(
                            text = "Reopen this activity",
                            onClick = viewModel::reopenEvent,
                            icon = Icons.AutoMirrored.Filled.Undo
                        )
                    }
                }

                if (state.previousEventId != null || state.nextEventId != null) {
                    item {
                        Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)
                        ) {
                            NeighbourButton(
                                label = "Previous",
                                icon = Icons.Default.ChevronLeft,
                                iconFirst = true,
                                targetId = state.previousEventId,
                                onNavigate = onNavigateToEvent,
                                modifier = Modifier.weight(1f)
                            )
                            NeighbourButton(
                                label = "Next",
                                icon = Icons.Default.ChevronRight,
                                iconFirst = false,
                                targetId = state.nextEventId,
                                onNavigate = onNavigateToEvent,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(metrics.sectionGap)) }
            }
        }
    }

    inspectedPhoto?.let { photo ->
        PhotoLightbox(
            photo = photo,
            onDismiss = { inspectedPhoto = null },
            onEdit = {
                val photoId = photo.id
                inspectedPhoto = null
                state.event?.let { onNavigateToPhotoEditor(it.id, photoId) }
            },
            onDelete = {
                viewModel.deletePhoto(photo.id)
                inspectedPhoto = null
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this activity?") },
            text = { Text("This will remove it from your itinerary. You cannot undo this.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteEvent(onNavigateBack)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Keep it")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The kind of activity, wearing its own colour.
 *
 * The same colour the timeline node, the map pin and the editor's chip use, so "purple means
 * food" is learned once and holds everywhere.
 */
@Composable
private fun TypeChip(type: EventType) {
    val colors = AppThemeExtended.colors

    Surface(
        shape = AppThemeExtended.metrics.chipShape,
        color = type.softColorIn(colors)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(
                type.icon,
                contentDescription = null,
                tint = type.colorIn(colors),
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = type.label,
                style = MaterialTheme.typography.labelMedium,
                color = type.colorIn(colors)
            )
        }
    }
}

/**
 * When it happens, with the countdown under a divider.
 *
 * The date and the clock get their own lines because they answer different questions — which
 * day am I looking at, and do I need to leave now — and the countdown is separated from both
 * because it is the only line here that changes while you read it.
 */
@Composable
private fun WhenCard(
    day: String,
    timeRange: String,
    duration: String,
    relative: String?
) {
    val colors = AppThemeExtended.colors
    val metrics = AppThemeExtended.metrics

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = metrics.badgeShape,
                color = colors.accentSoft,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = day,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeRange,
                        style = AppThemeExtended.text.time,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textFaint
                    )
                }
            }
        }

        if (relative != null) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                thickness = metrics.dividerWidth,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relative,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.accent
                )
            }
        }
    }
}

/**
 * The countdown line: how long until it starts, or how much of it is left.
 *
 * Terminal statuses return null. "3 hours ago" adds nothing the Done badge has not already
 * said, and a countdown against a decided event invites the reader to wonder whether it is
 * still going to happen.
 */
private fun relativeTimeLabel(
    status: EventStatus,
    event: Event,
    now: LocalDateTime
): String? = when (status) {
    EventStatus.UPCOMING, EventStatus.STARTING_SOON ->
        DateTimeUtils.formatTimeUntil(now, event.startTime)
    EventStatus.ACTIVE ->
        DateTimeUtils.formatTimeRemaining(now, event.endTime)
    EventStatus.COMPLETED, EventStatus.SKIPPED, EventStatus.MISSED -> null
}

/**
 * One step through the itinerary. Disabled rather than hidden at either end, so the pair
 * keeps its shape and the first activity does not look like it has a different screen from
 * the second.
 *
 * Drawn like [SecondaryButton] rather than with it, because the chevron has to trail the
 * label on "Next" and lead it on "Previous" — the direction is the button's whole point.
 */
@Composable
private fun NeighbourButton(
    label: String,
    icon: ImageVector,
    iconFirst: Boolean,
    targetId: Long?,
    onNavigate: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = AppThemeExtended.metrics
    val enabled = targetId != null
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = metrics.buttonShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            metrics.borderWidth,
            if (enabled) {
                MaterialTheme.colorScheme.outline
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .height(metrics.controlHeight)
            .then(
                if (enabled) {
                    Modifier.clickable { targetId?.let(onNavigate) }
                } else {
                    Modifier
                }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (iconFirst) {
                    Icon(icon, null, Modifier.size(18.dp), tint = contentColor)
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
                } else {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
                    Spacer(Modifier.width(6.dp))
                    Icon(icon, null, Modifier.size(18.dp), tint = contentColor)
                }
            }
        }
    }
}

/**
 * Full-size look at one reference photo. Image first and largest; the title, if there is one,
 * sits under it as a caption rather than as a heading above it.
 */
@Composable
private fun PhotoLightbox(
    photo: PlannedPhoto,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val metrics = AppThemeExtended.metrics

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = metrics.cardShapeLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(metrics.borderWidth, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (!photo.referenceImageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(photo.referenceImageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = photo.title.ifBlank { "Reference photo" },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Column(Modifier.padding(metrics.cardPadding)) {
                    if (photo.title.isNotBlank()) {
                        Text(
                            text = photo.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(metrics.rowGap))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)
                    ) {
                        SecondaryButton(
                            text = "Delete",
                            onClick = onDelete,
                            icon = Icons.Default.Delete,
                            contentColor = AppThemeExtended.colors.danger,
                            borderColor = AppThemeExtended.colors.danger,
                            modifier = Modifier.weight(1f)
                        )
                        PrimaryButton(
                            text = "Edit",
                            onClick = onEdit,
                            icon = Icons.Default.Edit,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
