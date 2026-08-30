package com.tripcompanion.app.ui.screens

import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.data.ticket.TicketFileReader
import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.TicketImportWarning
import com.tripcompanion.app.domain.model.TrainAllotment
import com.tripcompanion.app.domain.model.TrainBookingStatus
import com.tripcompanion.app.domain.service.TicketImportError
import com.tripcompanion.app.feature.train.PassengerDraft
import com.tripcompanion.app.feature.train.TrainEditorViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppFilterChip
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.PickerField
import com.tripcompanion.app.ui.components.PrimaryButton
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.SectionHeader
import com.tripcompanion.app.ui.components.label
import com.tripcompanion.app.ui.theme.AppThemeExtended
import java.time.LocalDate
import java.time.LocalTime

/** The classes the railway actually sells, so nobody has to type "3A" from memory. */
private val TRAVEL_CLASSES = listOf("1A", "2A", "3A", "3E", "SL", "CC", "EC", "2S", "GN")

/**
 * The genders offered as chips.
 *
 * Only Male and Female are shown here. [PassengerGender.TRANSGENDER] is kept in the model —
 * an IRCTC ticket can still carry it and it renders wherever a passenger's gender is shown —
 * it is simply not offered as a choice in this editor.
 *
 * [PassengerGender.UNSPECIFIED] is left out for a different reason: it is the state of having
 * nothing selected, and a chip for it beside real answers invites someone to pick it as
 * though it meant something.
 */
private val GENDERS = listOf(
    PassengerGender.MALE,
    PassengerGender.FEMALE
)

/**
 * Add or edit a train booking.
 *
 * Date, departure and arrival are three separate controls (§9), and "arrives next day" is a
 * switch rather than something inferred — an overnight train is the normal case here, not an
 * edge case, and guessing it wrong moves a journey by 24 hours.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TrainEditorScreen(
    onNavigateBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: TrainEditorViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics
    val colors = AppThemeExtended.colors
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)

    var editingDate by remember { mutableStateOf(false) }
    var editingDeparture by remember { mutableStateOf(false) }
    var editingArrival by remember { mutableStateOf(false) }

    // `OpenDocument` rather than `GetContent`: it opens the system file browser, which is where
    // a PDF that arrived by email or WhatsApp actually lives. `GetContent` leads with the photo
    // picker, and an e-ticket is never in the gallery.
    val ticketPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        // Null is a cancelled picker, which is not a failure and should say nothing.
        if (uri != null) viewModel.importTicket { TicketFileReader.read(context, uri) }
    }

    fun pickTicket() = ticketPicker.launch(arrayOf("application/pdf"))

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit train" else "Add a train") },
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
            Spacer(Modifier.height(metrics.rowGap))

            TicketImportSection(
                isImporting = state.isImporting,
                summary = state.importSummary,
                warnings = state.importWarnings,
                error = state.importError,
                onPick = { pickTicket() },
                onDismiss = viewModel::dismissImportNotice
            )

            // ── The train ──────────────────────────────────────────────────────────
            SectionHeader(
                title = "The train",
                subtitle = "The number is what fetches the timetable and live status."
            )

            Row(horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
                OutlinedTextField(
                    value = state.number,
                    onValueChange = viewModel::setNumber,
                    label = { Text("Number") },
                    placeholder = { Text("12978") },
                    shape = metrics.controlShape,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(140.dp)
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Name") },
                    placeholder = { Text("Optional") },
                    shape = metrics.controlShape,
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Stations ───────────────────────────────────────────────────────────
            SectionHeader(title = "From and to")

            StationFields(
                codeLabel = "From code",
                code = state.originCode,
                onCodeChange = viewModel::setOriginCode,
                nameLabel = "Station name",
                name = state.originName,
                onNameChange = viewModel::setOriginName
            )

            StationFields(
                codeLabel = "To code",
                code = state.destinationCode,
                onCodeChange = viewModel::setDestinationCode,
                nameLabel = "Station name",
                name = state.destinationName,
                onNameChange = viewModel::setDestinationName
            )

            // ── When ───────────────────────────────────────────────────────────────
            SectionHeader(title = "When")

            PickerField(
                label = "Journey date",
                value = DateTimeUtils.formatFullDate(state.journeyDate),
                icon = Icons.Default.CalendarToday,
                onClick = { editingDate = true }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
                PickerField(
                    label = "Departs",
                    value = DateTimeUtils.formatTime(state.departureTime),
                    icon = Icons.Default.Schedule,
                    onClick = { editingDeparture = true },
                    modifier = Modifier.weight(1f)
                )
                PickerField(
                    label = "Arrives",
                    value = DateTimeUtils.formatTime(state.arrivalTime),
                    icon = Icons.Default.Schedule,
                    onClick = { editingArrival = true },
                    modifier = Modifier.weight(1f)
                )
            }

            NextDayRow(
                checked = state.arrivesNextDay,
                onCheckedChange = viewModel::setArrivesNextDay
            )

            if (state.canSave) {
                Text(
                    text = DateTimeUtils.formatDayAndDate(state.departure.toLocalDate()) +
                        "  ·  " + DateTimeUtils.formatTimeRange(state.departure, state.arrival) +
                        "  ·  " + DateTimeUtils.formatDuration(state.departure, state.arrival),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppThemeExtended.colors.textFaint
                )
            } else if (!state.arrival.isAfter(state.departure)) {
                InvalidTimesNotice()
            }

            // ── Reservation ────────────────────────────────────────────────────────
            Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))
            SectionHeader(
                title = "Your reservation",
                subtitle = "All optional. A journey with no seat yet is still a journey."
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TRAVEL_CLASSES.forEach { travelClass ->
                    AppFilterChip(
                        label = travelClass,
                        selected = state.travelClass.equals(travelClass, ignoreCase = true),
                        onClick = {
                            viewModel.setTravelClass(
                                if (state.travelClass.equals(travelClass, ignoreCase = true)) {
                                    ""
                                } else {
                                    travelClass
                                }
                            )
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
                OutlinedTextField(
                    value = state.pnr,
                    onValueChange = viewModel::setPnr,
                    label = { Text("PNR") },
                    shape = metrics.controlShape,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.platform,
                    onValueChange = viewModel::setPlatform,
                    label = { Text("Platform") },
                    shape = metrics.controlShape,
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
            }

            // Live booking status by PNR. Shown only with a key configured — otherwise the
            // fetch could only fail, so it is not offered rather than offered and greyed out.
            // A PNR fills coach, berth and status but never names; the notice says so, in the
            // same cards the e-ticket import uses so the two paths read alike.
            if (state.isPnrLookupAvailable) {
                SecondaryButton(
                    text = if (state.isFetchingPnr) "Fetching…" else "Fetch booking status",
                    onClick = viewModel::fetchPnr,
                    enabled = state.canFetchPnr,
                    icon = Icons.Default.Search
                )

                state.pnrError?.let { message ->
                    ImportNotice(
                        text = message,
                        icon = Icons.Default.ErrorOutline,
                        tint = colors.danger,
                        background = colors.dangerSoft,
                        textColor = colors.dangerText,
                        onDismiss = viewModel::dismissPnrNotice
                    )
                }

                state.pnrSummary?.let { message ->
                    ImportNotice(
                        text = message,
                        icon = Icons.Default.CheckCircle,
                        tint = colors.success,
                        background = colors.successSoft,
                        textColor = colors.successText,
                        onDismiss = viewModel::dismissPnrNotice
                    )
                }
            }

            // ── The party ──────────────────────────────────────────────────────────
            Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))
            SectionHeader(
                title = "Who is travelling",
                subtitle = if (state.passengers.isEmpty()) {
                    "One PNR covers a party, each with their own berth. Add them here, or " +
                        "import the e-ticket PDF and they come across already seated."
                } else {
                    "They are numbered in the order the chart lists them."
                }
            )

            state.passengers.forEachIndexed { index, draft ->
                PassengerFields(
                    index = index,
                    draft = draft,
                    onRemove = { viewModel.removePassenger(index) },
                    onName = { viewModel.setPassengerName(index, it) },
                    onAge = { viewModel.setPassengerAge(index, it) },
                    onGender = { viewModel.setPassengerGender(index, it) },
                    onCoach = { viewModel.setPassengerCoach(index, it) },
                    onBerth = { viewModel.setPassengerBerth(index, it) },
                    onBerthType = { viewModel.setPassengerBerthType(index, it) },
                    onStatus = { viewModel.setPassengerStatus(index, it) },
                    onQueuePosition = { viewModel.setPassengerQueuePosition(index, it) }
                )
            }

            SecondaryButton(
                text = if (state.passengers.isEmpty()) "Add a passenger" else "Add another",
                onClick = viewModel::addPassenger,
                icon = Icons.Default.PersonAdd
            )

            // Only with nobody listed. With a party present each member carries their own status
            // and the booking's is derived from them, so two controls for one fact is how a badge
            // ends up disagreeing with the list underneath it.
            if (state.passengers.isEmpty()) {
                SectionHeader(
                    title = "Booking status",
                    subtitle = "Where this booking stands while nobody is listed on it."
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrainBookingStatus.entries.forEach { status ->
                        AppFilterChip(
                            label = status.label,
                            selected = state.bookingStatus == status,
                            onClick = { viewModel.setBookingStatus(status) }
                        )
                    }
                }
            }

            // ── Itinerary link ─────────────────────────────────────────────────────
            // Shown even with nothing to pick, because saving now adds a row to the plan and a
            // row appearing on the itinerary unannounced would read as a bug.
            Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))
            SectionHeader(
                title = "On the itinerary",
                subtitle = if (state.journeyEvents.isEmpty()) {
                    "Saving this booking adds it to your plan as a journey, so the day shows " +
                        "the hours you spend travelling."
                } else {
                    "Saving this booking puts it on your plan. Pick the journey it already " +
                        "fills, or leave it and a new one is added."
                }
            )
            state.journeyEvents.forEach { event ->
                LinkRow(
                    title = event.title,
                    detail = DateTimeUtils.formatDayAndDate(event.startTime.toLocalDate()) +
                        " · " + DateTimeUtils.formatTime(event.startTime),
                    linked = state.linkedEventId == event.id,
                    onClick = { viewModel.toggleLinkedEvent(event.id) }
                )
            }

            // ── Notes ──────────────────────────────────────────────────────────────
            Spacer(Modifier.height(metrics.sectionGap - metrics.rowGap))
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notes") },
                placeholder = { Text("Anything you'll want at the station") },
                shape = metrics.controlShape,
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            state.error?.let { message ->
                AppCard(
                    color = MaterialTheme.colorScheme.errorContainer,
                    borderColor = MaterialTheme.colorScheme.error
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(metrics.sectionGap))

            PrimaryButton(
                text = if (state.isEditing) "Save changes" else "Add train",
                onClick = { viewModel.save(onSaved) },
                enabled = state.canSave,
                busy = state.isSaving
            )

            Spacer(Modifier.height(metrics.sectionGap))
        }
    }

    if (editingDate) {
        TrainDateChooser(
            initial = state.journeyDate,
            onDismiss = { editingDate = false },
            onPick = viewModel::setJourneyDate
        )
    }

    if (editingDeparture) {
        TrainTimeChooser(
            initialTime = state.departureTime,
            is24Hour = is24Hour,
            onDismiss = { editingDeparture = false },
            onConfirm = {
                viewModel.setDepartureTime(it)
                editingDeparture = false
            }
        )
    }

    if (editingArrival) {
        TrainTimeChooser(
            initialTime = state.arrivalTime,
            is24Hour = is24Hour,
            onDismiss = { editingArrival = false },
            onConfirm = {
                viewModel.setArrivalTime(it)
                editingArrival = false
            }
        )
    }
}

/**
 * Fill the form from an e-ticket PDF.
 *
 * This is a shortcut past the rest of the form, not a replacement for it. The import writes the
 * same controls a person would fill by hand and every one of them stays editable, so a ticket
 * that only half reads is a head start rather than a dead end.
 *
 * The notices are cards rather than a snackbar because they are about fields further down the
 * screen: a message telling you to set the arrival has to still be there when you get to it.
 */
@Composable
private fun TicketImportSection(
    isImporting: Boolean,
    summary: String?,
    warnings: List<TicketImportWarning>,
    error: TicketImportError?,
    onPick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeExtended.colors

    SectionHeader(
        title = "Start from the ticket",
        subtitle = "Pick the e-ticket PDF and the train, the times and everyone on the booking " +
            "come across filled in. Everything stays editable."
    )

    // Tinted rather than a solid blue fill: importing is the quick way in, but typing the
    // booking is not the fallback path, and two filled buttons on one form pick a winner.
    PrimaryButton(
        text = "Import an e-ticket PDF",
        onClick = onPick,
        enabled = !isImporting,
        busy = isImporting,
        icon = Icons.Default.UploadFile,
        fill = colors.accentSoft,
        contentColor = colors.accent
    )

    if (error != null) {
        ImportNotice(
            text = error.message,
            icon = Icons.Default.ErrorOutline,
            tint = colors.danger,
            background = colors.dangerSoft,
            textColor = colors.dangerText,
            onDismiss = onDismiss
        )
    }

    // Warnings only ever arrive with a summary, so the summary carries the one dismiss control
    // and clearing it clears the group.
    if (summary != null) {
        ImportNotice(
            text = summary,
            icon = Icons.Default.CheckCircle,
            tint = colors.success,
            background = colors.successSoft,
            textColor = colors.successText,
            onDismiss = onDismiss
        )
    }

    warnings.forEach { warning ->
        ImportNotice(
            text = warning.message,
            icon = Icons.Default.Warning,
            tint = colors.warning,
            background = colors.warningSoft,
            textColor = colors.warningText
        )
    }
}

/** One line of news about the import, in the colour of what kind of news it is. */
@Composable
private fun ImportNotice(
    text: String,
    icon: ImageVector,
    tint: Color,
    background: Color,
    textColor: Color,
    onDismiss: (() -> Unit)? = null
) {
    AppCard(color = background, borderColor = tint) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = textColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * What a shortfall means for the person filling the form, and where to fix it.
 *
 * The wording is here rather than on the enum because it names this screen's own sections —
 * "under \"When\"" is only true of this layout, and the parser should not know about it.
 */
private val TicketImportWarning.message: String
    get() = when (this) {
        TicketImportWarning.DEPARTURE_TIME_MISSING ->
            "The departure time did not read. Set it under \"When\"."
        TicketImportWarning.ARRIVAL_TIME_MISSING ->
            "The ticket printed the destination's name where the arrival time goes. " +
                "Set the arrival under \"When\"."
        TicketImportWarning.NO_PASSENGERS_FOUND ->
            "No passengers could be read. Add them below."
        TicketImportWarning.FARE_INCOMPLETE ->
            "Part of the fare breakdown did not read, so the total may not match its parts."
        TicketImportWarning.STATION_CODES_MISSING ->
            "This ticket prints station names without their codes. Add them under \"From and to\"."
    }

/** Why nothing was filled in, and what to do instead. */
private val TicketImportError.message: String
    get() = when (this) {
        TicketImportError.NOT_A_PDF ->
            "That file is not a PDF. Pick the e-ticket you were emailed."
        TicketImportError.NO_TEXT_LAYER ->
            "This PDF is a scan or a photo, so there is no text to read. Enter the booking below."
        TicketImportError.NOT_AN_ETICKET ->
            "No reservation was found in that PDF. Check it is the e-ticket and not the " +
                "payment receipt."
        TicketImportError.UNREADABLE ->
            "That file could not be opened."
    }

/**
 * One traveller's row on the booking.
 *
 * The fields are ordered the way an e-ticket prints them — who, then where they sit — so
 * someone copying off a paper ticket reads straight down. Coach and berth are per person on
 * purpose: a party is allotted seat by seat, and two people on the same PNR routinely end up
 * in different berths.
 *
 * The queue position appears only while the status is a waitlist or RAC. On a confirmed ticket
 * there is no queue, and a field showing "24" next to a berth would read as a second berth.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassengerFields(
    index: Int,
    draft: PassengerDraft,
    onRemove: () -> Unit,
    onName: (String) -> Unit,
    onAge: (String) -> Unit,
    onGender: (PassengerGender) -> Unit,
    onCoach: (String) -> Unit,
    onBerth: (String) -> Unit,
    onBerthType: (BerthType) -> Unit,
    onStatus: (TrainBookingStatus) -> Unit,
    onQueuePosition: (String) -> Unit
) {
    val metrics = AppThemeExtended.metrics
    val colors = AppThemeExtended.colors

    // What the ticket said at booking, when the chart has since moved this person. Worth its own
    // line: waitlisted then confirmed is the case where both columns are news.
    val bookedAs = draft.bookingStatusText.takeIf {
        it.isNotBlank() && TrainAllotment.parse(it) != draft.allotment
    }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Passenger ${index + 1}",
                style = AppThemeExtended.text.eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            AppIconButton(
                icon = Icons.Default.Close,
                contentDescription = "Remove passenger ${index + 1}",
                onClick = onRemove,
                tint = colors.danger,
                background = colors.dangerSoft,
                borderColor = null,
                size = 36.dp
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = draft.name,
            onValueChange = onName,
            label = { Text("Name") },
            placeholder = { Text("As printed on the ticket") },
            shape = metrics.controlShape,
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(metrics.rowGap))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft.age,
                onValueChange = onAge,
                label = { Text("Age") },
                shape = metrics.controlShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(104.dp)
            )
            Spacer(Modifier.width(metrics.rowGap))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Tapping the chosen one clears it: a passenger whose gender was never asked for
                // is a real state, and there is no chip that says so.
                GENDERS.forEach { gender ->
                    AppFilterChip(
                        label = gender.label,
                        selected = draft.gender == gender,
                        onClick = {
                            onGender(
                                if (draft.gender == gender) PassengerGender.UNSPECIFIED else gender
                            )
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(metrics.rowGap))

        Row(horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
            OutlinedTextField(
                value = draft.coach,
                onValueChange = onCoach,
                label = { Text("Coach") },
                placeholder = { Text("S4") },
                shape = metrics.controlShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters
                ),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = draft.berth,
                onValueChange = onBerth,
                label = { Text("Berth") },
                placeholder = { Text("8") },
                shape = metrics.controlShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(metrics.rowGap))

        Row(horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
            CompactDropdown(
                label = "Berth type",
                value = draft.berthType,
                options = BerthType.entries,
                optionLabel = { it.label.ifBlank { "Not set" } },
                onSelect = onBerthType,
                modifier = Modifier.weight(1f)
            )
            CompactDropdown(
                label = "Status",
                value = draft.status,
                options = TrainBookingStatus.entries,
                optionLabel = { it.label },
                onSelect = onStatus,
                modifier = Modifier.weight(1f)
            )
        }

        if (draft.isQueued) {
            Spacer(Modifier.height(metrics.rowGap))
            OutlinedTextField(
                value = draft.queuePosition,
                onValueChange = onQueuePosition,
                label = { Text(if (draft.status == TrainBookingStatus.RAC) "RAC number" else "Waitlist number") },
                placeholder = { Text("24") },
                supportingText = {
                    Text(
                        text = if (draft.queueKind.isBlank()) {
                            "Where in the queue this booking sits."
                        } else {
                            "Where in the ${draft.queueKind} queue this booking sits."
                        }
                    )
                },
                shape = metrics.controlShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(200.dp)
            )
        }

        if (bookedAs != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Booked as $bookedAs",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint
            )
        }
    }
}

/**
 * A field that opens a list instead of a keyboard.
 *
 * Berth types and booking statuses are closed sets of eleven and five, which is too many for a
 * row of chips beside every passenger and not something anyone should be typing by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> CompactDropdown(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = optionLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = AppThemeExtended.metrics.controlShape,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** A station code and its name, side by side.
 *
 * The code is what the railway's API understands; the name is what the traveller reads. Both
 * are needed, and typing one does not give you the other without a station database.
 */
@Composable
private fun StationFields(
    codeLabel: String,
    code: String,
    onCodeChange: (String) -> Unit,
    nameLabel: String,
    name: String,
    onNameChange: (String) -> Unit
) {
    val metrics = AppThemeExtended.metrics
    Row(horizontalArrangement = Arrangement.spacedBy(metrics.rowGap)) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text(codeLabel) },
            shape = metrics.controlShape,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters
            ),
            modifier = Modifier.width(130.dp)
        )
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(nameLabel) },
            shape = metrics.controlShape,
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Overnight trains are the normal case on this route, so the switch says what it does. */
@Composable
private fun NextDayRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Arrives the next day",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Turn this on for an overnight run.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

/** One candidate journey from the itinerary. Tapping the linked one clears the link. */
@Composable
private fun LinkRow(
    title: String,
    detail: String,
    linked: Boolean,
    onClick: () -> Unit
) {
    val colors = AppThemeExtended.colors
    val metrics = AppThemeExtended.metrics
    AppCard(
        onClick = onClick,
        borderColor = if (linked) colors.accent else MaterialTheme.colorScheme.outline,
        borderWidth = if (linked) metrics.borderWidthStrong else metrics.borderWidth
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (linked) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Linked",
                    style = AppThemeExtended.text.badge,
                    color = colors.accent
                )
            }
        }
    }
}

/** An arrival that is not after the departure, said next to the controls at fault. */
@Composable
private fun InvalidTimesNotice() {
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
                text = "The arrival is not after the departure. If the train runs overnight, " +
                    "turn on \"Arrives the next day\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/** Material hands back UTC millis whatever the device's zone is, so the read is UTC too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainDateChooser(
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
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let {
                    onPick(DateTimeUtils.epochMillisToLocalDate(it))
                }
                onDismiss()
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DatePicker(state = pickerState)
    }
}

/** `is24Hour` follows the device so a boarding pass and the app read the same. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainTimeChooser(
    initialTime: LocalTime,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = is24Hour
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = AppThemeExtended.metrics.cardShape,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(LocalTime.of(pickerState.hour, pickerState.minute))
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        }
    )
}
