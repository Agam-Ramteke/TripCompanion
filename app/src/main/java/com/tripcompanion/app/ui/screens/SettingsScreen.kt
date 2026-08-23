package com.tripcompanion.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.data.transfer.TripArchiveFiles
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.service.TripExportError
import com.tripcompanion.app.domain.service.TripExportOutcome
import com.tripcompanion.app.domain.service.TripImportError
import com.tripcompanion.app.domain.service.TripImportOutcome
import com.tripcompanion.app.feature.settings.SettingsViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.ProfileRow
import com.tripcompanion.app.ui.components.SettingsGroup
import com.tripcompanion.app.ui.components.SettingsRow
import com.tripcompanion.app.ui.components.SettingsRowDivider
import com.tripcompanion.app.ui.components.ThemeOptionCard
import com.tripcompanion.app.ui.theme.AppThemeExtended
import com.tripcompanion.app.ui.theme.AppThemeType
import com.tripcompanion.app.ui.theme.TravelDarkColors
import com.tripcompanion.app.ui.theme.TravelLightColors

/**
 * Settings, where every row is wired to something.
 *
 * The screen this replaces had six rows and six empty `onClick`s — a currency it never used, a
 * notification it never sent, a "Backup & Sync" with nothing to sync. What is here instead is
 * shorter and true: three appearance modes, four preferences that screens actually read, and an
 * About section that reports what is stored rather than reassuring you about it.
 *
 * There is still no Backup & Sync. Cloud sync is Phase 2 (§27), and a switch that syncs nothing
 * is worse than no switch.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    val colors = AppThemeExtended.colors
    val prefs = state.preferences
    val context = LocalContext.current

    var editingProfile by remember { mutableStateOf(false) }

    // `CreateDocument` rather than a share sheet: a trip file is something to keep and then send
    // by whatever the two people already use, and the save dialog is the one place the user gets
    // to say where their own file goes.
    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        // Null is a cancelled dialog. The chosen trip is dropped, not remembered for next time —
        // backing out of the save dialog means backing out of the export.
        if (uri == null) viewModel.cancelExport() else {
            viewModel.exportPickedTrip { TripArchiveFiles.openForWrite(context, uri) }
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.importTrip { TripArchiveFiles.openForRead(context, uri) }
    }

    // The save dialog has to be opened with a file name, and the name comes from the trip — so it
    // can only be reached after the trip has been picked, which is why this waits on state rather
    // than happening in the row's `onClick`.
    LaunchedEffect(state.pendingExport) {
        state.pendingExport?.let { exportPicker.launch(it.fileName) }
    }

    if (editingProfile) {
        ProfileDialog(
            initialName = prefs.travellerName,
            initialHomeCity = prefs.homeCity,
            onDismiss = { editingProfile = false },
            onSave = { name, city ->
                viewModel.setTravellerName(name)
                viewModel.setHomeCity(city)
                editingProfile = false
            }
        )
    }

    if (state.isChoosingTripToExport) {
        TripPickerDialog(
            trips = state.trips,
            onDismiss = viewModel::cancelExport,
            onPick = viewModel::chooseTripToExport
        )
    }

    transferMessage(state.exportOutcome, state.importOutcome)?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissTransferOutcome,
            title = { Text(message.title) },
            text = { Text(message.body) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissTransferOutcome) { Text("Done") }
            }
        )
    }

    state.seededTripId?.let {
        AlertDialog(
            onDismissRequest = viewModel::dismissSeedConfirmation,
            title = { Text("Sample trip added") },
            text = {
                Text(
                    "It's on the Trips list with its days, places and a train. Delete it whenever " +
                        "you like — it's ordinary data."
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSeedConfirmation) { Text("Done") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.screenPadding,
            end = metrics.screenPadding,
            top = 12.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionGap)
    ) {
        item("header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        item("profile") {
            ProfileRow(
                name = prefs.travellerName.ifBlank { "Add your name" },
                subtitle = prefs.homeCity.ifBlank { "Set the city you travel from" },
                actionLabel = "Edit",
                onClick = { editingProfile = true }
            )
        }

        item("appearance") {
            Column {
                GroupTitle("Appearance")
                AppCard(contentPadding = PaddingValues(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeOptionCard(
                            label = "Light",
                            previewBackground = TravelLightColors.Background,
                            previewSurface = TravelLightColors.Surface,
                            previewAccent = TravelLightColors.Primary,
                            selected = state.theme == AppThemeType.LIGHT,
                            onClick = { viewModel.setTheme(AppThemeType.LIGHT) },
                            modifier = Modifier.weight(1f)
                        )
                        ThemeOptionCard(
                            label = "Dark",
                            previewBackground = TravelDarkColors.Background,
                            previewSurface = TravelDarkColors.Surface,
                            previewAccent = TravelDarkColors.Primary,
                            selected = state.theme == AppThemeType.DARK,
                            onClick = { viewModel.setTheme(AppThemeType.DARK) },
                            modifier = Modifier.weight(1f)
                        )
                        // The System swatch previews what the system is set to right now, so
                        // the choice shows its consequence instead of describing it.
                        val systemIsDark = isSystemInDarkTheme()
                        ThemeOptionCard(
                            label = "System",
                            previewBackground = if (systemIsDark) {
                                TravelDarkColors.Background
                            } else {
                                TravelLightColors.Background
                            },
                            previewSurface = if (systemIsDark) {
                                TravelDarkColors.Surface
                            } else {
                                TravelLightColors.Surface
                            },
                            previewAccent = if (systemIsDark) {
                                TravelDarkColors.Primary
                            } else {
                                TravelLightColors.Primary
                            },
                            selected = state.theme == AppThemeType.SYSTEM,
                            onClick = { viewModel.setTheme(AppThemeType.SYSTEM) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "System follows the phone's own light and dark setting, including " +
                            "its schedule.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item("trip-preferences") {
            SettingsGroup(title = "Trip preferences") {
                SettingsRow(
                    icon = Icons.Default.Checklist,
                    label = "Show completed activities",
                    description = "Off hides done and skipped entries on the itinerary",
                    iconTint = colors.success,
                    iconBackground = colors.successSoft,
                    checked = prefs.showCompletedActivities,
                    onCheckedChange = viewModel::setShowCompletedActivities
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Refresh,
                    label = "Refresh live status on its own",
                    description = "Re-checks a running train roughly every two minutes",
                    iconTint = colors.info,
                    iconBackground = colors.infoSoft,
                    checked = prefs.autoRefreshLiveStatus,
                    onCheckedChange = viewModel::setAutoRefreshLiveStatus
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.ScreenLockPortrait,
                    label = "Keep the screen on while tracking",
                    description = "Holds the display awake on the live tracking screen only",
                    iconTint = colors.warning,
                    iconBackground = colors.warningSoft,
                    checked = prefs.keepScreenOnDuringJourney,
                    onCheckedChange = viewModel::setKeepScreenOnDuringJourney
                )
            }
        }

        item("train-data") {
            SettingsGroup(title = "Train data") {
                SettingsRow(
                    icon = if (state.isTrainProviderLive) Icons.Default.Wifi else Icons.Default.Train,
                    label = if (state.isTrainProviderLive) "Live tracking" else "Projected from the schedule",
                    description = state.trainProviderName,
                    iconTint = if (state.isTrainProviderLive) colors.success else colors.warning,
                    iconBackground = if (state.isTrainProviderLive) {
                        colors.successSoft
                    } else {
                        colors.warningSoft
                    },
                    showChevron = false
                )
                if (!state.isTrainProviderLive) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Default.Info,
                        label = "Add an API key for live positions",
                        // Named exactly, because this is the one setting that cannot be changed
                        // from inside the app — it is a build input, not a preference.
                        description = "Put INDIANRAIL_API_KEY in local.properties and rebuild. " +
                            "Until then, running trains are projected from the stored timetable.",
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconBackground = MaterialTheme.colorScheme.surfaceVariant,
                        showChevron = false
                    )
                }
            }
        }

        item("transfer") {
            SettingsGroup(title = "Move a trip to another phone") {
                SettingsRow(
                    icon = Icons.Default.Upload,
                    label = "Export a trip",
                    description = if (state.trips.isEmpty()) {
                        "There is no trip to export yet"
                    } else {
                        "Saves one trip, with its days, bookings and photos, as a single file"
                    },
                    iconTint = colors.accent,
                    iconBackground = colors.accentSoft,
                    onClick = viewModel::beginExport
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Download,
                    label = "Import a trip",
                    description = "Adds the trip in a file as a new trip. Nothing already here changes.",
                    iconTint = colors.info,
                    iconBackground = colors.infoSoft,
                    // Any type, not `application/zip`: a file that has been through a chat app or
                    // an email comes back as `application/octet-stream` as often as not, and a
                    // filter that hides the user's own file is worse than one that hides nothing.
                    // What it really is gets checked when it is read.
                    onClick = { importPicker.launch(arrayOf("*/*")) }
                )
                if (state.isTransferring) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Default.HourglassEmpty,
                        label = "Working on the file",
                        description = "Photos are copied one at a time, so a big trip takes a moment.",
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconBackground = MaterialTheme.colorScheme.surfaceVariant,
                        showChevron = false
                    )
                }
            }
        }

        item("about") {
            SettingsGroup(title = "About") {
                SettingsRow(
                    icon = Icons.Default.Luggage,
                    label = "Trips",
                    value = state.tripCount.toString(),
                    iconTint = colors.accent,
                    iconBackground = colors.accentSoft,
                    showChevron = false
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Place,
                    label = "Places",
                    value = state.placeCount.toString(),
                    iconTint = colors.visit,
                    iconBackground = colors.visitSoft,
                    showChevron = false
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Train,
                    label = "Trains",
                    value = state.trainCount.toString(),
                    iconTint = colors.journey,
                    iconBackground = colors.journeySoft,
                    showChevron = false
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Info,
                    label = "Version",
                    value = state.appVersion,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconBackground = MaterialTheme.colorScheme.surfaceVariant,
                    showChevron = false
                )
            }
        }

        item("sample") {
            AppCard {
                Text(
                    text = "Load the sample trip",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "A five-day trip with its days, places, photo plans and a train, so " +
                        "you can see every screen with something in it. It's ordinary data — " +
                        "edit it or delete it like your own.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    text = "Load the sample trip",
                    onClick = viewModel::loadSampleTrip,
                    busy = state.isSeeding,
                    icon = Icons.Default.Luggage
                )
            }
        }

        // Stored on the device only. Said once, at the bottom, rather than as a row that
        // pretends to be a setting.
        item("storage-note") {
            Text(
                text = "Everything above is stored on this phone. Nothing is uploaded, and " +
                    "the app works with no connection except when a train is being tracked.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/** A group's label, matching [SettingsGroup]'s own so the sections read as one list. */
@Composable
private fun GroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/**
 * Which trip to export.
 *
 * A step of its own because a file holds one trip. Sending someone the whole database to give them
 * one week in Udaipur is not a thing to do quietly, and naming the trip is also what names the
 * file, so the choice has to happen before the save dialog.
 */
@Composable
private fun TripPickerDialog(
    trips: List<Trip>,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which trip?") },
        text = {
            // Scrollable because this list is however many trips the user has, and a dialog that
            // grows past the screen puts the last trip somewhere it cannot be tapped.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "One trip per file, so you can hand someone a single trip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                trips.forEach { trip ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(trip.id) }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = trip.name.ifBlank { "Untitled trip" },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** What the dialog says after a transfer. */
private data class TransferMessage(val title: String, val body: String)

/**
 * The one sentence to show about a finished transfer, or null while none has finished.
 *
 * Counts rather than "Export complete", because a count is checkable by the person who pressed the
 * button: eleven days and three trains is either the trip they meant or it is not. Every failure
 * names what to do next — the four import errors are separate precisely so that this can.
 */
private fun transferMessage(
    export: TripExportOutcome?,
    import: TripImportOutcome?
): TransferMessage? = when {
    export is TripExportOutcome.Written -> TransferMessage(
        title = "Saved “${export.tripName}”",
        body = "${countOf(export.eventCount, "entry", "entries")}, " +
            "${countOf(export.trainCount, "train", "trains")} and " +
            "${countOf(export.imageCount, "photo", "photos")} are in the file. Send it to the " +
            "other phone however you like, then use Import a trip there."
    )

    export is TripExportOutcome.Failed -> when (export.error) {
        TripExportError.TRIP_NOT_FOUND -> TransferMessage(
            title = "That trip is gone",
            body = "It was deleted before the file could be written."
        )

        TripExportError.WRITE_FAILED -> TransferMessage(
            title = "Could not save the file",
            body = "The file could not be written. There may not be enough space left, or the " +
                "place you chose may no longer be available."
        )
    }

    import is TripImportOutcome.Imported -> TransferMessage(
        title = "Added “${import.tripName}”",
        body = "It is on your trips with ${countOf(import.eventCount, "entry", "entries")}, " +
            "${countOf(import.trainCount, "train", "trains")} and " +
            "${countOf(import.imageCount, "photo", "photos")}. Nothing that was already here " +
            "changed."
    )

    import is TripImportOutcome.Failed -> when (import.error) {
        TripImportError.NOT_A_TRIP_FILE -> TransferMessage(
            title = "Not a trip file",
            body = "That file was not exported from this app. Look for the one the other phone " +
                "saved — it ends in .zip and has the trip's name."
        )

        TripImportError.NEWER_VERSION -> TransferMessage(
            title = "Saved by a newer version",
            body = "The phone that made this file has a newer version of the app. Update this " +
                "one and try again — importing it as it is would leave parts of the trip out."
        )

        TripImportError.DAMAGED -> TransferMessage(
            title = "The file is damaged",
            body = "It stops partway through, which usually means the transfer was interrupted. " +
                "Send it again."
        )

        TripImportError.UNREADABLE -> TransferMessage(
            title = "Could not open the file",
            body = "The file could not be read. It may have been moved or deleted since it was " +
                "picked."
        )
    }

    else -> null
}

/** `"1 train"`, `"3 trains"`, `"no photos"` — a count that reads as part of a sentence. */
private fun countOf(count: Int, one: String, many: String): String = when (count) {
    0 -> "no $many"
    1 -> "1 $one"
    else -> "$count $many"
}

/**
 * Name and home city, edited together.
 *
 * Two fields in one dialog rather than two drill-ins: they are both "who is travelling", they
 * are both short, and a two-row dialog is faster than two screens.
 */
@Composable
private fun ProfileDialog(
    initialName: String,
    initialHomeCity: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var city by remember { mutableStateOf(initialHomeCity) }
    val metrics = AppThemeExtended.metrics

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About you") },
        text = {
            Column {
                Text(
                    text = "Your name appears in the greeting on Home and as the passenger on a " +
                        "ticket. The home city is where a journey is measured from.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    shape = metrics.controlShape,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Home city") },
                    singleLine = true,
                    shape = metrics.controlShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), city.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
