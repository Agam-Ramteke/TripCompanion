package com.tripcompanion.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.feature.place.NearbyPlace
import com.tripcompanion.app.feature.place.PlaceDetailViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppDivider
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.BadgeTone
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.HeroImage
import com.tripcompanion.app.ui.components.MetaRow
import com.tripcompanion.app.ui.components.PhotoCard
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.SectionHeader
import com.tripcompanion.app.ui.components.StatusBadge
import com.tripcompanion.app.ui.components.TextActionButton
import com.tripcompanion.app.ui.theme.AppThemeExtended

/**
 * One place, and everything the rest of the database already knows about it.
 *
 * "Nearby" is the user's own list sorted by distance rather than a search result: the places
 * worth suggesting next to this one are ones they already chose, and a provider would answer
 * with restaurants they have never heard of while they are offline on a hill.
 */
@Composable
fun PlaceDetailScreen(
    onNavigateBack: () -> Unit,
    onAddToItinerary: (Long, Long) -> Unit,
    onNavigateToEvent: (Long) -> Unit,
    onNavigateToPlace: (Long) -> Unit,
    onNavigateToNewTrip: () -> Unit,
    viewModel: PlaceDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    val context = LocalContext.current

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

    val place = state.place
    if (place == null) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = metrics.screenPadding),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                icon = Icons.Default.Place,
                title = "This place is gone",
                message = "It was deleted, or the link that brought you here is stale.",
                actionLabel = "Go back",
                onAction = onNavigateBack
            )
        }
        return
    }

    // No horizontal padding on the list: the hero runs edge to edge, so every other item
    // insets itself.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item("hero") {
            HeroImage(
                uri = place.photoUri,
                contentDescription = place.name,
                placeholderIcon = Icons.Default.Place
            ) {
                AppIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(metrics.screenPadding),
                    tint = MaterialTheme.colorScheme.onSurface,
                    background = MaterialTheme.colorScheme.surface,
                    borderColor = null,
                    size = 44.dp
                )
                AppIconButton(
                    icon = if (place.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (place.isSaved) "Remove bookmark" else "Save this place",
                    onClick = viewModel::toggleSaved,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(metrics.screenPadding),
                    tint = if (place.isSaved) {
                        AppThemeExtended.colors.accent
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    background = MaterialTheme.colorScheme.surface,
                    borderColor = null,
                    size = 44.dp
                )
            }
        }

        item("title") {
            Inset {
                Column {
                    if (place.category.isNotBlank()) {
                        Text(
                            text = place.category,
                            style = AppThemeExtended.text.eyebrow,
                            color = AppThemeExtended.colors.accent
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (place.isVisited) {
                        Spacer(Modifier.height(8.dp))
                        StatusBadge(label = "Visited", tone = BadgeTone.POSITIVE, uppercase = false)
                    }
                }
            }
        }

        item("facts") {
            Inset {
                FactsRow(place)
            }
        }

        item("actions") {
            Inset {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val tripId = state.defaultTripId
                    if (tripId != null) {
                        PrimaryButton(
                            text = if (state.isOnItinerary) "Add another visit" else "Add to itinerary",
                            onClick = { onAddToItinerary(tripId, place.id) },
                            icon = Icons.Default.Add
                        )
                    } else {
                        // A place saved before any trip exists is the normal way people plan,
                        // so the button explains itself rather than disappearing.
                        AppCard {
                            Text(
                                text = "No trip to add this to yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Create a trip and this place can go straight onto a day.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            SecondaryButton(
                                text = "Plan a trip",
                                onClick = onNavigateToNewTrip,
                                icon = Icons.Default.Luggage
                            )
                        }
                    }
                    if (state.hasPosition) {
                        SecondaryButton(
                            text = "Directions",
                            onClick = { openDirections(context, place) },
                            icon = Icons.Default.Directions
                        )
                    }
                    TextActionButton(
                        text = if (place.isVisited) "Not been there after all" else "Been there",
                        onClick = viewModel::toggleVisited,
                        icon = if (place.isVisited) {
                            Icons.AutoMirrored.Filled.Undo
                        } else {
                            Icons.Default.CheckCircleOutline
                        }
                    )
                }
            }
        }

        if (state.scheduledEvents.isNotEmpty()) {
            item("when-header") {
                Inset {
                    SectionHeader(
                        title = "When you're going",
                        subtitle = "From your itinerary."
                    )
                }
            }
            items(state.scheduledEvents, key = { "event-${it.id}" }) { event ->
                Inset {
                    ScheduledRow(event = event, onClick = { onNavigateToEvent(event.id) })
                }
            }
        }

        if (state.photoIdeas.isNotEmpty()) {
            item("photos-header") {
                Inset {
                    SectionHeader(
                        title = "Photo ideas",
                        subtitle = "The shots you planned for the visits above."
                    )
                }
            }
            item("photos") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = metrics.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.photoIdeas, key = { it.id }) { idea ->
                        PhotoCard(
                            uri = idea.referenceImageUri,
                            caption = idea.title.takeIf { it.isNotBlank() },
                            placeholderIcon = Icons.Default.PhotoCamera
                        )
                    }
                }
            }
        }

        if (place.address.isNotBlank() || place.openingHours.isNotBlank()) {
            item("about") {
                Inset {
                    AppCard {
                        Text(
                            text = "About",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (place.address.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            MetaRow(
                                label = "Address",
                                value = place.address,
                                icon = Icons.Default.Place
                            )
                        }
                        if (place.openingHours.isNotBlank()) {
                            if (place.address.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                AppDivider()
                                Spacer(Modifier.height(4.dp))
                            }
                            MetaRow(
                                label = "Hours",
                                value = place.openingHours,
                                icon = Icons.Default.AccessTime
                            )
                        }
                    }
                }
            }
        }

        if (state.nearby.isNotEmpty()) {
            item("nearby-header") {
                Inset {
                    SectionHeader(
                        title = "Near here",
                        subtitle = "Other places on your own list, closest first."
                    )
                }
            }
            items(state.nearby, key = { "nearby-${it.place.id}" }) { neighbour ->
                Inset {
                    NearbyRow(
                        neighbour = neighbour,
                        onClick = { onNavigateToPlace(neighbour.place.id) }
                    )
                }
            }
        }
    }
}

/** The horizontal inset every item except the hero shares. */
@Composable
private fun Inset(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = AppThemeExtended.metrics.screenPadding)) { content() }
}

/**
 * Rating, hours and how long a visit takes — only the ones that exist.
 *
 * A place added by hand has none of these, and three rows of dashes would say less than
 * showing nothing at all.
 */
@Composable
private fun FactsRow(place: Location) {
    val facts = buildList {
        place.rating?.let { add(Icons.Default.Star to String.format("%.1f", it)) }
        place.estimatedVisitMinutes?.let {
            add(Icons.Default.HourglassEmpty to DateTimeUtils.formatMinutes(it.toLong()))
        }
        place.openingHours.takeIf { it.isNotBlank() }?.let { add(Icons.Default.AccessTime to it) }
    }
    if (facts.isEmpty()) return
    AppCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            facts.forEach { (icon, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = AppThemeExtended.colors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** One itinerary entry pointing here: when, what, and where it stands. */
@Composable
private fun ScheduledRow(event: Event, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = DateTimeUtils.formatDayAndDate(event.startTime.toLocalDate()) +
                        " · " + DateTimeUtils.formatTime(event.startTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(10.dp))
            StatusBadge(status = event.status)
        }
    }
}

/** A neighbour with the walk already measured. */
@Composable
private fun NearbyRow(neighbour: NearbyPlace, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.NearMe,
                contentDescription = null,
                tint = AppThemeExtended.colors.destination,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = neighbour.place.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (neighbour.place.category.isNotBlank()) {
                    Text(
                        text = neighbour.place.category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = neighbour.distanceLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Hands the position to whatever maps app the device has.
 *
 * A `geo:` URI with a `q` label is understood by every Android maps app, and the in-app map
 * is for planning rather than for walking — turn-by-turn navigation is explicitly out of
 * scope, so this defers to a tool that does it properly.
 */
private fun openDirections(context: android.content.Context, place: Location) {
    val label = Uri.encode(place.name.ifBlank { "Destination" })
    val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}($label)")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "No maps app to open this in", Toast.LENGTH_SHORT).show()
    }
}
