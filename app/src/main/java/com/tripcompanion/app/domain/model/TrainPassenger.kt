package com.tripcompanion.app.domain.model

/**
 * One person on one ticket.
 *
 * A reservation is per passenger, not per booking: two people on the same PNR routinely get
 * different berths, and occasionally different coaches, because the quota is allotted seat by
 * seat. That is why the coach and berth live here and not on [Train] — a single pair of fields
 * on the booking could only ever be right for one of them.
 *
 * [serialNo] is the `#` column on the e-ticket, kept so the app lists people in the order the
 * railway does. It is the number the TTE reads off the chart.
 *
 * [bookingStatusText] and [currentStatusText] are the two allotment columns verbatim
 * (`"CNF/S3/56/SU"`, `"CNF/S3/56/NA"`). They are stored alongside the parsed [allotment]
 * because the pair carries information the parse cannot: someone booked on the waitlist who
 * has since been confirmed wants to see *both*, and a string this app failed to understand is
 * still worth showing to the person holding the ticket. Both are empty for a passenger entered
 * by hand, who has no e-ticket behind them.
 */
data class TrainPassenger(
    val id: Long = 0,
    val trainId: Long = 0,
    val serialNo: Int = 1,
    val name: String = "",
    /** Null for a passenger whose age was never recorded, which is not the same as zero. */
    val age: Int? = null,
    val gender: PassengerGender = PassengerGender.UNSPECIFIED,
    /** Where this person actually sits, as far as anything known says. */
    val allotment: TrainAllotment = TrainAllotment(),
    val bookingStatusText: String = "",
    val currentStatusText: String = ""
) {

    /** `"Riddhi · 21 · Female"`, dropping whichever parts are missing. */
    val displayLine: String
        get() = buildList {
            if (name.isNotBlank()) add(name)
            age?.let { add(it.toString()) }
            if (gender != PassengerGender.UNSPECIFIED) add(gender.label)
        }.joinToString(" · ")

    /**
     * True when the allotment changed between booking and the chart.
     *
     * The one case worth a second line on screen: waitlisted then confirmed, or moved to a
     * different berth. When the two columns say the same thing, saying it twice is noise — and
     * a berth type that only one of them named is them saying the same thing, which is why this
     * asks [TrainAllotment.describesSameAs] rather than comparing the strings.
     */
    val allotmentChanged: Boolean
        get() = bookingStatusText.isNotBlank() &&
            currentStatusText.isNotBlank() &&
            !TrainAllotment.parse(bookingStatusText)
                .describesSameAs(TrainAllotment.parse(currentStatusText))
}

/**
 * As the railway records it.
 *
 * Three values plus "not recorded", because IRCTC accepts exactly Male, Female and
 * Transgender, and a passenger typed in by hand may simply not have been asked.
 */
enum class PassengerGender(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    TRANSGENDER("Transgender"),
    UNSPECIFIED("");

    companion object {
        /**
         * Reads whatever the ticket printed.
         *
         * E-tickets have used the full word and the single letter at different times, so both
         * are accepted. Anything else is [UNSPECIFIED] rather than a guess.
         */
        fun parse(raw: String): PassengerGender = when (raw.trim().uppercase()) {
            "MALE", "M" -> MALE
            "FEMALE", "F" -> FEMALE
            "TRANSGENDER", "T" -> TRANSGENDER
            else -> UNSPECIFIED
        }
    }
}

/**
 * The kind of berth or seat, from the two-letter code on the allotment.
 *
 * Worth spelling out rather than showing the code: "SU" tells a regular traveller they are on
 * the side upper and tells everyone else nothing, and the difference between a side upper and
 * a lower berth is most of how a night on a train feels.
 */
enum class BerthType(val code: String, val label: String, private val aliases: List<String> = emptyList()) {
    LOWER("LB", "Lower berth"),
    MIDDLE("MB", "Middle berth"),
    UPPER("UB", "Upper berth"),
    SIDE_LOWER("SL", "Side lower"),
    SIDE_UPPER("SU", "Side upper"),
    WINDOW_SEAT("WS", "Window seat"),
    MIDDLE_SEAT("MS", "Middle seat"),
    AISLE_SEAT("AS", "Aisle seat", aliases = listOf("AB")),
    CABIN("CB", "Cabin"),
    COUPE("CP", "Coupe"),

    /**
     * No berth type in the string, or one this app does not know.
     *
     * Reached often and legitimately: the Current Status column prints `NA` once the chart is
     * made, and a waitlisted passenger has no berth to have a type.
     */
    UNKNOWN("", "");

    val isKnown: Boolean get() = this != UNKNOWN

    companion object {
        /** `"SU"` → [SIDE_UPPER]. `"NA"`, `""` and anything unrecognised → [UNKNOWN]. */
        fun parse(raw: String): BerthType {
            val token = raw.trim().uppercase()
            if (token.isEmpty() || token == "NA") return UNKNOWN
            return entries.firstOrNull { it.isKnown && (it.code == token || token in it.aliases) }
                ?: UNKNOWN
        }
    }
}
