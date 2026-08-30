package com.tripcompanion.app.domain.model

import java.time.LocalDateTime

/**
 * A train the traveller is actually on.
 *
 * This is a *booking*, not a timetable entry. [number] and [name] identify the service,
 * everything from [travelClass] down is personal to this journey, and [TrainStop] carries
 * the timetable separately — so two people on the same train have two [Train] rows and one
 * schedule.
 *
 * [passengers] is the reservation itself. A PNR covers a party, each member with their own
 * berth, so the coach and berth are on [TrainPassenger] and this class only summarises them.
 * The list is assembled by the repository from a second table; [toEntity] drops it, the way
 * [TrainRunStatus] drops its stop rows.
 *
 * [eventId] links back to the JOURNEY event on the itinerary when there is one. It is
 * nullable because the two are added in either order: a train can be recorded before the
 * itinerary is built, and a JOURNEY event can exist before the ticket is booked. Home reads
 * this link to show the real coach and berths on its transit card instead of guessing.
 *
 * [platform] is user-entered. The live-status API does not carry a platform number, and
 * inventing one for a screen someone reads while running for a train would be worse than
 * leaving it blank.
 *
 * Everything from [quota] to [transactionId] exists because it is printed on an e-ticket and
 * this app can read one. None of it is required: a booking typed in by hand carries none of
 * it and every screen treats each field as absent rather than blank.
 */
data class Train(
    val id: Long = 0,
    val tripId: Long,
    val eventId: Long? = null,
    val number: String,
    val name: String,
    val originCode: String,
    val originName: String,
    val destinationCode: String,
    val destinationName: String,
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val actualBoardingTime: LocalDateTime? = null,
    val actualDepartureTime: LocalDateTime? = null,
    val actualArrivalTime: LocalDateTime? = null,
    val arrivalSource: String? = null,
    val travelClass: String = "",
    val pnr: String = "",
    val bookingStatus: TrainBookingStatus = TrainBookingStatus.NOT_BOOKED,
    val platform: String = "",
    /**
     * Delay in minutes as the traveller knows it, for the offline projection.
     *
     * Set from the platform announcement when there is no live data. Ignored entirely when
     * a live provider is answering, because then the API's own delay is the better number.
     */
    val knownDelayMinutes: Int = 0,
    val notes: String = "",

    // ── From the e-ticket ──

    /** `"General"`, `"Tatkal"`, `"Ladies"` — the printed word, its shouting undone and nothing else. */
    val quota: String = "",
    /** Booked distance in kilometres. Zero means not known, not a zero-kilometre journey. */
    val distanceKm: Int = 0,
    /**
     * Where the party actually gets on, when that is not [originCode].
     *
     * A ticket booked from an earlier station than the one you board at is ordinary — it is how
     * a full quota gets worked around — and the departure time printed on the ticket is the
     * boarding point's, not the booked-from station's. Blank when the two are the same.
     */
    val boardingCode: String = "",
    val boardingName: String = "",
    /** When the ticket was bought. Nullable: only an imported ticket knows. */
    val bookedAt: LocalDateTime? = null,
    /** `"IRCTC"`, `"MakeMyTrip"`, `"Goibibo"` — whoever issued it. */
    val agentName: String = "",
    val agentBookingId: String = "",
    val transactionId: String = "",
    val fare: TrainFare = TrainFare(),
    val passengers: List<TrainPassenger> = emptyList(),

    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
) {
    /** `"12978 · Mewar Express"`, or just the number when the name is unknown. */
    val displayTitle: String
        get() = if (name.isBlank()) number else "$number · $name"

    /**
     * The coaches this party is in — usually one, occasionally two.
     *
     * `"S4"` for a party allotted together, `"S3, S4"` when the quota split them. Empty when
     * nothing has been allotted yet.
     */
    val coachSummary: String
        get() = passengers.map { it.allotment.coach }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

    /** `"6, 8"` — every berth on the booking, in the order the passengers are listed. */
    val berthSummary: String
        get() = passengers.map { it.allotment.berth }
            .filter { it.isNotBlank() }
            .joinToString(", ")

    /** `"Riddhi, Agam"` — first names only, which is what fits on a card. */
    val passengerSummary: String
        get() = passengers.mapNotNull { p -> p.name.trim().split(' ').firstOrNull()?.takeIf { it.isNotBlank() } }
            .joinToString(", ")

    /**
     * True when the party boards somewhere other than the station the ticket was booked from.
     *
     * Worth its own line on the ticket screen: getting on at the booked-from station out of
     * habit, when the reservation starts further down the line, is a missed train.
     */
    val boardsElsewhere: Boolean
        get() = boardingCode.isNotBlank() &&
            !boardingCode.equals(originCode, ignoreCase = true) &&
            boardingName.isNotBlank()

    /** Where to actually stand, with its code — the boarding point if there is one, else the origin. */
    val boardingPointName: String
        get() = if (boardsElsewhere) boardingName else originName

    /**
     * The state of the booking as a whole: the least settled of its passengers.
     *
     * Derived rather than read from [bookingStatus] whenever there are passengers, so a badge
     * can never disagree with the list underneath it. The *least* settled on purpose — one
     * person still waitlisted is the thing the trip has to be planned around, even if everyone
     * else is confirmed.
     */
    val effectiveBookingStatus: TrainBookingStatus
        get() = passengers.map { it.allotment.status }
            .filter { it != TrainBookingStatus.NOT_BOOKED }
            .minByOrNull { it.settledness }
            ?: bookingStatus
}

/**
 * Where the booking stands.
 *
 * Distinct from [EventStatus], which is about time. A confirmed ticket on a train that has
 * already arrived is CONFIRMED and COMPLETED at once, and the ticket screen needs the first
 * while the itinerary needs the second.
 *
 * [allotmentCode] is the railway's own abbreviation, so [TrainAllotment] can read these values
 * off a ticket and write them back in the same form.
 */
enum class TrainBookingStatus(val allotmentCode: String, val settledness: Int) {
    /** No ticket yet — the train is on the plan but not booked. */
    NOT_BOOKED("", settledness = 1),
    CONFIRMED("CNF", settledness = 4),

    /** Reservation Against Cancellation: a seat is shared until someone cancels. */
    RAC("RAC", settledness = 3),
    WAITLISTED("WL", settledness = 2),
    CANCELLED("CAN", settledness = 0);

    companion object {
        /**
         * Reads the leading token of an allotment string.
         *
         * Every waitlist flavour ends in `WL` — `GNWL` general, `RLWL` remote location, `PQWL`
         * pooled quota, `TQWL`/`CKWL` tatkal, `RSWL` road-side — and they all mean the same
         * thing to this app, so the suffix is matched rather than the whole set enumerated.
         * An unrecognised token is [NOT_BOOKED], never a guess.
         */
        fun fromAllotmentCode(raw: String): TrainBookingStatus {
            val code = raw.trim().uppercase()
            return when {
                code.isEmpty() -> NOT_BOOKED
                code == "CNF" || code == "CONFIRMED" -> CONFIRMED
                code == "RAC" -> RAC
                code.endsWith("WL") -> WAITLISTED
                code.startsWith("CAN") -> CANCELLED
                else -> NOT_BOOKED
            }
        }
    }
}
