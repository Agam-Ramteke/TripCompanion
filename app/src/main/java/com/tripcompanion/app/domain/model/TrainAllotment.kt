package com.tripcompanion.app.domain.model

/**
 * Where a passenger sits, parsed out of the railway's own shorthand.
 *
 * An e-ticket prints the allotment as one slash-separated string — `"CNF/S3/56/SU"` — and it is
 * the only place the coach and berth appear. Reading it is therefore unavoidable, and it has to
 * be read tolerantly: the same column carries `"CNF/S4/8"` with no berth type, `"CNF/S3/56/NA"`
 * with the berth type explicitly absent, `"GNWL/24"` for a waitlist position, and `"CAN/MOD"`
 * for a cancellation. Every one of those is a real string off a real ticket.
 *
 * [queuePosition] is deliberately not stored in [berth]. On a waitlisted ticket the number
 * after the slash is a place in a queue, not a berth, and rendering it as "Berth 24" would tell
 * someone they have a seat when they do not.
 *
 * This is a value object rather than a set of columns on [TrainPassenger] so that the parse and
 * the rendering of an allotment live in one place. It is flattened back out to columns by the
 * entity mapper.
 */
data class TrainAllotment(
    val status: TrainBookingStatus = TrainBookingStatus.NOT_BOOKED,
    val coach: String = "",
    val berth: String = "",
    val berthType: BerthType = BerthType.UNKNOWN,
    /** Place in the RAC or waitlist queue, when the allotment is a queue position. */
    val queuePosition: Int? = null,
    /** The queue this position is in — `"GNWL"`, `"RLWL"`, `"PQWL"`, `"RAC"` — verbatim. */
    val queueKind: String = ""
) {

    /** True once there is a specific place on the train to go to. */
    val hasSeat: Boolean get() = coach.isNotBlank() || berth.isNotBlank()

    /** `"S3 · 56"`, or just whichever half is known, or empty. */
    val seatLabel: String
        get() = listOf(coach, berth).filter { it.isNotBlank() }.joinToString(" · ")

    /** `"Waitlist 24 · GNWL"`, or `"RAC 12"`, or empty when there is no queue position. */
    val queueLabel: String
        get() = when {
            queuePosition == null -> ""
            queueKind.equals("RAC", ignoreCase = true) -> "RAC $queuePosition"
            queueKind.isBlank() -> "Waitlist $queuePosition"
            else -> "Waitlist $queuePosition · $queueKind"
        }

    /**
     * Back to the railway's own form, so a hand-entered allotment renders like a printed one.
     *
     * Round-trips with [parse] for every allotment that has a status. An allotment with no
     * status is not something the railway would ever print, so it comes back empty rather than
     * as a seat with an invented `CNF` in front of it — screens show [seatLabel] for those.
     */
    val text: String
        get() = when {
            status == TrainBookingStatus.NOT_BOOKED -> ""

            queuePosition != null -> listOf(queueKind.ifBlank { status.allotmentCode }, queuePosition.toString())
                .joinToString("/")

            hasSeat -> buildList {
                add(status.allotmentCode)
                add(coach)
                add(berth)
                if (berthType.isKnown) add(berthType.code)
            }.filter { it.isNotBlank() }.joinToString("/")

            else -> status.allotmentCode
        }

    /**
     * Whether this and [other] are the same allotment, allowing for an unstated berth type.
     *
     * Not `equals`, and deliberately not a replacement for it. An e-ticket prints the berth type
     * in the booked column and often leaves it out of the charted one — `"CNF/S4/8/SU"` beside
     * `"CNF/S4/8"`, or `"CNF/S3/56/NA"` with it explicitly absent — and both of those describe
     * one berth in one coach. Comparing them field for field says every passenger on every such
     * ticket was moved, which is a "was X, now Y" line on screen about nothing at all.
     *
     * A type stated on both sides is compared. Anything else — coach, berth, status, a queue
     * position — always is.
     */
    fun describesSameAs(other: TrainAllotment): Boolean =
        if (berthType.isKnown && other.berthType.isKnown) {
            this == other
        } else {
            copy(berthType = BerthType.UNKNOWN) == other.copy(berthType = BerthType.UNKNOWN)
        }

    companion object {

        /**
         * Reads one allotment column off an e-ticket.
         *
         * Tolerant on purpose. Anything unrecognised comes back as [TrainAllotment] with the
         * status it could determine and nothing invented, so the screen falls back to showing
         * the original string rather than a wrong seat number.
         */
        fun parse(raw: String): TrainAllotment {
            val cleaned = raw.trim()
                .replace(Regex("\\s*/\\s*"), "/")
                .replace("W/L", "WL", ignoreCase = true)
            if (cleaned.isEmpty()) return TrainAllotment()

            // "WL 24" and "RAC 12" use a space where the slashed forms use a slash.
            val parts = cleaned.split('/', ' ').filter { it.isNotBlank() }
            if (parts.isEmpty()) return TrainAllotment()

            val head = parts.first().uppercase()
            val status = TrainBookingStatus.fromAllotmentCode(head)

            // A cancelled reservation has no seat. `CAN/MOD` is a cancellation with a reason
            // code after it, and reading `MOD` as a coach would put a passenger in coach MOD.
            if (status == TrainBookingStatus.CANCELLED) {
                return TrainAllotment(status = status)
            }

            // A queue position: a status that is a waitlist or RAC followed by a bare number,
            // with no coach to go with it.
            val queueCandidate = parts.getOrNull(1)?.toIntOrNull()
            if (queueCandidate != null && parts.size <= 2 &&
                (status == TrainBookingStatus.WAITLISTED || status == TrainBookingStatus.RAC)
            ) {
                return TrainAllotment(
                    status = status,
                    queuePosition = queueCandidate,
                    queueKind = head
                )
            }

            return TrainAllotment(
                status = status,
                coach = parts.getOrNull(1)?.takeUnless { it.equals("NA", true) }?.uppercase() ?: "",
                berth = parts.getOrNull(2)?.takeUnless { it.equals("NA", true) } ?: "",
                berthType = BerthType.parse(parts.getOrNull(3) ?: "")
            )
        }
    }
}
