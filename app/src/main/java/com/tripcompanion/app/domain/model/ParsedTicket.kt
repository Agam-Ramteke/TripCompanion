package com.tripcompanion.app.domain.model

import java.time.LocalDateTime

/**
 * A train booking as read off an e-ticket PDF, before anyone has agreed it is right.
 *
 * Deliberately not a [Train]. A [Train] is a record the app stands behind — its departure and
 * arrival are non-null because every screen draws a countdown from them — whereas this is a
 * reading of a document, and a reading can be incomplete. The one field that is genuinely
 * unreliable is the arrival time: some renderings print the destination's *name* in the cell
 * where the arrival time belongs, so [arrival] is nullable and the import screen asks for it
 * rather than inventing one. Making that impossible to ignore is the entire reason this type
 * exists instead of the parser returning a half-filled [Train].
 *
 * [warnings] is what the import screen tells the user before they save. Everything here is
 * editable on that screen; the parse is a first draft, not an authority.
 */
data class ParsedTicket(
    val pnr: String = "",
    val trainNumber: String = "",
    val trainName: String = "",
    val travelClass: String = "",
    val quota: String = "",
    val distanceKm: Int = 0,
    val originCode: String = "",
    val originName: String = "",
    val destinationCode: String = "",
    val destinationName: String = "",
    /** Where the party actually boards, when the ticket names a different station. */
    val boardingCode: String = "",
    val boardingName: String = "",
    val departure: LocalDateTime? = null,
    val arrival: LocalDateTime? = null,
    val bookedAt: LocalDateTime? = null,
    val agentName: String = "",
    val agentBookingId: String = "",
    val transactionId: String = "",
    val fare: TrainFare = TrainFare(),
    val passengers: List<TrainPassenger> = emptyList(),
    val warnings: List<TicketImportWarning> = emptyList()
) {

    /** True when nothing needs to be filled in by hand before this can be saved. */
    val isComplete: Boolean get() = departure != null && arrival != null && passengers.isNotEmpty()

    /**
     * The minimum that makes a parse worth showing at all.
     *
     * A PNR or a train number. Without one of them the document was not a reservation, whatever
     * else the extractor found on it, and offering an empty form to correct is worse than saying
     * the file could not be read.
     */
    val hasSubstance: Boolean get() = pnr.isNotBlank() || trainNumber.isNotBlank()

    /**
     * Turns the reading into a booking.
     *
     * [departure] and [arrival] are parameters rather than being taken from this object because
     * they may be the two things this object does not know. The caller has either confirmed the
     * parsed values or collected them from the user; either way the decision is made before this
     * is called, not inside it.
     */
    fun toTrain(
        tripId: Long,
        departure: LocalDateTime,
        arrival: LocalDateTime,
        eventId: Long? = null
    ): Train = Train(
        tripId = tripId,
        eventId = eventId,
        number = trainNumber,
        name = trainName,
        originCode = originCode,
        originName = originName,
        destinationCode = destinationCode,
        destinationName = destinationName,
        departureTime = departure,
        arrivalTime = arrival,
        travelClass = travelClass,
        pnr = pnr,
        bookingStatus = passengers.map { it.allotment.status }
            .filter { it != TrainBookingStatus.NOT_BOOKED }
            .minByOrNull { it.settledness }
            ?: TrainBookingStatus.NOT_BOOKED,
        quota = quota,
        distanceKm = distanceKm,
        boardingCode = boardingCode,
        boardingName = boardingName,
        bookedAt = bookedAt,
        agentName = agentName,
        agentBookingId = agentBookingId,
        transactionId = transactionId,
        fare = fare,
        passengers = passengers
    )
}

/**
 * Something the parse could not settle, in the user's terms rather than the parser's.
 *
 * An enum and not a sentence so the wording lives with the screen that shows it, matching how
 * [com.tripcompanion.app.domain.service.LocationSearchError] is handled. Each of these is a
 * real gap seen on a real ticket, not a defensive placeholder.
 */
enum class TicketImportWarning {
    /** The departure cell held something that was not a time. */
    DEPARTURE_TIME_MISSING,

    /**
     * The arrival cell held a station name instead of a time.
     *
     * Observed on the older of the two ticket layouts, which prints `Arrival* <station>` when
     * the arrival is on a later day. The user has to supply it.
     */
    ARRIVAL_TIME_MISSING,

    /** The passenger table was found but no rows could be read from it. */
    NO_PASSENGERS_FOUND,

    /** Some of the fare breakdown was unreadable, so the total may not match its parts. */
    FARE_INCOMPLETE,

    /** Station names were read but their codes were not printed on this layout. */
    STATION_CODES_MISSING
}
