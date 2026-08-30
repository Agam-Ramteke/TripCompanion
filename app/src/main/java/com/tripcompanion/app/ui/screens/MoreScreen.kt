package com.tripcompanion.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.feature.more.MoreViewModel
import com.tripcompanion.app.feature.place.PlaceTab
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.SettingsGroup
import com.tripcompanion.app.ui.components.SettingsRow
import com.tripcompanion.app.ui.components.SettingsRowDivider
import com.tripcompanion.app.ui.theme.AppThemeExtended
import com.tripcompanion.app.ui.theme.staggeredEntrance

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.tripcompanion.app.ui.components.AppIconButton

/**
 * The rest of the app, with the size of each thing on the way in.
 *
 * A hub is usually five words and five chevrons, which tells you nothing you didn't already
 * know from the words. Every row here carries its own count, so the screen answers "is there
 * anything in there" before you tap — and the Stay tile can admit there is no booking rather
 * than leading somewhere empty.
 */
@Composable
fun MoreScreen(
    onNavigateToMap: (Long) -> Unit,
    onNavigateToHotel: (Long) -> Unit,
    onNavigateToPlaces: (PlaceTab) -> Unit,
    onNavigateToItinerary: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    val colors = AppThemeExtended.colors
    val trip = state.trip

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = metrics.screenPadding,
            end = metrics.screenPadding,
            top = 16.dp,
            bottom = metrics.navHeight + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap)
    ) {
        item("header") {
            Column {
                Text(
                    text = "More",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = trip?.let {
                        it.name + " · " + DateTimeUtils.formatDateRange(it.startDate, it.endDate)
                    } ?: "No trip yet. Create one from Trips and the rest fills in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // The two trip-scoped destinations. Without a trip they would both open a screen whose
        // only content is its own empty state, so they are not offered at all.
        if (trip != null) {
            item("trip-destinations") {
                Column(modifier = Modifier.staggeredEntrance(1)) {
                    Text(
                        text = "This trip",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
                        DestinationTile(
                            icon = Icons.Default.Map,
                            title = "Trip map",
                            detail = mappedStopsLabel(state.mappedStopCount),
                            accent = colors.destination,
                            accentSoft = colors.destinationSoft,
                            onClick = { onNavigateToMap(trip.id) },
                            modifier = Modifier.weight(1f)
                        )
                        DestinationTile(
                            icon = Icons.Default.Hotel,
                            title = "Stay",
                            detail = staysLabel(state.stayCount),
                            accent = colors.stay,
                            accentSoft = colors.staySoft,
                            onClick = { onNavigateToHotel(trip.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item("places") {
            SettingsGroup(
                title = "Places",
                modifier = Modifier.staggeredEntrance(if (trip != null) 2 else 1)
            ) {
                SettingsRow(
                    icon = Icons.Default.Place,
                    label = "To visit",
                    description = "Everywhere still on the list",
                    value = state.toVisitCount.toString(),
                    iconTint = colors.visit,
                    iconBackground = colors.visitSoft,
                    onClick = { onNavigateToPlaces(PlaceTab.TO_VISIT) }
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.CheckCircle,
                    label = "Visited",
                    description = "Marked off along the way",
                    value = state.visitedCount.toString(),
                    iconTint = colors.success,
                    iconBackground = colors.successSoft,
                    onClick = { onNavigateToPlaces(PlaceTab.VISITED) }
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Bookmark,
                    label = "Saved",
                    description = "Bookmarked for another time",
                    value = state.savedCount.toString(),
                    iconTint = colors.accent,
                    iconBackground = colors.accentSoft,
                    onClick = { onNavigateToPlaces(PlaceTab.SAVED) }
                )
            }
        }

        item("app") {
            SettingsGroup(
                title = "App",
                modifier = Modifier.staggeredEntrance(if (trip != null) 3 else 2)
            ) {
                if (trip != null) {
                    SettingsRow(
                        icon = Icons.Default.PhotoCamera,
                        label = "Photo plans",
                        // Said plainly, because this row does not open a gallery — the plans
                        // belong to the activities they were made for, and that is where it goes.
                        description = "On the activities they belong to",
                        value = state.photoPlanCount.toString(),
                        iconTint = colors.custom,
                        iconBackground = colors.customSoft,
                        onClick = { onNavigateToItinerary(trip.id) }
                    )
                    SettingsRowDivider()
                }
                SettingsRow(
                    icon = Icons.Default.Settings,
                    label = "Settings",
                    description = "Appearance, preferences, about",
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconBackground = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = onNavigateToSettings
                )
            }
        }
    }
}

/**
 * One large destination: a tinted icon, where it goes, and how much is in there.
 *
 * Bigger than a quick action because these two are places you go rather than things you do,
 * and the count under the title is the reason the tile is worth its space.
 */
@Composable
private fun DestinationTile(
    icon: ImageVector,
    title: String,
    detail: String,
    accent: Color,
    accentSoft: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = accentSoft) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(9.dp)
                    .size(22.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "mapped" is the honest word: an activity with no place attached is not one of these. */
private fun mappedStopsLabel(count: Int): String = when (count) {
    0 -> "Nothing mapped yet"
    1 -> "1 stop mapped"
    else -> "$count stops mapped"
}

private fun staysLabel(count: Int): String = when (count) {
    0 -> "No stay added yet"
    1 -> "1 booking"
    else -> "$count bookings"
}
