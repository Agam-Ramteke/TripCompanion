package com.tripcompanion.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.data.local.ImageStorageHelper
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.StayDetails
import com.tripcompanion.app.feature.stay.HotelUiState
import com.tripcompanion.app.feature.stay.HotelViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppDivider
import com.tripcompanion.app.ui.components.AppFilterChip
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.FilterChipRow
import com.tripcompanion.app.ui.components.HotelCard
import com.tripcompanion.app.ui.components.MetaRow
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.SectionHeader
import com.tripcompanion.app.ui.components.TextActionButton
import com.tripcompanion.app.ui.theme.AppThemeExtended
import kotlinx.coroutines.launch

/**
 * Where you're staying.
 *
 * Check-in and check-out are read off the STAY event rather than stored again, so this screen and
 * the itinerary can never disagree about when the room is booked. What lives here is the
 * paperwork: the reference to quote at the desk, the room, and how to reach the property — the
 * things you need standing in a lobby with one bar of signal.
 */
@Composable
fun HotelScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEvent: (Long) -> Unit,
    onNavigateToItinerary: (Long) -> Unit,
    viewModel: HotelViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val trip = state.trip
    val stay = state.selected

    if (trip == null || stay == null) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.screenPadding)
        ) {
            ScreenHeader(
                title = "Stay",
                subtitle = trip?.name,
                onNavigateBack = onNavigateBack
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Default.Hotel,
                    title = if (trip == null) "No trip yet" else "No stay booked",
                    message = if (trip == null) {
                        "Plan a trip first. A stay is an activity on its itinerary, and its " +
                            "booking details live here."
                    } else {
                        "Add a Stay to the itinerary — its check-in, check-out and booking " +
                            "reference all appear on this screen."
                    },
                    actionLabel = trip?.let { "Open the itinerary" },
                    onAction = trip?.let { { onNavigateToItinerary(it.id) } }
                )
            }
        }
        return
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    // Copied into app-private storage, so the photo survives the picker's
                    // temporary permission grant and still loads offline.
                    val stored = ImageStorageHelper.saveImageToInternalStorage(context, uri)
                    viewModel.saveDetails(
                        (state.details ?: StayDetails(eventId = stay.id)).copy(photoUri = stored)
                    )
                } catch (_: Exception) {
                    Toast.makeText(context, "That photo couldn't be saved", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    if (state.isEditing) {
        HotelDetailsForm(
            stayTitle = stay.title,
            initial = state.details ?: StayDetails(eventId = stay.id),
            onCancel = { viewModel.setEditing(false) },
            onSave = viewModel::saveDetails
        )
        return
    }

    val details = state.details

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.screenPadding,
            end = metrics.screenPadding,
            top = 12.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item("header") {
            ScreenHeader(
                title = "Stay",
                subtitle = trip.name,
                onNavigateBack = onNavigateBack
            )
        }

        // More than one hotel on a trip is a normal itinerary, so the picker is only in the way
        // when there is nothing to pick.
        if (state.stays.size > 1) {
            item("stay-picker") {
                FilterChipRow {
                    state.stays.forEach { candidate ->
                        AppFilterChip(
                            label = stayChipLabel(candidate),
                            selected = candidate.id == stay.id,
                            onClick = { viewModel.selectStay(candidate.id) }
                        )
                    }
                }
            }
        }

        item("card") {
            HotelCard(
                name = stay.title,
                imageUri = stayImageUri(details, state.location),
                address = stayAddress(details, state.location),
                checkInLabel = stayStamp(stay.startTime),
                checkOutLabel = stayStamp(stay.endTime),
                nightsLabel = stayNightsLabel(stayNights(stay)),
                statusLabel = stayStatusLabel(stay, state.now),
                statusTone = stayStatusTone(stay, state.now)
            )
        }

        item("stay-line") {
            AppCard {
                Text(
                    text = stayPhaseLine(state),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = DateTimeUtils.formatDayAndDate(stay.startTime.toLocalDate()) +
                        " to " + DateTimeUtils.formatDayAndDate(stay.endTime.toLocalDate()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // The instructions are what a person reads at the door at midnight, so they get their
        // own card and full height rather than one truncated line in a table.
        details?.checkInInstructions?.takeIf { it.isNotBlank() }?.let { instructions ->
            item("instructions") {
                AppCard {
                    Text(
                        text = "Getting in",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = instructions,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        if (hasBookingFacts(details)) {
            item("booking") {
                AppCard {
                    Text(
                        text = "Booking",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    val facts = bookingFacts(details)
                    facts.forEachIndexed { index, fact ->
                        if (index > 0) {
                            Spacer(Modifier.height(4.dp))
                            AppDivider()
                            Spacer(Modifier.height(4.dp))
                        }
                        MetaRow(label = fact.label, value = fact.value, icon = fact.icon)
                    }
                }
            }
        } else {
            item("no-booking") {
                AppCard {
                    Text(
                        text = "No booking details yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Add the reference, the room and a phone number and they're here " +
                            "whether or not you have signal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        text = "Add booking details",
                        onClick = { viewModel.setEditing(true) },
                        icon = Icons.Default.Edit
                    )
                }
            }
        }

        item("actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                details?.contactPhone?.takeIf { it.isNotBlank() }?.let { phone ->
                    PrimaryButton(
                        text = "Call the hotel",
                        onClick = { dial(context, phone) },
                        icon = Icons.Default.Call
                    )
                }
                if (state.location != null || stayAddress(details, state.location) != null) {
                    SecondaryButton(
                        text = "Directions",
                        onClick = { openHotelDirections(context, state) },
                        icon = Icons.Default.Directions
                    )
                }
                SecondaryButton(
                    text = if (details?.photoUri == null) "Add a photo" else "Change the photo",
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    icon = Icons.Default.AddPhotoAlternate
                )
                if (hasBookingFacts(details)) {
                    TextActionButton(
                        text = "Edit booking details",
                        onClick = { viewModel.setEditing(true) },
                        icon = Icons.Default.Edit
                    )
                }
                TextActionButton(
                    text = "Open on the itinerary",
                    onClick = { onNavigateToEvent(stay.id) },
                    icon = Icons.AutoMirrored.Filled.EventNote
                )
            }
        }
    }
}

/**
 * The booking paperwork, edited as one form.
 *
 * Saved in a single write rather than field by field, so a half-typed phone number never
 * reaches the database and a mistake is abandoned by leaving.
 */
@Composable
private fun HotelDetailsForm(
    stayTitle: String,
    initial: StayDetails,
    onCancel: () -> Unit,
    onSave: (StayDetails) -> Unit
) {
    val metrics = AppThemeExtended.metrics

    var reference by remember { mutableStateOf(initial.bookingReference) }
    var roomType by remember { mutableStateOf(initial.roomType) }
    var guests by remember { mutableStateOf(initial.guests) }
    var phone by remember { mutableStateOf(initial.contactPhone) }
    var address by remember { mutableStateOf(initial.address) }
    var instructions by remember { mutableStateOf(initial.checkInInstructions) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.screenPadding,
            end = metrics.screenPadding,
            top = 12.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item("header") {
            ScreenHeader(
                title = "Booking details",
                subtitle = stayTitle,
                onNavigateBack = onCancel
            )
        }

        item("reference") {
            SectionHeader(
                title = "At the desk",
                subtitle = "What you'll be asked for on arrival."
            )
        }

        item("fields") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = { Text("Booking reference") },
                    singleLine = true,
                    shape = metrics.controlShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = roomType,
                    onValueChange = { roomType = it },
                    label = { Text("Room") },
                    placeholder = { Text("Deluxe lake view") },
                    singleLine = true,
                    shape = metrics.controlShape,
                    modifier = Modifier.fillMaxWidth()
                )
                GuestStepper(
                    guests = guests,
                    onChange = { guests = it.coerceIn(1, 20) }
                )
            }
        }

        item("contact-header") {
            SectionHeader(
                title = "Reaching the property",
                subtitle = "Kept on the device, so it works with no signal."
            )
        }

        item("contact") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    singleLine = true,
                    shape = metrics.controlShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    // The hotel's own wording, which is what you show a driver.
                    placeholder = { Text("As written on the confirmation") },
                    minLines = 2,
                    shape = metrics.controlShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Getting in") },
                    placeholder = { Text("Reception is on the first floor; the gate code is…") },
                    minLines = 3,
                    shape = metrics.controlShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item("save") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(
                    text = "Save details",
                    onClick = {
                        onSave(
                            initial.copy(
                                bookingReference = reference.trim(),
                                roomType = roomType.trim(),
                                guests = guests,
                                contactPhone = phone.trim(),
                                address = address.trim(),
                                checkInInstructions = instructions.trim()
                            )
                        )
                    }
                )
                TextActionButton(text = "Cancel", onClick = onCancel)
            }
        }
    }
}

/** Guests as two buttons and a figure, because it is a small whole number and nothing else. */
@Composable
private fun GuestStepper(guests: Int, onChange: (Int) -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Guests",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            AppIconButton(
                icon = Icons.Default.Remove,
                contentDescription = "One fewer guest",
                onClick = { onChange(guests - 1) }
            )
            Text(
                text = guests.toString(),
                style = AppThemeExtended.text.statNumber,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            AppIconButton(
                icon = Icons.Default.Add,
                contentDescription = "One more guest",
                onClick = { onChange(guests + 1) }
            )
        }
    }
}

/** The back button, the screen's name, and what it belongs to. */
@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String?,
    onNavigateBack: () -> Unit
) {
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
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class BookingFact(
    val label: String,
    val value: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * Only the paperwork that exists.
 *
 * A row of dashes where the reference should be says nothing a missing row doesn't say better,
 * and the empty card below the list already offers the way to fill them in.
 */
private fun bookingFacts(details: StayDetails?): List<BookingFact> {
    if (details == null) return emptyList()
    return buildList {
        details.bookingReference.takeIf { it.isNotBlank() }?.let {
            add(BookingFact("Reference", it, Icons.Default.VpnKey))
        }
        details.roomType.takeIf { it.isNotBlank() }?.let {
            add(BookingFact("Room", it, Icons.Default.Tag))
        }
        if (details.guests > 1) {
            add(BookingFact("Guests", details.guests.toString(), Icons.Default.Person))
        }
        details.contactPhone.takeIf { it.isNotBlank() }?.let {
            add(BookingFact("Phone", it, Icons.Default.Call))
        }
        details.address.takeIf { it.isNotBlank() }?.let {
            add(BookingFact("Address", it, Icons.Default.Place))
        }
    }
}

private fun hasBookingFacts(details: StayDetails?): Boolean = bookingFacts(details).isNotEmpty()

private fun stayChipLabel(stay: Event): String =
    DateTimeUtils.formatShortDate(stay.startTime.toLocalDate()) + " · " + stay.title

/** One sentence about where this stay stands, in the tense it is actually in. */
private fun stayPhaseLine(state: HotelUiState): String {
    val stay = state.selected ?: return ""
    return when {
        stayHasCheckedOut(stay, state.now) -> "Checked out on " +
            DateTimeUtils.formatShortDate(stay.endTime.toLocalDate()) + "."
        // formatTimeUntil already supplies the "in …" — reading its output back into a sentence
        // is the whole reason it phrases itself rather than returning a bare number.
        stayIsCheckedIn(stay, state.now) -> "You're checked in. Check-out " +
            DateTimeUtils.formatTimeUntil(state.now, stay.endTime) + "."
        else -> "Check-in " + DateTimeUtils.formatTimeUntil(state.now, stay.startTime) + "."
    }
}

/** `ACTION_DIAL` fills the dialler instead of placing the call, so it needs no permission. */
private fun dial(context: android.content.Context, phone: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No dialler on this device", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Hands the hotel to whatever maps app the device has.
 *
 * A coordinate is used when there is one, because it is unambiguous; an address is passed as a
 * query when there is not, which is what a person would type themselves.
 */
private fun openHotelDirections(context: android.content.Context, state: HotelUiState) {
    val place = state.location
    val label = Uri.encode(state.selected?.title?.ifBlank { "Hotel" } ?: "Hotel")
    val uri = if (place != null && (place.latitude != 0.0 || place.longitude != 0.0)) {
        Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}($label)")
    } else {
        val query = stayAddress(state.details, state.location) ?: return
        Uri.parse("geo:0,0?q=${Uri.encode(query)}")
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No maps app to open this in", Toast.LENGTH_SHORT).show()
    }
}
