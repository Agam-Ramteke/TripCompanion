package com.tripcompanion.app.data.ticket

import com.tripcompanion.app.data.pdf.PdfText
import com.tripcompanion.app.data.pdf.PdfTextExtractor
import com.tripcompanion.app.data.pdf.PdfTextRun
import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.ParsedTicket
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.TicketImportWarning
import com.tripcompanion.app.domain.model.TrainBookingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The parser against four real e-ticket PDFs.
 *
 * The fixtures in `src/test/resources/tickets/` are the author's own bookings with every piece
 * of personal data replaced *inside the PDF's own content streams* — names, PNRs, ages, booking
 * references, the lot. Everything that makes these worth testing against is intact: the same
 * fonts, the same CID encoding, the same two page templates, the same stray period in front of
 * the insurance premium. Nothing here is a hand-written approximation of a ticket, which is the
 * only way to be sure the reading survives contact with the real thing.
 *
 * Two layouts are represented, and they differ in seven places — station codes, distance units,
 * quota capitalisation, what goes in the arrival cell, the width of the day of the month, the
 * shape of the current-status column, and a stray period in the fare block. Each of those is
 * asserted below, because each of them is a place a change to the parser can quietly break one
 * layout while the other keeps passing.
 */
class IrctcTicketParserTest {

    private fun ticket(name: String): ParsedTicket {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/tickets/$name")) {
            "missing fixture /tickets/$name"
        }.readBytes()
        return IrctcTicketParser.parse(PdfTextExtractor.extract(bytes))
    }

    // ── The older layout: names without codes, a station where the arrival time belongs ──

    @Test
    fun `older layout reads the booking`() {
        val t = ticket("mmt_sleeper.pdf")

        assertEquals("1000000001", t.pnr)
        assertEquals("12964", t.trainNumber)
        assertEquals("MEWAR EXPRESS", t.trainName)
        assertEquals("SL", t.travelClass)
        assertEquals("General", t.quota)
        assertEquals(610, t.distanceKm)
    }

    /** `Arrival* <station name>` — the cell holds a place, not a time. Null, never a guess. */
    @Test
    fun `older layout has a departure but no arrival`() {
        val t = ticket("mmt_sleeper.pdf")

        assertEquals(LocalDateTime.of(2026, 9, 10, 18, 30), t.departure)
        assertNull(t.arrival)
        assertTrue(TicketImportWarning.ARRIVAL_TIME_MISSING in t.warnings)
        assertFalse(TicketImportWarning.DEPARTURE_TIME_MISSING in t.warnings)
        assertFalse(t.isComplete)
    }

    /** This layout prints no codes at all, which the user has to be told about. */
    @Test
    fun `older layout reads station names and warns that codes are absent`() {
        val t = ticket("mmt_sleeper.pdf")

        assertEquals("Udaipur City", t.originName)
        assertEquals("", t.originCode)
        assertEquals("Mathura Jn", t.destinationName)
        assertEquals("", t.destinationCode)
        assertTrue(TicketImportWarning.STATION_CODES_MISSING in t.warnings)
    }

    /** Two digits for the day of the month here, one on the newer layout. */
    @Test
    fun `older layout reads a two-digit booking date`() {
        assertEquals(
            LocalDateTime.of(2026, 8, 2, 12, 49, 25),
            ticket("mmt_sleeper.pdf").bookedAt
        )
    }

    @Test
    fun `older layout reads the agent and the transaction separately`() {
        val t = ticket("mmt_sleeper.pdf")

        assertEquals("MakeMyTrip", t.agentName)
        assertEquals("TRNAV1BAAAAA0001AA", t.agentBookingId)
        assertEquals("ON1000000000000001", t.transactionId)
    }

    @Test
    fun `older layout reads the whole fare breakdown`() {
        val fare = ticket("mmt_sleeper.pdf").fare

        assertEquals(790.00, fare.ticketFare!!, 0.001)
        assertEquals(17.70, fare.convenienceFee!!, 0.001)
        assertEquals(0.90, fare.insurancePremium!!, 0.001)
        assertEquals(20.00, fare.agentServiceCharge!!, 0.001)
        assertEquals(0.00, fare.paymentGatewayCharge!!, 0.001)
        assertEquals(828.60, fare.totalFare!!, 0.001)
        assertFalse(TicketImportWarning.FARE_INCOMPLETE in ticket("mmt_sleeper.pdf").warnings)
    }

    /**
     * The current-status column drops the berth type on this layout, so it is taken from the
     * booking column — same coach, same berth, so the `SU` still holds.
     */
    @Test
    fun `older layout reads both passengers and keeps the berth type from the booking`() {
        val party = ticket("mmt_sleeper.pdf").passengers
        assertEquals(2, party.size)

        val first = party[0]
        assertEquals(1, first.serialNo)
        assertEquals("Ananya Kapoor", first.name)
        assertEquals(21, first.age)
        assertEquals(PassengerGender.FEMALE, first.gender)
        assertEquals(TrainBookingStatus.CONFIRMED, first.allotment.status)
        assertEquals("S4", first.allotment.coach)
        assertEquals("8", first.allotment.berth)
        assertEquals(BerthType.SIDE_UPPER, first.allotment.berthType)
        assertEquals("CNF/S4/8/SU", first.bookingStatusText)
        assertEquals("CNF/S4/8", first.currentStatusText)

        val second = party[1]
        assertEquals(2, second.serialNo)
        assertEquals("Rohit Bansal", second.name)
        assertEquals(PassengerGender.MALE, second.gender)
        assertEquals("6", second.allotment.berth)
        assertEquals(BerthType.UPPER, second.allotment.berthType)
    }

    /** The acronym legend and the agent's booking id sit directly under the table. */
    @Test
    fun `the passenger table stops at the end of the table`() {
        assertEquals(2, ticket("mmt_sleeper.pdf").passengers.size)
        assertEquals(2, ticket("goibibo_kzj_agc.pdf").passengers.size)
    }

    // ── The newer layout: codes, kms, a real arrival time, a separate boarding point ──

    @Test
    fun `newer layout reads the booking`() {
        val t = ticket("goibibo_kzj_agc.pdf")

        assertEquals("1000000002", t.pnr)
        assertEquals("12723", t.trainNumber)
        assertEquals("TELANGANA SF EXP", t.trainName)
        assertEquals("SL", t.travelClass)
        assertEquals("General", t.quota)
        // `1348 kms` — the unit is printed on this layout and not on the other.
        assertEquals(1348, t.distanceKm)
    }

    @Test
    fun `newer layout reads both times and needs nothing filled in`() {
        val t = ticket("goibibo_kzj_agc.pdf")

        assertEquals(LocalDateTime.of(2026, 9, 5, 15, 25), t.departure)
        assertEquals(LocalDateTime.of(2026, 9, 6, 4, 25), t.arrival)
        assertTrue(t.warnings.isEmpty())
        assertTrue(t.isComplete)
    }

    /**
     * Three station cells under two headers: booked-from, boarding point, destination.
     *
     * This is the ticket that makes the boarding point worth reading at all — the reservation
     * starts two stations before the party gets on, and standing at the booked-from station is
     * a missed train.
     */
    @Test
    fun `newer layout reads a boarding point that is not the origin`() {
        val t = ticket("goibibo_kzj_agc.pdf")

        assertEquals("KZJ", t.originCode)
        assertEquals("Kazipet Jn", t.originName)
        assertEquals("NGP", t.boardingCode)
        assertEquals("Nagpur", t.boardingName)
        assertEquals("AGC", t.destinationCode)
        assertEquals("Agra Cantt", t.destinationName)
    }

    /** One digit for the day of the month here, two on the older layout. */
    @Test
    fun `newer layout reads a one-digit booking date`() {
        assertEquals(
            LocalDateTime.of(2026, 8, 2, 9, 13, 26),
            ticket("goibibo_kzj_agc.pdf").bookedAt
        )
    }

    /**
     * `. 0.9` — one layout draws a stray period in front of the insurance premium.
     *
     * The number is matched out of the cell rather than the cell being stripped of everything
     * but digits and points, which would leave `.0.9` and parse to nothing.
     */
    @Test
    fun `newer layout reads a fare cell with a stray leading period`() {
        val fare = ticket("goibibo_kzj_agc.pdf").fare

        assertEquals(1300.0, fare.ticketFare!!, 0.001)
        assertEquals(0.9, fare.insurancePremium!!, 0.001)
        assertEquals(1338.6, fare.totalFare!!, 0.001)
    }

    /** `CNF/S3/56/SU` booked, `CNF/S3/56/NA` charted: the same berth, type not repeated. */
    @Test
    fun `newer layout merges an NA berth type with the booked one`() {
        val party = ticket("goibibo_kzj_agc.pdf").passengers
        assertEquals(2, party.size)

        assertEquals("S3", party[0].allotment.coach)
        assertEquals("56", party[0].allotment.berth)
        assertEquals(BerthType.SIDE_UPPER, party[0].allotment.berthType)
        assertEquals("CNF/S3/56/SU", party[0].bookingStatusText)
        assertEquals("CNF/S3/56/NA", party[0].currentStatusText)

        assertEquals("16", party[1].allotment.berth)
        assertEquals(BerthType.SIDE_UPPER, party[1].allotment.berthType)
    }

    @Test
    fun `newer layout reads the agent and the transaction separately`() {
        val t = ticket("goibibo_kzj_agc.pdf")

        assertEquals("Goibibo", t.agentName)
        assertEquals("TRNAV1BAAAAA0002AA", t.agentBookingId)
        assertEquals("100000000000002", t.transactionId)
    }

    // ── The other two bookings, which share the newer template ──

    @Test
    fun `third booking reads end to end`() {
        val t = ticket("goibibo_agc_udz.pdf")

        assertEquals("1000000003", t.pnr)
        assertEquals("20178", t.trainNumber)
        assertEquals("AGC ASV SF EXP", t.trainName)
        assertEquals(602, t.distanceKm)
        assertEquals("AGC", t.originCode)
        assertEquals("Agra Cantt", t.originName)
        assertEquals("UDZ", t.destinationCode)
        assertEquals("Udaipur City", t.destinationName)
        assertEquals(LocalDateTime.of(2026, 9, 6, 18, 45), t.departure)
        assertEquals(LocalDateTime.of(2026, 9, 7, 5, 20), t.arrival)
        assertEquals(LocalDateTime.of(2026, 8, 2, 10, 42, 5), t.bookedAt)
        assertEquals(828.6, t.fare.totalFare!!, 0.001)
        assertEquals("TRNAV1BAAAAA0003AA", t.agentBookingId)
        assertTrue(t.warnings.isEmpty())

        assertEquals(listOf("S2", "S2"), t.passengers.map { it.allotment.coach })
        assertEquals(listOf("24", "17"), t.passengers.map { it.allotment.berth })
        assertEquals(
            listOf(BerthType.SIDE_UPPER, BerthType.LOWER),
            t.passengers.map { it.allotment.berthType }
        )
    }

    /** Departs and arrives on the same day, unlike the other three. */
    @Test
    fun `fourth booking reads end to end`() {
        val t = ticket("goibibo_mtj_ngp.pdf")

        assertEquals("1000000004", t.pnr)
        assertEquals("12644", t.trainNumber)
        assertEquals("NZM TVC SF EXP", t.trainName)
        assertEquals(949, t.distanceKm)
        assertEquals("MTJ", t.originCode)
        assertEquals("Mathura Jn", t.originName)
        assertEquals("NGP", t.destinationCode)
        assertEquals("Nagpur", t.destinationName)
        assertEquals(LocalDateTime.of(2026, 9, 11, 6, 45), t.departure)
        assertEquals(LocalDateTime.of(2026, 9, 11, 21, 50), t.arrival)
        assertEquals(LocalDateTime.of(2026, 8, 2, 13, 10, 48), t.bookedAt)
        assertEquals(1088.6, t.fare.totalFare!!, 0.001)
        assertTrue(t.warnings.isEmpty())

        assertEquals(listOf("40", "34"), t.passengers.map { it.allotment.berth })
        assertEquals(
            listOf(BerthType.SIDE_UPPER, BerthType.MIDDLE),
            t.passengers.map { it.allotment.berthType }
        )
    }

    // ── The whole set, and what it must never do ──

    /**
     * Every fixture yields something worth showing, and every one names a boarding point.
     *
     * The boarding point equals the origin on three of the four; it is still filled in on all
     * four, because [com.tripcompanion.app.domain.model.Train.boardsElsewhere] is what decides
     * whether it is worth a line on screen, not whether it was read.
     */
    @Test
    fun `all four fixtures parse into something with substance`() {
        val names = listOf(
            "mmt_sleeper.pdf",
            "goibibo_kzj_agc.pdf",
            "goibibo_agc_udz.pdf",
            "goibibo_mtj_ngp.pdf"
        )
        names.forEach { name ->
            val t = ticket(name)
            assertTrue("$name has no substance", t.hasSubstance)
            assertTrue("$name has no passengers", t.passengers.size == 2)
            assertTrue("$name has no train number", t.trainNumber.length == 5)
            assertTrue("$name lost its boarding point", t.boardingName.isNotBlank())
            assertEquals("$name misread the class", "SL", t.travelClass)
        }
    }

    /**
     * A document with text but no reservation panel reads as nothing, not as a half-filled form.
     *
     * The panel is identified by two labels sharing one content stream, so anything else returns
     * the empty reading and the caller reports the file was not an e-ticket. Worth its own test
     * because the alternative — a form pre-filled with fragments of somebody's boarding pass —
     * is worse than being told the file was wrong.
     */
    @Test
    fun `text with no reservation panel parses to nothing`() {
        val notATicket = PdfText(
            listOf(
                PdfTextRun(stream = 0, x = 60f, y = 700f, text = "Invoice"),
                PdfTextRun(stream = 0, x = 60f, y = 680f, text = "Amount due"),
                PdfTextRun(stream = 0, x = 300f, y = 680f, text = "1,240.00")
            )
        )

        assertEquals(ParsedTicket(), IrctcTicketParser.parse(notATicket))
        assertFalse(IrctcTicketParser.parse(notATicket).hasSubstance)
    }

    /** Half a panel is not a panel: both markers have to be there. */
    @Test
    fun `one marker alone is not enough to claim a stream`() {
        val onlyPnr = PdfText(
            listOf(
                PdfTextRun(stream = 0, x = 60f, y = 700f, text = "PNR"),
                PdfTextRun(stream = 0, x = 60f, y = 680f, text = "1000000001")
            )
        )

        assertFalse(IrctcTicketParser.parse(onlyPnr).hasSubstance)
    }
}
