package com.tripcompanion.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The reservation as the railway writes it, and as this app has to read it back.
 *
 * One slash-separated column carries the whole allotment — `"CNF/S3/56/SU"` — and it is the only
 * place the coach and the berth appear on a ticket. Every string asserted here is one that has
 * actually been printed on one: a berth type that is present, absent, or explicitly `NA`; a
 * waitlist position where a coach would otherwise be; a cancellation with a reason code after it
 * that must never be read as a coach called MOD.
 *
 * These are pure functions over strings, which makes them cheap to cover and expensive to get
 * wrong: a misread here puts a passenger in the wrong coach on a screen someone is reading while
 * boarding.
 */
class TrainAllotmentTest {

    // ---- Reading the column ------------------------------------------------

    @Test
    fun `a full allotment reads all four parts`() {
        val a = TrainAllotment.parse("CNF/S3/56/SU")

        assertEquals(TrainBookingStatus.CONFIRMED, a.status)
        assertEquals("S3", a.coach)
        assertEquals("56", a.berth)
        assertEquals(BerthType.SIDE_UPPER, a.berthType)
        assertNull(a.queuePosition)
        assertTrue(a.hasSeat)
        assertEquals("S3 · 56", a.seatLabel)
    }

    /** The current-status column often stops after the berth. */
    @Test
    fun `an allotment with no berth type is still a seat`() {
        val a = TrainAllotment.parse("CNF/S4/8")

        assertEquals("S4", a.coach)
        assertEquals("8", a.berth)
        assertEquals(BerthType.UNKNOWN, a.berthType)
        assertTrue(a.hasSeat)
    }

    /** `NA` is the ticket saying the type is not repeated here, not a berth type called NA. */
    @Test
    fun `an NA berth type reads as unknown`() {
        assertEquals(BerthType.UNKNOWN, TrainAllotment.parse("CNF/S3/56/NA").berthType)
        assertEquals("56", TrainAllotment.parse("CNF/S3/56/NA").berth)
    }

    /** `NA` in the coach or berth position means the same thing and must not become a coach. */
    @Test
    fun `an NA coach reads as no coach`() {
        val a = TrainAllotment.parse("CNF/NA/NA")

        assertEquals("", a.coach)
        assertEquals("", a.berth)
        assertFalse(a.hasSeat)
    }

    /**
     * A waitlist number is a place in a queue, not a berth.
     *
     * Rendering `"GNWL/24"` as berth 24 would tell someone they have a seat when they do not,
     * which is the one thing this value object exists to prevent.
     */
    @Test
    fun `a waitlist position is a queue and not a berth`() {
        val a = TrainAllotment.parse("GNWL/24")

        assertEquals(TrainBookingStatus.WAITLISTED, a.status)
        assertEquals(24, a.queuePosition)
        assertEquals("GNWL", a.queueKind)
        assertEquals("", a.coach)
        assertEquals("", a.berth)
        assertFalse(a.hasSeat)
        assertEquals("Waitlist 24 · GNWL", a.queueLabel)
    }

    /** Some columns use a space where the slashed forms use a slash. */
    @Test
    fun `RAC uses a space and reads the same`() {
        val a = TrainAllotment.parse("RAC 12")

        assertEquals(TrainBookingStatus.RAC, a.status)
        assertEquals(12, a.queuePosition)
        assertEquals("RAC 12", a.queueLabel)
    }

    /** `W/L` is a waitlist with a slash inside its own name, which would otherwise split it. */
    @Test
    fun `W slash L is a waitlist and not two parts`() {
        val a = TrainAllotment.parse("W/L 24")

        assertEquals(TrainBookingStatus.WAITLISTED, a.status)
        assertEquals(24, a.queuePosition)
        assertEquals("WL", a.queueKind)
        assertEquals("Waitlist 24 · WL", a.queueLabel)
    }

    /**
     * `"CAN/MOD"` is a cancellation with a reason code, and `MOD` is not a coach.
     *
     * A cancelled reservation has no seat at all, so everything after the status is dropped
     * rather than read into the fields it happens to line up with.
     */
    @Test
    fun `a cancellation keeps nothing but its status`() {
        val a = TrainAllotment.parse("CAN/MOD")

        assertEquals(TrainBookingStatus.CANCELLED, a.status)
        assertEquals("", a.coach)
        assertEquals("", a.berth)
        assertFalse(a.hasSeat)
        assertNull(a.queuePosition)
    }

    /** RAC passengers do get a berth to share, and it is printed the long way. */
    @Test
    fun `RAC with a berth is a seat rather than a queue position`() {
        val a = TrainAllotment.parse("RAC/S1/12/SL")

        assertEquals(TrainBookingStatus.RAC, a.status)
        assertEquals("S1", a.coach)
        assertEquals("12", a.berth)
        assertEquals(BerthType.SIDE_LOWER, a.berthType)
        assertNull(a.queuePosition)
    }

    @Test
    fun `spacing and case do not matter`() {
        val a = TrainAllotment.parse("  cnf / s3 / 56 / su  ")

        assertEquals(TrainBookingStatus.CONFIRMED, a.status)
        assertEquals("S3", a.coach)
        assertEquals(BerthType.SIDE_UPPER, a.berthType)
    }

    @Test
    fun `an empty column is an empty allotment`() {
        assertEquals(TrainAllotment(), TrainAllotment.parse(""))
        assertEquals(TrainAllotment(), TrainAllotment.parse("   "))
        assertEquals(TrainAllotment(), TrainAllotment.parse("///"))
    }

    /** An unrecognised status keeps whatever else was readable and invents no status. */
    @Test
    fun `an unrecognised status is not a guess`() {
        val a = TrainAllotment.parse("XYZ/S1/2")

        assertEquals(TrainBookingStatus.NOT_BOOKED, a.status)
        assertEquals("S1", a.coach)
        assertEquals("2", a.berth)
        // Nothing the railway would have printed, so nothing is printed back.
        assertEquals("", a.text)
        assertEquals("S1 · 2", a.seatLabel)
    }

    // ---- Writing it back --------------------------------------------------

    @Test
    fun `an allotment renders back into the railway's own form`() {
        assertEquals("CNF/S3/56/SU", TrainAllotment.parse("CNF/S3/56/SU").text)
        assertEquals("CNF/S4/8", TrainAllotment.parse("CNF/S4/8").text)
        assertEquals("GNWL/24", TrainAllotment.parse("GNWL/24").text)
        assertEquals("CAN", TrainAllotment.parse("CAN/MOD").text)
    }

    /**
     * A queue position written with a space comes back with a slash, and still means the same.
     *
     * Round-tripping as a *value* is what matters — the app stores the original string beside the
     * parse precisely so the exact characters are never lost to a paraphrase.
     */
    @Test
    fun `a spaced queue position round-trips as a value`() {
        val spaced = TrainAllotment.parse("RAC 12")

        assertEquals("RAC/12", spaced.text)
        assertEquals(spaced, TrainAllotment.parse(spaced.text))
    }

    @Test
    fun `every parsed form round-trips through text`() {
        listOf("CNF/S3/56/SU", "CNF/S4/8", "GNWL/24", "PQWL/8", "RAC/S1/12/SL", "CAN/MOD")
            .forEach { printed ->
                val once = TrainAllotment.parse(printed)
                assertEquals(printed, once, TrainAllotment.parse(once.text))
            }
    }

    /** A seat with no status is not something the railway prints, so it renders as nothing. */
    @Test
    fun `a seat with no status renders as nothing`() {
        val a = TrainAllotment(coach = "S3", berth = "56")

        assertEquals("", a.text)
        assertEquals("S3 · 56", a.seatLabel)
    }

    @Test
    fun `a status with nothing after it renders as just the status`() {
        assertEquals("WL", TrainAllotment(status = TrainBookingStatus.WAITLISTED).text)
        assertEquals("", TrainAllotment(status = TrainBookingStatus.WAITLISTED).queueLabel)
    }

    /** A queue position with no queue named — a hand-entered waitlist — says the plain thing. */
    @Test
    fun `a queue with no flavour is just a waitlist position`() {
        val a = TrainAllotment(status = TrainBookingStatus.WAITLISTED, queuePosition = 24)

        assertEquals("Waitlist 24", a.queueLabel)
        // Falls back to the status' own code, so it still reads like a printed allotment.
        assertEquals("WL/24", a.text)
    }

    @Test
    fun `half a seat still labels`() {
        assertEquals("S3", TrainAllotment(coach = "S3").seatLabel)
        assertEquals("56", TrainAllotment(berth = "56").seatLabel)
        assertEquals("", TrainAllotment().seatLabel)
    }

    // ---- The same allotment, printed two ways -----------------------------

    /**
     * A berth type stated in one column and not the other is not a change of berth.
     *
     * This is the rule that stops every passenger on every real ticket showing a "was X, now Y"
     * line about nothing: the booked column prints the type, the charted one frequently does not.
     */
    @Test
    fun `an unstated berth type is not a difference`() {
        val booked = TrainAllotment.parse("CNF/S4/8/SU")

        assertTrue(booked.describesSameAs(TrainAllotment.parse("CNF/S4/8")))
        assertTrue(booked.describesSameAs(TrainAllotment.parse("CNF/S4/8/NA")))
        // And the other way round, because neither column is privileged.
        assertTrue(TrainAllotment.parse("CNF/S4/8").describesSameAs(booked))
    }

    @Test
    fun `two stated berth types that differ are a difference`() {
        assertFalse(
            TrainAllotment.parse("CNF/S4/8/SU")
                .describesSameAs(TrainAllotment.parse("CNF/S4/8/LB"))
        )
    }

    @Test
    fun `a different berth, coach, status or queue is always a difference`() {
        val booked = TrainAllotment.parse("CNF/S4/8/SU")

        assertFalse(booked.describesSameAs(TrainAllotment.parse("CNF/S4/9/SU")))
        assertFalse(booked.describesSameAs(TrainAllotment.parse("CNF/S5/8/SU")))
        assertFalse(booked.describesSameAs(TrainAllotment.parse("RAC/S4/8/SU")))
        assertFalse(booked.describesSameAs(TrainAllotment.parse("GNWL/24")))
        assertFalse(
            TrainAllotment.parse("GNWL/24").describesSameAs(TrainAllotment.parse("GNWL/8"))
        )
    }

    @Test
    fun `an allotment always describes itself`() {
        listOf("CNF/S3/56/SU", "CNF/S4/8", "GNWL/24", "RAC 12", "CAN/MOD", "")
            .map { TrainAllotment.parse(it) }
            .forEach { assertTrue(it.describesSameAs(it)) }
    }

    // ---- What the passenger row makes of the two columns -------------------

    /** The older layout: the type in the booked column only. Nothing moved. */
    @Test
    fun `a berth type printed once is not a passenger who was moved`() {
        assertFalse(passenger("CNF/S4/8/SU", "CNF/S4/8").allotmentChanged)
        assertFalse(passenger("CNF/S3/56/SU", "CNF/S3/56/NA").allotmentChanged)
        assertFalse(passenger("CNF/S4/8/SU", "CNF/S4/8/SU").allotmentChanged)
    }

    /** Waitlisted then confirmed is the case the second line on screen exists for. */
    @Test
    fun `a passenger confirmed off the waitlist has changed`() {
        assertTrue(passenger("GNWL/24", "CNF/S3/56/SU").allotmentChanged)
        assertTrue(passenger("CNF/S4/8/SU", "CNF/S4/9/SU").allotmentChanged)
    }

    /** A hand-entered passenger has no e-ticket behind them and nothing to compare. */
    @Test
    fun `a passenger with only one column has not changed`() {
        assertFalse(passenger("CNF/S4/8/SU", "").allotmentChanged)
        assertFalse(passenger("", "CNF/S4/8/SU").allotmentChanged)
        assertFalse(passenger("", "").allotmentChanged)
    }

    @Test
    fun `a passenger line drops whatever is missing`() {
        assertEquals(
            "A Traveller · 21 · Female",
            TrainPassenger(
                name = "A Traveller", age = 21, gender = PassengerGender.FEMALE
            ).displayLine
        )
        assertEquals(
            "A Traveller · Female",
            TrainPassenger(name = "A Traveller", gender = PassengerGender.FEMALE).displayLine
        )
        assertEquals("A Traveller · 21", TrainPassenger(name = "A Traveller", age = 21).displayLine)
        assertEquals("", TrainPassenger().displayLine)
    }

    // ---- The codes on the ticket ------------------------------------------

    @Test
    fun `berth type codes read in either case, and NA does not`() {
        assertEquals(BerthType.SIDE_UPPER, BerthType.parse("SU"))
        assertEquals(BerthType.SIDE_UPPER, BerthType.parse(" su "))
        assertEquals(BerthType.LOWER, BerthType.parse("LB"))
        assertEquals(BerthType.SIDE_LOWER, BerthType.parse("SL"))
        // `AB` for an aisle seat appears on chair-car tickets alongside `AS`.
        assertEquals(BerthType.AISLE_SEAT, BerthType.parse("AB"))
        assertEquals(BerthType.AISLE_SEAT, BerthType.parse("AS"))
        assertEquals(BerthType.UNKNOWN, BerthType.parse("NA"))
        assertEquals(BerthType.UNKNOWN, BerthType.parse(""))
        assertEquals(BerthType.UNKNOWN, BerthType.parse("ZZ"))
        assertFalse(BerthType.UNKNOWN.isKnown)
        assertTrue(BerthType.SIDE_UPPER.isKnown)
    }

    @Test
    fun `gender reads the word or the letter`() {
        assertEquals(PassengerGender.FEMALE, PassengerGender.parse("Female"))
        assertEquals(PassengerGender.FEMALE, PassengerGender.parse("F"))
        assertEquals(PassengerGender.MALE, PassengerGender.parse("male"))
        assertEquals(PassengerGender.MALE, PassengerGender.parse("M"))
        assertEquals(PassengerGender.TRANSGENDER, PassengerGender.parse("T"))
        assertEquals(PassengerGender.UNSPECIFIED, PassengerGender.parse(""))
        assertEquals(PassengerGender.UNSPECIFIED, PassengerGender.parse("Unknown"))
    }

    /**
     * Every waitlist flavour means one thing to this app.
     *
     * `GNWL` general, `RLWL` remote location, `PQWL` pooled quota, `TQWL` and `CKWL` tatkal,
     * `RSWL` road-side — the suffix is matched rather than the set enumerated, so a flavour
     * nobody has seen yet still reads as a waitlist instead of as nothing.
     */
    @Test
    fun `every waitlist flavour is a waitlist`() {
        listOf("GNWL", "RLWL", "PQWL", "TQWL", "CKWL", "RSWL", "WL", "gnwl").forEach { code ->
            assertEquals(code, TrainBookingStatus.WAITLISTED, TrainBookingStatus.fromAllotmentCode(code))
        }
    }

    @Test
    fun `the other status codes read as themselves`() {
        assertEquals(TrainBookingStatus.CONFIRMED, TrainBookingStatus.fromAllotmentCode("CNF"))
        assertEquals(
            TrainBookingStatus.CONFIRMED,
            TrainBookingStatus.fromAllotmentCode("Confirmed")
        )
        assertEquals(TrainBookingStatus.RAC, TrainBookingStatus.fromAllotmentCode("rac"))
        assertEquals(TrainBookingStatus.CANCELLED, TrainBookingStatus.fromAllotmentCode("CAN"))
        assertEquals(TrainBookingStatus.NOT_BOOKED, TrainBookingStatus.fromAllotmentCode(""))
        assertEquals(TrainBookingStatus.NOT_BOOKED, TrainBookingStatus.fromAllotmentCode("XYZ"))
    }

    /** The order the whole booking's status is derived from, least settled first. */
    @Test
    fun `settledness orders the statuses`() {
        assertEquals(
            listOf(
                TrainBookingStatus.CANCELLED,
                TrainBookingStatus.NOT_BOOKED,
                TrainBookingStatus.WAITLISTED,
                TrainBookingStatus.RAC,
                TrainBookingStatus.CONFIRMED
            ),
            TrainBookingStatus.entries.sortedBy { it.settledness }
        )
    }

    // ---- The booking, summarised from its party ---------------------------

    /**
     * One person still waitlisted is what the whole trip has to be planned around.
     *
     * Derived from the party rather than read from the stored column, so a badge can never
     * disagree with the list printed underneath it.
     */
    @Test
    fun `the booking takes the least settled status in the party`() {
        val booking = train(
            listOf(
                seated("CNF/S4/8/SU"),
                TrainPassenger(
                    serialNo = 2,
                    name = "Another Traveller",
                    allotment = TrainAllotment.parse("GNWL/24")
                )
            )
        )

        assertEquals(TrainBookingStatus.WAITLISTED, booking.effectiveBookingStatus)
    }

    /** A party with nobody allotted yet falls back to the status stored on the booking. */
    @Test
    fun `a booking with no party keeps its own status`() {
        assertEquals(
            TrainBookingStatus.CONFIRMED,
            train(emptyList()).copy(bookingStatus = TrainBookingStatus.CONFIRMED)
                .effectiveBookingStatus
        )
    }

    @Test
    fun `the party summarises into what fits on a card`() {
        val booking = train(
            listOf(
                seated("CNF/S4/8/SU", name = "First Traveller"),
                seated("CNF/S4/6/UB", name = "Second Traveller", serialNo = 2),
                seated("CNF/S5/12/LB", name = "Third Traveller", serialNo = 3)
            )
        )

        // Distinct, in order: a party split across two coaches is worth seeing as two.
        assertEquals("S4, S5", booking.coachSummary)
        assertEquals("8, 6, 12", booking.berthSummary)
        assertEquals("First, Second, Third", booking.passengerSummary)
    }

    @Test
    fun `an unallotted party summarises to nothing rather than to blanks`() {
        val booking = train(
            listOf(
                TrainPassenger(serialNo = 1, name = "A Traveller", allotment = TrainAllotment.parse("GNWL/24"))
            )
        )

        assertEquals("", booking.coachSummary)
        assertEquals("", booking.berthSummary)
        assertEquals("A", booking.passengerSummary)
    }

    /**
     * Boarding somewhere other than the station the ticket was booked from.
     *
     * Ordinary — it is how a full quota gets worked around — and getting on at the booked-from
     * station out of habit is a missed train, which is why it is a fact the booking exposes
     * rather than something a screen has to work out.
     */
    @Test
    fun `a boarding point elsewhere is worth its own line`() {
        val booking = train(emptyList()).copy(boardingCode = "CCC", boardingName = "Gamma")

        assertTrue(booking.boardsElsewhere)
        assertEquals("Gamma", booking.boardingPointName)
    }

    @Test
    fun `a boarding point at the origin is not`() {
        val sameStation = train(emptyList()).copy(boardingCode = "AAA", boardingName = "Alpha Jn")

        assertFalse(sameStation.boardsElsewhere)
        assertEquals("Alpha Jn", sameStation.boardingPointName)
        // Not read at all when the ticket never named one.
        assertFalse(train(emptyList()).boardsElsewhere)
        assertEquals("Alpha Jn", train(emptyList()).boardingPointName)
    }

    @Test
    fun `a booking with no name shows its number alone`() {
        assertEquals("12345 · SOME EXPRESS", train(emptyList()).displayTitle)
        assertEquals("12345", train(emptyList()).copy(name = "").displayTitle)
    }

    // ---- Fixtures ---------------------------------------------------------

    private fun passenger(booked: String, current: String) = TrainPassenger(
        name = "A Traveller",
        allotment = TrainAllotment.parse(booked),
        bookingStatusText = booked,
        currentStatusText = current
    )

    private fun seated(printed: String, name: String = "A Traveller", serialNo: Int = 1) =
        TrainPassenger(
            serialNo = serialNo,
            name = name,
            allotment = TrainAllotment.parse(printed),
            bookingStatusText = printed,
            currentStatusText = printed
        )

    /** Station names are placeholders: no code in this app may recognise a real one (§4). */
    private fun train(party: List<TrainPassenger>) = Train(
        tripId = 1,
        number = "12345",
        name = "SOME EXPRESS",
        originCode = "AAA",
        originName = "Alpha Jn",
        destinationCode = "BBB",
        destinationName = "Beta",
        departureTime = LocalDateTime.of(2026, 11, 3, 22, 40),
        arrivalTime = LocalDateTime.of(2026, 11, 4, 5, 20),
        passengers = party
    )
}
