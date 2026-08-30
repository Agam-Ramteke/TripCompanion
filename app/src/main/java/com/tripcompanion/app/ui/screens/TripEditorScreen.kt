package com.tripcompanion.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.data.local.ImageStorageHelper
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.feature.trip.TripEditorViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppFilterChip
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.HeroImage
import com.tripcompanion.app.ui.components.PickerField
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.SectionHeader
import com.tripcompanion.app.ui.components.tripStatusLabel
import com.tripcompanion.app.ui.theme.AppThemeExtended
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Create or edit a trip: a cover photo, a name, two dates, and — when editing — a status.
 *
 * Still the shortest editor in the app. A trip is a container; everything that makes it a plan
 * is added on the itinerary afterwards, and asking for more here would put a form between the
 * user and the thing they came to do.
 *
 * The cover comes first because it is the one field that changes how the app looks: it is the
 * photograph behind Home's hero and on the trip's card, so choosing it is the difference
 * between a list of names and a list of places.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: TripEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingStartDate by remember { mutableStateOf(false) }
    var editingEndDate by remember { mutableStateOf(false) }
    var isCopyingCover by remember { mutableStateOf(false) }
    var coverFailed by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isCopyingCover = true
                coverFailed = false
                try {
                    // Copied into app-private storage, so the cover outlives the picker's
                    // temporary permission grant and still renders after a reboot (§16).
                    viewModel.updateCover(ImageStorageHelper.saveImageToInternalStorage(context, uri))
                } catch (_: Exception) {
                    coverFailed = true
                } finally {
                    isCopyingCover = false
                }
            }
        }
    }

    fun pickCover() = coverPicker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )

    LaunchedEffect(state.savedTripId) {
        if (state.savedTripId != null) onNavigateBack()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit trip" else "New trip") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = metrics.screenPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
        ) {
            Spacer(Modifier.height(4.dp))

            CoverPhotoField(
                uri = state.coverImageUri,
                busy = isCopyingCover,
                onPick = { pickCover() },
                onRemove = { viewModel.updateCover(null) }
            )

            if (coverFailed) {
                Text(
                    text = "That image couldn't be copied. Try picking it again, or choose another.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("Trip name") },
                placeholder = { Text("Where are you going?") },
                shape = metrics.controlShape,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            SectionHeader(
                title = "Dates",
                subtitle = "The first and last day you're away."
            )

            Row(horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
                PickerField(
                    label = "First day",
                    value = DateTimeUtils.formatFullDate(state.startDate),
                    icon = Icons.Default.CalendarToday,
                    onClick = { editingStartDate = true },
                    modifier = Modifier.weight(1f)
                )
                PickerField(
                    label = "Last day",
                    value = DateTimeUtils.formatFullDate(state.endDate),
                    icon = Icons.Default.CalendarToday,
                    onClick = { editingEndDate = true },
                    modifier = Modifier.weight(1f)
                )
            }

            if (state.datesAreValid) {
                // The derived length, restated. Two dates are easy to get right one at a
                // time and still add up to the wrong trip.
                LengthSummary(
                    range = DateTimeUtils.formatDateRange(state.startDate, state.endDate),
                    length = tripLengthLabel(state.dayCount, state.nightCount)
                )
            } else {
                DatesInvalidNotice()
            }

            if (state.isEditing) {
                Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))
                SectionHeader(
                    title = "Status",
                    subtitle = "Which list this trip appears under."
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TripStatus.entries.forEach { status ->
                        AppFilterChip(
                            label = tripStatusLabel(status),
                            selected = state.status == status,
                            onClick = { viewModel.updateStatus(status) }
                        )
                    }
                }
            }

            // `sectionGap - rowGap`, not `sectionGap`: the Column already puts `rowGap`
            // between every child, so a full section gap here reads as 36dp of nothing
            // above the button.
            Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))

            PrimaryButton(
                text = if (state.isEditing) "Save changes" else "Create trip",
                onClick = viewModel::save,
                enabled = state.canSave,
                busy = state.isSaving
            )

            // Small on purpose. `padding(padding)` above already carries the navigation
            // bar's inset, so anything more than a breathing gap here is added to it.
            Spacer(Modifier.height(4.dp))
        }
    }

    if (editingStartDate) {
        DateChooser(
            initial = state.startDate,
            onDismiss = { editingStartDate = false },
            onPick = { viewModel.updateStartDate(it) }
        )
    }

    if (editingEndDate) {
        DateChooser(
            initial = state.endDate,
            onDismiss = { editingEndDate = false },
            onPick = { viewModel.updateEndDate(it) }
        )
    }
}

/**
 * The cover photo, and the invitation to add one.
 *
 * Tapping anywhere opens the picker in both states, so "add" and "change" are the same gesture
 * — the small remove button is the only thing that has to be aimed at. The empty state is a
 * designed panel rather than a grey rectangle, because on a first run this is the first thing
 * on the screen.
 */
@Composable
private fun CoverPhotoField(
    uri: String?,
    busy: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = AppThemeExtended.metrics
    val colors = AppThemeExtended.colors

    HeroImage(
        uri = uri,
        contentDescription = if (uri != null) "Trip cover photo" else null,
        modifier = modifier.clickable(enabled = !busy, onClick = onPick),
        height = 180.dp,
        shape = metrics.cardShapeLarge,
        // No scrim over the empty panel: there is no photograph under it to darken, and
        // dimming the prompt would only make it look disabled.
        scrim = uri != null
    ) {
        when {
            busy -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(metrics.cardPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = if (uri != null) Color.White else colors.accent,
                    modifier = Modifier.size(28.dp)
                )
            }

            uri == null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(metrics.cardPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Add a cover photo",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Optional. It becomes the picture on Home and on this trip's card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            else -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Change photo",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
                AppIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = "Remove cover photo",
                    onClick = onRemove,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    tint = colors.danger,
                    background = MaterialTheme.colorScheme.surface,
                    borderColor = null,
                    size = 36.dp
                )
            }
        }
    }
}

/**
 * How long the trip is, worked out from the two dates.
 *
 * Shown as its own line rather than folded into the date fields: "3 – 6 November" and
 * "4 days · 3 nights" answer different questions, and it is the second one people check
 * against a hotel booking.
 */
@Composable
private fun LengthSummary(range: String, length: String) {
    val colors = AppThemeExtended.colors

    AppCard(contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = AppThemeExtended.metrics.badgeShape,
                color = colors.accentSoft,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = range,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = length,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint
                )
            }
        }
    }
}

/**
 * The platform date picker (§9), wired through [DateTimeUtils] on both sides.
 *
 * Material hands back UTC millis whatever the device's zone is, so the conversion has
 * to be UTC too. Reading it in the local zone shifts the selection by a day for any
 * user behind UTC — a bug that is invisible to a developer sitting east of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChooser(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = DateTimeUtils.localDateToEpochMillis(initial)
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = AppThemeExtended.metrics.cardShape,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let {
                        onPick(DateTimeUtils.epochMillisToLocalDate(it))
                    }
                    onDismiss()
                }
            ) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = pickerState)
    }
}

/** A trip that ends before it starts, said plainly and next to the controls at fault. */
@Composable
private fun DatesInvalidNotice() {
    AppCard(
        color = MaterialTheme.colorScheme.errorContainer,
        borderColor = MaterialTheme.colorScheme.error
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "The last day is before the first. Pick a later end date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * "4 days · 3 nights".
 *
 * A day trip has no nights, so the word is left out rather than printed as a zero.
 */
private fun tripLengthLabel(days: Int, nights: Int): String = when {
    days <= 1 -> "1 day"
    nights == 1 -> "$days days · 1 night"
    else -> "$days days · $nights nights"
}
