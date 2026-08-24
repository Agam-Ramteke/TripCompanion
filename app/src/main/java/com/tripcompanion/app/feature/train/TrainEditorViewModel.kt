package com.tripcompanion.app.feature.train

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.ParsedTicket
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.TicketImportWarning
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainAllotment
import com.tripcompanion.app.domain.model.TrainBookingStatus
import com.tripcompanion.app.domain.model.TrainPassenger
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.repository.TripRepository
import com.tripcompanion.app.domain.service.JourneyEventLinker
import com.tripcompanion.app.domain.service.PnrLookupError
import com.tripcompanion.app.domain.service.PnrLookupOutcome
import com.tripcompanion.app.domain.service.PnrLookupService
import com.tripcompanion.app.domain.service.TicketImportError
import com.tripcompanion.app.domain.service.TicketImportOutcome
import com.tripcompanion.app.domain.service.TicketImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject

/**
 * One traveller, as a form holds them.
 *
 * Separate from [TrainPassenger] because a text field holds a string: an age half-typed is
 * `"2"`, which is not an `Int?` and is not a mistake either. The parse happens once, on save.
 *
 * [bookingStatusText] and [currentStatusText] are carried through untouched from whatever the
 * form was opened on. They are the railway's own columns verbatim, and an editor that rewrote
 * them on every save would destroy the one copy of a string this app failed to understand.
 */
data class PassengerDraft(
    val name: String = "",
    /** Digits as typed. Blank means not recorded, which is not the same as zero. */
    val age: String = "",
    val gender: PassengerGender = PassengerGender.UNSPECIFIED,
    val coach: String = "",
    val berth: String = "",
    val berthType: BerthType = BerthType.UNKNOWN,
    val status: TrainBookingStatus = TrainBookingStatus.CONFIRMED,
    /** Place in the queue, shown only while [status] is a waitlist or RAC. */
    val queuePosition: String = "",
    /** `"GNWL"`, `"PQWL"` — preserved from an imported ticket, never asked for by hand. */
    val queueKind: String = "",
    val bookingStatusText: String = "",
    val currentStatusText: String = ""
) {
    /** A row the user added and never filled. Dropped on save rather than saved as a blank. */
    val isEmpty: Boolean
        get() = name.isBlank() && age.isBlank() && coach.isBlank() &&
            berth.isBlank() && queuePosition.isBlank()

    val allotment: TrainAllotment
        get() = TrainAllotment(
            status = status,
            coach = coach.trim().uppercase(),
            berth = berth.trim(),
            berthType = berthType,
            queuePosition = queuePosition.trim().toIntOrNull(),
            queueKind = queueKind
        )

    /** True while the queue-position field is worth showing at all. */
    val isQueued: Boolean
        get() = status == TrainBookingStatus.WAITLISTED || status == TrainBookingStatus.RAC
}

/**
 * The add-and-edit form for a train booking.
 *
 * Date, departure and arrival are three separate controls (§9). One combined field is where
 * the old editors leaked machine timestamps onto the screen, and a train that arrives the
 * next morning needs the day to be a thing the user can say rather than something inferred.
 *
 * [passengers] is a list because a PNR covers a party, each member with their own berth. There
 * is no control for reordering it: the serial number is the chart position the railway allotted,
 * counted from the list on save, and it is not the traveller's to shuffle.
 *
 * [source] is the record this form was opened on, or the one an imported ticket produced. Every
 * field the form does not show — the fare, the quota, the agent's booking id, the date the
 * ticket was bought — rides along on it, so saving an edit cannot silently empty a column the
 * editor has no control for.
 */
data class TrainEditorUiState(
    val tripId: Long = 0L,
    val trainId: Long? = null,
    val number: String = "",
    val name: String = "",
    val originCode: String = "",
    val originName: String = "",
    val destinationCode: String = "",
    val destinationName: String = "",
    val journeyDate: LocalDate = LocalDate.now(),
    val departureTime: LocalTime = LocalTime.of(8, 0),
    val arrivalTime: LocalTime = LocalTime.of(14, 0),
    /** Arrival on the following day. Overnight trains are the normal case, not an edge one. */
    val arrivesNextDay: Boolean = false,
    val travelClass: String = "",
    val pnr: String = "",
    val platform: String = "",
    val passengers: List<PassengerDraft> = emptyList(),
    /**
     * The booking's status when nobody is listed on it.
     *
     * With a party present each member carries their own and this is derived from them on save,
     * which is why the control for it only appears while [passengers] is empty — two controls
     * for one fact is how a badge ends up disagreeing with the list underneath it.
     */
    val bookingStatus: TrainBookingStatus = TrainBookingStatus.CONFIRMED,
    val notes: String = "",
    /** JOURNEY entries on this trip, offered as the itinerary slot this train fills. */
    val journeyEvents: List<Event> = emptyList(),
    val linkedEventId: Long? = null,
    val source: Train? = null,
    /** A ticket is being read. The form stays usable; only the import button waits. */
    val isImporting: Boolean = false,
    /**
     * Confirmation that an import landed, in a sentence.
     *
     * A form that silently rearranges itself is unnerving, and half these fields are below the
     * fold. Cleared by [TrainEditorViewModel.dismissImportNotice].
     */
    val importSummary: String? = null,
    /** What the parse could not settle. Worded by the screen, not here. */
    val importWarnings: List<TicketImportWarning> = emptyList(),
    val importError: TicketImportError? = null,
    /** A PNR is being looked up. The form stays usable; only the fetch button waits. */
    val isFetchingPnr: Boolean = false,
    /**
     * Confirmation that a PNR lookup landed, in a sentence.
     *
     * Separate from [importSummary] because the two sources fill from different places and say
     * different things — a PNR brings seats but no names, and the sentence has to admit that.
     * Cleared by [TrainEditorViewModel.dismissPnrNotice].
     */
    val pnrSummary: String? = null,
    /** Why a PNR lookup failed, as a finished sentence — worded in the view model, not the screen. */
    val pnrError: String? = null,
    /** Whether a PNR lookup can be made at all. False binds no fetch button. */
    val isPnrLookupAvailable: Boolean = false,
    val isSaving: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val isEditing: Boolean get() = trainId != null

    val departure: LocalDateTime get() = LocalDateTime.of(journeyDate, departureTime)

    val arrival: LocalDateTime
        get() = LocalDateTime.of(if (arrivesNextDay) journeyDate.plusDays(1) else journeyDate, arrivalTime)

    /**
     * Whether the form can be saved.
     *
     * A number is the one field the live lookup cannot work without, and an arrival that is
     * not after its departure is not a journey — validated here rather than at the database,
     * so the message lands next to the field that caused it.
     *
     * A half-filled passenger row does not block the save. It is dropped instead, because
     * refusing to save a whole booking over a stray tap on "Add passenger" would be worse.
     */
    val canSave: Boolean get() = number.isNotBlank() && arrival.isAfter(departure)

    /** The party, minus the rows nobody filled in. What actually gets written. */
    val filledPassengers: List<PassengerDraft> get() = passengers.filterNot { it.isEmpty }

    /**
     * What the booking's status becomes: the least settled of the party.
     *
     * Mirrors [Train.effectiveBookingStatus] so the stored column agrees with what every screen
     * derives from the list, rather than going stale the first time someone is confirmed.
     */
    val partyStatus: TrainBookingStatus
        get() = filledPassengers.map { it.status }
            .filter { it != TrainBookingStatus.NOT_BOOKED }
            .minByOrNull { it.settledness }
            ?: bookingStatus

    /**
     * Whether the "fetch booking status" affordance should work.
     *
     * A PNR is exactly ten digits, the lookup has to be configured, and one must not already be
     * in flight. The button is hidden entirely when [isPnrLookupAvailable] is false, so this only
     * gates the enabled state once it is shown at all.
     */
    val canFetchPnr: Boolean
        get() = isPnrLookupAvailable && pnr.length == 10 && !isFetchingPnr
}

@HiltViewModel
class TrainEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trainRepository: TrainRepository,
    private val eventRepository: EventRepository,
    private val tripRepository: TripRepository,
    private val ticketImportService: TicketImportService,
    private val pnrLookupService: PnrLookupService,
    private val journeyEventLinker: JourneyEventLinker,
    private val timeProvider: TimeProvider
) : ViewModel() {

    private val tripId: Long = savedStateHandle.get<String>("tripId")?.toLongOrNull() ?: 0L
    private val editingId: Long? = savedStateHandle.get<String>("trainId")?.toLongOrNull()

    private val _state = MutableStateFlow(
        TrainEditorUiState(
            tripId = tripId,
            journeyDate = timeProvider.now().toLocalDate(),
            isPnrLookupAvailable = pnrLookupService.isAvailable
        )
    )
    val state: StateFlow<TrainEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val events = eventRepository.getEventsForTrip(tripId).first()
                .filter { it.type == EventType.JOURNEY }
            val existing = editingId?.let { trainRepository.getTrainByIdOnce(it) }
            // A new train on a trip that has not started yet should open on the trip's first
            // day, not on today — today is usually weeks earlier and every field would need
            // correcting by hand.
            val trip = tripRepository.getTripById(tripId).first()
            val fallbackDate = trip?.startDate ?: timeProvider.now().toLocalDate()

            _state.update { current ->
                if (existing == null) {
                    current.copy(
                        journeyEvents = events,
                        journeyDate = fallbackDate,
                        isLoading = false
                    )
                } else {
                    current.copy(
                        trainId = existing.id,
                        number = existing.number,
                        name = existing.name,
                        originCode = existing.originCode,
                        originName = existing.originName,
                        destinationCode = existing.destinationCode,
                        destinationName = existing.destinationName,
                        journeyDate = existing.departureTime.toLocalDate(),
                        departureTime = existing.departureTime.toLocalTime(),
                        arrivalTime = existing.arrivalTime.toLocalTime(),
                        arrivesNextDay = existing.arrivalTime.toLocalDate()
                            .isAfter(existing.departureTime.toLocalDate()),
                        travelClass = existing.travelClass,
                        pnr = existing.pnr,
                        platform = existing.platform,
                        passengers = existing.passengers.map { it.toDraft() },
                        bookingStatus = existing.bookingStatus,
                        notes = existing.notes,
                        journeyEvents = events,
                        linkedEventId = existing.eventId,
                        source = existing,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun setNumber(value: String) = _state.update { it.copy(number = value.filter { c -> c.isDigit() }.take(5)) }
    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setOriginCode(value: String) = _state.update { it.copy(originCode = stationCode(value)) }
    fun setOriginName(value: String) = _state.update { it.copy(originName = value) }
    fun setDestinationCode(value: String) = _state.update { it.copy(destinationCode = stationCode(value)) }
    fun setDestinationName(value: String) = _state.update { it.copy(destinationName = value) }
    fun setJourneyDate(value: LocalDate) = _state.update { it.copy(journeyDate = value) }
    fun setDepartureTime(value: LocalTime) = _state.update { it.copy(departureTime = value) }
    fun setArrivalTime(value: LocalTime) = _state.update { it.copy(arrivalTime = value) }
    fun setArrivesNextDay(value: Boolean) = _state.update { it.copy(arrivesNextDay = value) }
    fun setTravelClass(value: String) = _state.update { it.copy(travelClass = value) }
    fun setPnr(value: String) = _state.update { it.copy(pnr = value.filter { c -> c.isDigit() }.take(10)) }
    fun setPlatform(value: String) = _state.update { it.copy(platform = value) }
    fun setBookingStatus(value: TrainBookingStatus) = _state.update { it.copy(bookingStatus = value) }
    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    // ── The party ──────────────────────────────────────────────────────────────

    /**
     * Adds a traveller, starting from where the last one sits.
     *
     * A party allotted together shares a coach and a status, so copying both from the previous
     * row saves the typing that is right most of the time and is one field to correct when it
     * is not. The berth is never copied: two people cannot have the same one.
     */
    fun addPassenger() = _state.update { current ->
        val previous = current.passengers.lastOrNull()
        current.copy(
            passengers = current.passengers + PassengerDraft(
                coach = previous?.coach.orEmpty(),
                status = previous?.status ?: current.bookingStatus.orConfirmed()
            )
        )
    }

    fun removePassenger(index: Int) = _state.update { current ->
        if (index !in current.passengers.indices) {
            current
        } else {
            current.copy(passengers = current.passengers.filterIndexed { i, _ -> i != index })
        }
    }

    fun setPassengerName(index: Int, value: String) = editPassenger(index) { it.copy(name = value) }

    fun setPassengerAge(index: Int, value: String) = editPassenger(index) {
        it.copy(age = value.filter { c -> c.isDigit() }.take(3))
    }

    fun setPassengerGender(index: Int, value: PassengerGender) =
        editPassenger(index) { it.copy(gender = value) }

    /** Coach codes are short and always uppercase: `S4`, `B2`, `H1`, `GS`. */
    fun setPassengerCoach(index: Int, value: String) = editPassenger(index) {
        it.copy(coach = value.filter { c -> c.isLetterOrDigit() }.uppercase().take(4))
    }

    fun setPassengerBerth(index: Int, value: String) = editPassenger(index) {
        it.copy(berth = value.filter { c -> c.isDigit() }.take(3))
    }

    fun setPassengerBerthType(index: Int, value: BerthType) =
        editPassenger(index) { it.copy(berthType = value) }

    /**
     * Sets one person's status, clearing the queue position once they leave the queue.
     *
     * A passenger moved from waitlisted to confirmed keeping "24" in a hidden field would come
     * back as `CNF/24` on the next save, which is not a berth and not a queue either.
     */
    fun setPassengerStatus(index: Int, value: TrainBookingStatus) = editPassenger(index) { draft ->
        val leavingQueue = draft.isQueued &&
            value != TrainBookingStatus.WAITLISTED && value != TrainBookingStatus.RAC
        draft.copy(
            status = value,
            queuePosition = if (leavingQueue) "" else draft.queuePosition,
            queueKind = if (leavingQueue) "" else draft.queueKind
        )
    }

    fun setPassengerQueuePosition(index: Int, value: String) = editPassenger(index) {
        it.copy(queuePosition = value.filter { c -> c.isDigit() }.take(4))
    }

    private fun editPassenger(index: Int, block: (PassengerDraft) -> PassengerDraft) =
        _state.update { current ->
            if (index !in current.passengers.indices) {
                current
            } else {
                current.copy(
                    passengers = current.passengers.mapIndexed { i, draft ->
                        if (i == index) block(draft) else draft
                    }
                )
            }
        }

    /** Linking is a toggle: tapping the already-linked journey clears it. */
    fun toggleLinkedEvent(eventId: Long) = _state.update {
        it.copy(linkedEventId = if (it.linkedEventId == eventId) null else eventId)
    }

    // ── Importing an e-ticket ──────────────────────────────────────────────────

    /**
     * Fills the form from a ticket PDF.
     *
     * [read] is a suspending function rather than the bytes themselves so that opening the
     * user's chosen file happens inside [viewModelScope]: a picker result arrives during
     * recomposition, and a read started in a composable's scope is cancelled by a rotation
     * halfway through. It also keeps `Context` out of this class, so the whole import path
     * above the file read is testable on a plain JVM.
     *
     * Nothing is saved. Every field the ticket supplies lands in the form the user is already
     * looking at, and the same Save button applies — which is the point of importing into the
     * editor rather than building a separate preview screen that would have to grow its own
     * copy of the same validation.
     */
    fun importTicket(read: suspend () -> ByteArray?) {
        if (_state.value.isImporting) return
        _state.update {
            it.copy(
                isImporting = true,
                importError = null,
                importSummary = null,
                importWarnings = emptyList()
            )
        }
        viewModelScope.launch {
            val bytes = try {
                read()
            } catch (_: Exception) {
                null
            }
            if (bytes == null) {
                _state.update {
                    it.copy(isImporting = false, importError = TicketImportError.UNREADABLE)
                }
                return@launch
            }
            when (val outcome = ticketImportService.readTicket(bytes)) {
                is TicketImportOutcome.Failed ->
                    _state.update { it.copy(isImporting = false, importError = outcome.error) }

                is TicketImportOutcome.Parsed ->
                    _state.update { it.applying(outcome.ticket) }
            }
        }
    }

    fun dismissImportNotice() = _state.update {
        it.copy(importSummary = null, importWarnings = emptyList(), importError = null)
    }

    // ── Fetching booking status by PNR ──────────────────────────────────────────

    /**
     * Fills the form from a PNR lookup.
     *
     * The RailRadar twin of [importTicket]: the booking behind the PNR arrives as the same
     * [ParsedTicket] a scanned e-ticket produces, lands in the same editable controls, and the
     * same Save applies. A PNR is thinner than a PDF, though — it brings the train, the route
     * ends, the boarding point and each passenger's coach, berth and status, but no names and no
     * clock times — so [withParsedTicket] leaves the dates alone and [pnrSuccessText] says out
     * loud that the names are still to fill.
     *
     * Nothing happens without a usable PNR: [TrainEditorUiState.canFetchPnr] gates the button and
     * this rechecks it, so a lookup is never spent on a number that cannot succeed.
     */
    fun fetchPnr() {
        val current = _state.value
        if (current.isFetchingPnr || !current.canFetchPnr) return
        val pnr = current.pnr
        _state.update {
            it.copy(
                isFetchingPnr = true,
                pnrError = null,
                pnrSummary = null,
                importSummary = null,
                importWarnings = emptyList(),
                importError = null
            )
        }
        viewModelScope.launch {
            when (val outcome = pnrLookupService.lookup(pnr)) {
                is PnrLookupOutcome.Failed ->
                    _state.update { it.copy(isFetchingPnr = false, pnrError = pnrErrorText(outcome.error)) }

                is PnrLookupOutcome.Found ->
                    _state.update {
                        it.withParsedTicket(outcome.ticket).copy(
                            isFetchingPnr = false,
                            pnrError = null,
                            pnrSummary = pnrSuccessText(outcome.ticket)
                        )
                    }
            }
        }
    }

    fun dismissPnrNotice() = _state.update { it.copy(pnrSummary = null, pnrError = null) }

    /**
     * What a landed PNR says, admitting the gap.
     *
     * The seats came across; the names did not, because Indian Railways does not expose them over
     * a PNR — so the sentence points the user at the rows that still need a name rather than
     * letting a "3 passengers" leave them thinking the import was complete.
     */
    private fun pnrSuccessText(ticket: ParsedTicket): String {
        val count = ticket.passengers.size
        return if (count == 0) {
            "Fetched the booking. It listed no passengers — add them by hand."
        } else {
            val people = if (count == 1) "passenger" else "passengers"
            "Filled in from the PNR — $count $people. Coach, berth and status came across; a PNR " +
                "carries no names, so add those."
        }
    }

    /** A finished sentence for each lookup failure. The screen only chooses the colour. */
    private fun pnrErrorText(error: PnrLookupError): String = when (error) {
        PnrLookupError.NOT_CONFIGURED -> "Live lookups need a RailRadar key."
        PnrLookupError.NETWORK_UNAVAILABLE -> "Couldn't reach RailRadar. Check your connection."
        PnrLookupError.TIMEOUT -> "RailRadar didn't answer in time. Try again."
        PnrLookupError.RATE_LIMITED -> "RailRadar's request limit was reached. Give it a minute."
        PnrLookupError.NOT_FOUND -> "No booking found for that PNR."
        PnrLookupError.MALFORMED -> "RailRadar sent something we couldn't read."
        PnrLookupError.UNKNOWN -> "Couldn't fetch that PNR."
    }

    fun save(onSaved: (Long) -> Unit) {
        val current = _state.value
        if (!current.canSave || current.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                // Built from the record the form was opened on, not from scratch: the fare, the
                // quota, the agent's booking id and the date the ticket was bought have no
                // control on this screen, and a fresh `Train(...)` would blank every one of them
                // on the first edit. Also keeps `createdAt` as the date it really was.
                val blank = Train(
                    tripId = current.tripId,
                    number = current.number,
                    name = "",
                    originCode = "",
                    originName = "",
                    destinationCode = "",
                    destinationName = "",
                    departureTime = current.departure,
                    arrivalTime = current.arrival
                )
                val train = (current.source ?: blank).copy(
                    id = current.trainId ?: 0L,
                    tripId = current.tripId,
                    eventId = current.linkedEventId,
                    number = current.number,
                    name = current.name.trim(),
                    originCode = current.originCode,
                    originName = current.originName.trim(),
                    destinationCode = current.destinationCode,
                    destinationName = current.destinationName.trim(),
                    departureTime = current.departure,
                    arrivalTime = current.arrival,
                    travelClass = current.travelClass.trim(),
                    pnr = current.pnr,
                    bookingStatus = current.partyStatus,
                    platform = current.platform.trim(),
                    notes = current.notes.trim(),
                    passengers = current.filledPassengers.mapIndexed { index, draft ->
                        draft.toPassenger(serialNo = index + 1)
                    }
                )
                // A train is a part of the itinerary, not a thing beside it, so the JOURNEY event
                // is settled before the booking is written and the booking carries the link. The
                // event's times follow the ticket from here on; its title stays the user's once
                // they have touched it. See [JourneyEventLinker].
                val linked = train.copy(
                    eventId = journeyEventLinker.syncEvent(train, previous = current.source)
                )
                val id = if (current.trainId == null) {
                    trainRepository.insertTrain(linked)
                } else {
                    trainRepository.updateTrain(linked)
                    current.trainId
                }
                onSaved(id)
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = e.message ?: "Could not save this train.", isSaving = false)
                }
            }
        }
    }

    /** Station codes are three or four uppercase letters; anything else is a typo, not a code. */
    private fun stationCode(raw: String): String =
        raw.filter { it.isLetter() }.uppercase().take(4)
}

/** A booking with no status is not a state the editor offers; confirmed is the sane default. */
private fun TrainBookingStatus.orConfirmed(): TrainBookingStatus =
    if (this == TrainBookingStatus.NOT_BOOKED) TrainBookingStatus.CONFIRMED else this

/**
 * The form, with everything a parsed booking had to say written into it.
 *
 * Shared by the e-ticket import and the PNR lookup, because both arrive as a [ParsedTicket] and
 * both fill the same form — the only difference is the sentence each shows afterwards, which is
 * its caller's to add. This function sets no notice fields, so neither source can leave the
 * other's confirmation on screen.
 *
 * Field by field rather than wholesale: a blank on the source never overwrites something the
 * user already typed. Someone who filled in the train number by hand and then imported the PDF
 * to save typing the party keeps their number if the parse missed it.
 *
 * The dates are the delicate part. A source that carried no arrival time — every PNR, and the
 * older ticket layout — leaves the arrival fields exactly as they were, which usually means the
 * arrival is no longer after the departure and `canSave` refuses, deliberately. That is the one
 * thing on this form the source could not tell us, and inventing it would put a countdown on the
 * Home screen against a moment nobody chose.
 *
 * [TrainEditorUiState.source] becomes the parsed booking, so the fare, the quota, the agent's
 * booking id and the transaction id ride along to the save even though this form has no control
 * for any of them. The row's own identity does not: id, creation date and the delay someone
 * entered from a platform announcement all belong to the record, not to the document.
 */
private fun TrainEditorUiState.withParsedTicket(ticket: ParsedTicket): TrainEditorUiState {
    val date = ticket.departure?.toLocalDate() ?: journeyDate
    val depTime = ticket.departure?.toLocalTime() ?: departureTime
    val arrTime = ticket.arrival?.toLocalTime() ?: arrivalTime
    val nextDay = ticket.arrival?.toLocalDate()?.isAfter(date) ?: arrivesNextDay
    val party = ticket.passengers.map { it.toDraft() }

    val imported = ticket.toTrain(
        tripId = tripId,
        departure = LocalDateTime.of(date, depTime),
        arrival = LocalDateTime.of(if (nextDay) date.plusDays(1) else date, arrTime),
        eventId = linkedEventId
    )

    return copy(
        number = ticket.trainNumber.ifBlank { number },
        name = ticket.trainName.ifBlank { name },
        originCode = ticket.originCode.ifBlank { originCode },
        originName = ticket.originName.ifBlank { originName },
        destinationCode = ticket.destinationCode.ifBlank { destinationCode },
        destinationName = ticket.destinationName.ifBlank { destinationName },
        journeyDate = date,
        departureTime = depTime,
        arrivalTime = arrTime,
        arrivesNextDay = nextDay,
        travelClass = ticket.travelClass.ifBlank { travelClass },
        pnr = ticket.pnr.ifBlank { pnr },
        passengers = party.ifEmpty { passengers },
        bookingStatus = if (party.isEmpty()) bookingStatus else imported.bookingStatus,
        source = imported.copy(
            id = source?.id ?: 0L,
            knownDelayMinutes = source?.knownDelayMinutes ?: 0,
            createdAt = source?.createdAt ?: imported.createdAt
        )
    )
}

/**
 * [withParsedTicket] plus the e-ticket's own confirmation and warnings.
 *
 * Clears any PNR notice as it lands, so the two sources never stack their sentences on screen.
 */
private fun TrainEditorUiState.applying(ticket: ParsedTicket): TrainEditorUiState {
    val count = ticket.passengers.size
    val issuer = ticket.agentName.takeIf { it.isNotBlank() }?.let { "$it e-ticket" } ?: "e-ticket"
    val summary = if (count == 0) {
        "Filled in from the $issuer."
    } else {
        "Filled in from the $issuer — $count " + if (count == 1) "passenger." else "passengers."
    }

    return withParsedTicket(ticket).copy(
        isImporting = false,
        importError = null,
        importSummary = summary,
        importWarnings = ticket.warnings,
        pnrSummary = null,
        pnrError = null
    )
}

/** How the form shows someone the database already knows about. */
private fun TrainPassenger.toDraft(): PassengerDraft = PassengerDraft(
    name = name,
    age = age?.toString().orEmpty(),
    gender = gender,
    coach = allotment.coach,
    berth = allotment.berth,
    berthType = allotment.berthType,
    status = allotment.status,
    queuePosition = allotment.queuePosition?.toString().orEmpty(),
    queueKind = allotment.queueKind,
    bookingStatusText = bookingStatusText,
    currentStatusText = currentStatusText
)

/**
 * The form's version of a traveller, back to the one the database stores.
 *
 * [serialNo] comes from the position in the list rather than the draft, because that is the only
 * place it can come from once a row has been deleted — a party of three that loses its second
 * member is numbered 1, 2, not 1, 3.
 *
 * The railway's own status string survives an edit that did not change anything. It is rewritten
 * only once the allotment actually differs, and blanked when there is nothing left to describe:
 * a hand-entered passenger has no e-ticket behind them, and `"CNF"` on its own is a string the
 * railway never printed.
 */
private fun PassengerDraft.toPassenger(serialNo: Int): TrainPassenger {
    val next = allotment
    // `describesSameAs` rather than `==`: the current-status column often leaves the berth type
    // to the booked one beside it, and rewriting `"CNF/S4/8"` as `"CNF/S4/8/SU"` on a save that
    // touched nothing would replace what was printed with the app's own paraphrase of it.
    val current = when {
        TrainAllotment.parse(currentStatusText).describesSameAs(next) -> currentStatusText
        next.hasSeat || next.queuePosition != null -> next.text
        else -> ""
    }
    return TrainPassenger(
        serialNo = serialNo,
        name = name.trim(),
        age = age.trim().toIntOrNull(),
        gender = gender,
        allotment = next,
        bookingStatusText = bookingStatusText,
        currentStatusText = current
    )
}
