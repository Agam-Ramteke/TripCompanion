package com.tripcompanion.app.data.network.railradar

import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.TicketImportWarning
import com.tripcompanion.app.domain.model.TrainBookingStatus
import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.model.TrainRunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The RailRadar parser: where the integration's bugs would live.
 *
 * RailRadar is tidier than the vendor it replaced — one `{ success, data, meta }` envelope, real
 * JSON numbers, 24-hour times — but the same things still need proving: that a failure is unwrapped
 * and classified before anything is read (including one nested under `error:{}`), that a single
 * unusable row costs one field rather than the whole request, and that positions and speed are
 * *derived* from the route and never invented.
 *
 * These assert on [RailRadarException.kind] directly — the parser's own vendor-shaped failure type
 * — because the map from there to a user-facing error is each consumer's job and is tested with
 * them, not here. The fixtures use the shapes RailRadar actually sends: the schedule nests its
 * station under `station:{code,name}` with bare `HH:mm`, the live route carries `stationCode`/
 * `stationName` flat with ISO times, and both state each stop's 1-based day — which the parser
 * trusts, deriving rollover only when the day fields are absent.
 */
class RailRadarParserTest {

    private val fetchedAt = LocalDateTime.of(2026, 8, 21, 9, 30)

    /** Deliberately not the run's start date, so a test can tell "read from startDate" from "fell back". */
    private val requestedDate = LocalDate.of(2026, 1, 1)

    // ── Time and date parsing ──────────────────────────────────────────────────────────────

    @Test
    fun `parseTime reads a bare 24-hour time`() {
        assertEquals(LocalTime.of(16, 27), RailRadarParser.parseTime("16:27"))
        assertEquals(LocalTime.of(6, 20), RailRadarParser.parseTime("06:20"))
        assertEquals(LocalTime.of(6, 20), RailRadarParser.parseTime("6:20"))
    }

    @Test
    fun `parseTime reads the clock out of an ISO datetime`() {
        assertEquals(LocalTime.of(16, 27), RailRadarParser.parseTime("2026-08-24T16:27:00"))
        assertEquals(LocalTime.of(16, 27), RailRadarParser.parseTime("2026-08-24T16:27:00+05:30"))
        assertEquals(LocalTime.of(6, 20), RailRadarParser.parseTime("2026-08-24T06:20:15"))
    }

    @Test
    fun `parseTime rejects placeholders and impossible clocks`() {
        for (blank in listOf(null, "", " ", "-", "--", "N/A", "NA", "NULL")) {
            assertNull("expected null for '$blank'", RailRadarParser.parseTime(blank))
        }
        assertNull(RailRadarParser.parseTime("25:00"))
        assertNull(RailRadarParser.parseTime("10:75"))
        assertNull(RailRadarParser.parseTime("Chittaurgarh"))
    }

    @Test
    fun `parseDate reads ISO dates and the date half of a datetime`() {
        assertEquals(LocalDate.of(2026, 8, 24), RailRadarParser.parseDate("2026-08-24"))
        assertEquals(LocalDate.of(2026, 8, 24), RailRadarParser.parseDate("2026-08-24T16:27:00"))
    }

    @Test
    fun `parseDate falls back to a day-first date`() {
        assertEquals(LocalDate.of(2026, 8, 24), RailRadarParser.parseDate("24-08-2026"))
    }

    @Test
    fun `parseDate rejects blanks and nonsense`() {
        assertNull(RailRadarParser.parseDate(null))
        assertNull(RailRadarParser.parseDate("-"))
        assertNull(RailRadarParser.parseDate("someday"))
    }

    // ── Envelope handling ──────────────────────────────────────────────────────────────────

    @Test
    fun `a non-JSON body is malformed`() {
        assertKind(RailRadarErrorKind.MALFORMED) { parseLive("<html>502 Bad Gateway</html>") }
    }

    @Test
    fun `a success-false envelope is classified by its message`() {
        assertKind(RailRadarErrorKind.NOT_FOUND) { parseLive(failure("Train not found")) }
        assertKind(RailRadarErrorKind.RATE_LIMITED) { parseLive(failure("Rate limit exceeded")) }
        assertKind(RailRadarErrorKind.KEY_REJECTED) { parseLive(failure("API key rejected")) }
        assertKind(RailRadarErrorKind.PROVIDER_ERROR) { parseLive(failure("Something exploded")) }
    }

    @Test
    fun `a nested error object is unwrapped and classified`() {
        // RailRadar returns a bad PNR as `error:{code,message}`, not a flat top-level message.
        assertKind(RailRadarErrorKind.NOT_FOUND) {
            RailRadarParser.parsePnr(
                """{"success":false,"error":{"code":"PRS:PNR_FLUSHED",""" +
                    """"message":"This PNR has expired or does not exist in active Indian Railways records."}}"""
            )
        }
    }

    @Test
    fun `a 200 carrying no data object is malformed`() {
        assertKind(RailRadarErrorKind.MALFORMED) { parseLive("""{"success":true,"meta":{}}""") }
    }

    @Test
    fun `live data with no route is malformed`() {
        assertKind(RailRadarErrorKind.MALFORMED) {
            parseLive("""{"success":true,"data":{"trainNumber":"12991"}}""")
        }
    }

    @Test
    fun `a route of unreadable rows is malformed`() {
        assertKind(RailRadarErrorKind.MALFORMED) {
            parseLive("""{"success":true,"data":{"route":[{"foo":"bar"},{"baz":1}]}}""")
        }
    }

    // ── Live status ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a mid-run snapshot reads position, delay, next stop and speed`() {
        val status = parseLive(liveMidRun, trainId = 7)

        assertEquals(7L, status.trainId)
        assertEquals(fetchedAt, status.fetchedAt)
        assertEquals(TrainRunSource.LIVE, status.source)
        assertEquals(LocalDate.of(2026, 8, 21), status.runDate)
        assertEquals("Running", status.message)
        assertEquals("COR", status.currentStationCode)
        assertEquals("Chittaurgarh", status.currentStationName)
        assertEquals(14, status.delayMinutes)
        assertTrue(status.isDelayed)
        assertEquals(2, status.lastDepartedSerial)
        assertTrue(status.hasStarted)
        assertFalse(status.hasArrived)
        assertEquals(248f / 383f, status.progressFraction, 0.0001f)
        assertEquals("COR", status.nextStopCode)
        assertEquals("Chittaurgarh", status.nextStopName)
        assertEquals(LocalTime.of(11, 54), status.nextStopEta)
        assertEquals(47.929, status.averageSpeedKmph!!, 0.01)
        assertEquals(4, status.stops.size)
    }

    @Test
    fun `the origin has no arrival and the terminus no departure`() {
        val status = parseLive(liveMidRun, trainId = 7)
        val first = status.stops.first()
        val last = status.stops.last()

        assertNull(first.scheduledArrival)
        assertEquals(LocalTime.of(6, 20), first.scheduledDeparture)
        assertNull(last.scheduledDeparture)
        assertEquals(LocalTime.of(13, 5), last.scheduledArrival)
    }

    @Test
    fun `ISO datetimes in the route read as their clock times`() {
        val status = parseLive(liveIsoTimes, trainId = 7)
        assertEquals(LocalTime.of(6, 20), status.stops.first().scheduledDeparture)
        assertEquals(LocalTime.of(13, 12), status.stops.last().actualArrival)
    }

    @Test
    fun `an overnight run derives day offsets and a positive speed`() {
        val status = parseLive(liveOvernight, trainId = 7)
        assertEquals(listOf(0, 1, 1), status.stops.map { it.dayOffset })
        assertEquals(112.5, status.averageSpeedKmph!!, 0.01)
    }

    @Test
    fun `a train not yet departed has started nothing`() {
        val status = parseLive(liveNotStarted, trainId = 7)
        assertEquals(0, status.lastDepartedSerial)
        assertFalse(status.hasStarted)
        assertEquals(0f, status.progressFraction, 0.0001f)
        assertNull(status.averageSpeedKmph)
        assertEquals("JP", status.nextStopCode)
        assertNull(status.nextStopEta)
    }

    @Test
    fun `a missing start date falls back to the requested date`() {
        // liveNotStarted carries no startDate.
        val status = parseLive(liveNotStarted, trainId = 7)
        assertEquals(requestedDate, status.runDate)
    }

    @Test
    fun `a completed run has arrived with nothing next`() {
        val status = parseLive(liveArrived, trainId = 7)
        assertTrue(status.hasArrived)
        assertEquals(1f, status.progressFraction, 0.0001f)
        assertEquals("UDZ", status.currentStationCode)
        assertEquals("", status.nextStopCode)
        assertNull(status.nextStop())
        assertTrue(status.stops.all { it.isDeparted })
    }

    @Test
    fun `numbers read whether quoted, bare or decimal`() {
        val status = parseLive(liveMixedTypes, trainId = 7)
        assertEquals(12, status.delayMinutes)          // delayMinutes was a quoted string
        assertEquals(2, status.stops[1].serialNo)      // sequence was a quoted string
        assertEquals(150, status.stops[1].distanceKm)  // distance was a quoted string
        assertEquals(300, status.stops[2].distanceKm)  // distance was a decimal, truncated to whole km
    }

    @Test
    fun `a station row with no code or name is skipped, not fatal`() {
        val status = parseLive(liveJunkRow, trainId = 7)
        assertEquals(2, status.stops.size)
        assertEquals(listOf("A", "C"), status.stops.map { it.stationCode })
    }

    @Test
    fun `a station with a code but no name shows the code`() {
        val status = parseLive(liveCodeOnly, trainId = 7)
        assertEquals("XYZ", status.stops[1].stationName)
    }

    // ── Schedule ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a schedule reads in route order with endpoints trimmed`() {
        val stops = RailRadarParser.parseSchedule(scheduleFull)

        assertEquals(listOf("JP", "AII", "UDZ"), stops.map { it.stationCode })
        assertNull(stops.first().scheduledArrival)
        assertEquals(LocalTime.of(6, 20), stops.first().scheduledDeparture)
        assertNull(stops.last().scheduledDeparture)
        assertEquals(LocalTime.of(13, 5), stops.last().scheduledArrival)
        assertEquals(listOf(0, 135, 383), stops.map { it.distanceKm })
        assertEquals(listOf(0, 0, 0), stops.map { it.dayOffset })
    }

    @Test
    fun `a schedule nested under train is read`() {
        val json = """
            {"success":true,"data":{"train":{"route":[
                {"sequence":1,"stationCode":"A","stationName":"Alpha","scheduledDeparture":"06:00","distance":0},
                {"sequence":2,"stationCode":"B","stationName":"Bravo","scheduledArrival":"07:00","distance":90}
            ]}}}
        """.trimIndent()
        val stops = RailRadarParser.parseSchedule(json)
        assertEquals(2, stops.size)
        assertEquals("B", stops.last().stationCode)
    }

    @Test
    fun `an overnight schedule rolls the day over at midnight`() {
        assertEquals(listOf(0, 1, 1), RailRadarParser.parseSchedule(scheduleOvernight).map { it.dayOffset })
    }

    @Test
    fun `a halt across midnight advances the following stops a day`() {
        assertEquals(listOf(0, 0, 1), RailRadarParser.parseSchedule(scheduleMidnightHalt).map { it.dayOffset })
    }

    @Test
    fun `an explicit day field is trusted over what the times would derive`() {
        // Times ascend (06:00 → 08:00), so derivation sees no rollover and would call both day 0.
        // The 1-based departureDay says the second stop is a day later, and the field wins.
        val json = """
            {"success":true,"data":{"route":[
                {"sequence":1,"station":{"code":"A","name":"Alpha"},"departure":"06:00","departureDay":1,"distance":0},
                {"sequence":2,"station":{"code":"B","name":"Bravo"},"arrival":"08:00","arrivalDay":2,"departure":"08:10","departureDay":2,"distance":90}
            ]}}
        """.trimIndent()
        assertEquals(listOf(0, 1), RailRadarParser.parseSchedule(json).map { it.dayOffset })
    }

    @Test
    fun `a schedule with no route is malformed`() {
        assertKind(RailRadarErrorKind.MALFORMED) {
            RailRadarParser.parseSchedule("""{"success":true,"data":{"trainNumber":"12991"}}""")
        }
    }

    @Test
    fun `a single-stop schedule keeps its one station`() {
        val json = """
            {"success":true,"data":{"route":[
                {"sequence":1,"stationCode":"A","stationName":"Alpha","scheduledArrival":"06:00","scheduledDeparture":"06:10","distance":0}
            ]}}
        """.trimIndent()
        val stops = RailRadarParser.parseSchedule(json)
        assertEquals(1, stops.size)
        assertNull(stops.first().scheduledArrival)
    }

    // ── PNR ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a PNR fills the booking, route ends and boarding point`() {
        val ticket = RailRadarParser.parsePnr(pnrFull)

        assertEquals("2957123456", ticket.pnr)
        assertEquals("12991", ticket.trainNumber)
        assertEquals("Udaipur City SF Express", ticket.trainName)
        assertEquals("3A", ticket.travelClass)
        assertEquals("GN", ticket.quota)
        assertEquals("JP", ticket.originCode)
        assertEquals("UDZ", ticket.destinationCode)
        assertEquals("AII", ticket.boardingCode)
        assertEquals("Ajmer Junction", ticket.boardingName)
        // A PNR carries no clock times; the editor keeps whatever the form already has.
        assertNull(ticket.departure)
        assertNull(ticket.arrival)
        assertTrue(ticket.warnings.isEmpty())
    }

    @Test
    fun `PNR passengers get coach, berth and status but never names`() {
        val ticket = RailRadarParser.parsePnr(pnrFull)
        assertEquals(2, ticket.passengers.size)

        val first = ticket.passengers.first()
        assertEquals("", first.name)
        assertNull(first.age)
        assertEquals(PassengerGender.UNSPECIFIED, first.gender)
        assertEquals(TrainBookingStatus.CONFIRMED, first.allotment.status)
        assertEquals("S3", first.allotment.coach)
        assertEquals("56", first.allotment.berth)
        assertEquals(BerthType.SIDE_UPPER, first.allotment.berthType)
    }

    @Test
    fun `a waitlisted PNR passenger carries a queue position, not a berth`() {
        val ticket = RailRadarParser.parsePnr(pnrWaitlist)
        val passenger = ticket.passengers.first()

        assertEquals(TrainBookingStatus.WAITLISTED, passenger.allotment.status)
        assertEquals(24, passenger.allotment.queuePosition)
        assertEquals("", passenger.allotment.berth)
    }

    @Test
    fun `a cancelled PNR passenger keeps no seat`() {
        val ticket = RailRadarParser.parsePnr(pnrCancelled)
        val passenger = ticket.passengers.first()

        assertEquals(TrainBookingStatus.CANCELLED, passenger.allotment.status)
        assertFalse(passenger.allotment.hasSeat)
    }

    @Test
    fun `passenger status falls back to the chart text when flags are absent`() {
        val ticket = RailRadarParser.parsePnr(pnrTextStatus)
        val passenger = ticket.passengers.first()

        assertEquals(TrainBookingStatus.CONFIRMED, passenger.allotment.status)
        assertEquals(BerthType.LOWER, passenger.allotment.berthType)
    }

    @Test
    fun `a string boolean flag is still read`() {
        val json = """
            {"success":true,"data":{"pnrNumber":"1","train":{"number":"1"},
                "passengers":[{"passengerNumber":1,"isConfirmed":"true","coach":"A1","berthNumber":5,"berthCode":"LB"}]}}
        """.trimIndent()
        val ticket = RailRadarParser.parsePnr(json)
        assertEquals(TrainBookingStatus.CONFIRMED, ticket.passengers.first().allotment.status)
    }

    @Test
    fun `a PNR with no passengers warns rather than failing`() {
        val ticket = RailRadarParser.parsePnr(pnrNoPassengers)
        assertTrue(ticket.passengers.isEmpty())
        assertEquals(listOf(TicketImportWarning.NO_PASSENGERS_FOUND), ticket.warnings)
    }

    @Test
    fun `a PNR failure envelope is classified`() {
        assertKind(RailRadarErrorKind.NOT_FOUND) {
            RailRadarParser.parsePnr("""{"success":false,"message":"PNR not found"}""")
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────

    private fun parseLive(json: String, trainId: Long = 1L): TrainRunStatus =
        RailRadarParser.parseLiveStatus(json, trainId = trainId, fetchedAt = fetchedAt, requestedDate = requestedDate)

    private fun failure(message: String): String = """{"success":false,"message":"$message"}"""

    private inline fun assertKind(expected: RailRadarErrorKind, block: () -> Unit) {
        try {
            block()
            fail("expected RailRadarException($expected)")
        } catch (e: RailRadarException) {
            assertEquals(expected, e.kind)
        }
    }

    // ── Fixtures ───────────────────────────────────────────────────────────────────────────

    /** JP→AII→COR→UDZ, train standing at COR (seq 3), 14 minutes down, JP and AII behind it. */
    private val liveMidRun = """
        {"success":true,"data":{
            "trainNumber":"12991","delayMinutes":14,"startDate":"2026-08-21","status":"Running",
            "currentLocation":{"stationCode":"COR","sequence":3},
            "nextHalt":{"stationCode":"COR","stationName":"Chittaurgarh"},
            "route":[
                {"sequence":1,"stationCode":"JP","stationName":"Jaipur","scheduledDeparture":"06:20","actualDeparture":"06:20","distance":0,"status":"DEPARTED"},
                {"sequence":2,"stationCode":"AII","stationName":"Ajmer","scheduledArrival":"08:35","actualArrival":"08:49","scheduledDeparture":"08:55","actualDeparture":"09:09","delayArrival":14,"delayDeparture":14,"distance":135,"status":"DEPARTED"},
                {"sequence":3,"stationCode":"COR","stationName":"Chittaurgarh","scheduledArrival":"11:40","scheduledDeparture":"11:50","distance":248,"status":"UPCOMING"},
                {"sequence":4,"stationCode":"UDZ","stationName":"Udaipur","scheduledArrival":"13:05","distance":383,"status":"UPCOMING"}
            ]
        }}
    """.trimIndent()

    /** Two stops whose times are full ISO datetimes rather than bare `HH:mm`. */
    private val liveIsoTimes = """
        {"success":true,"data":{
            "startDate":"2026-08-21",
            "route":[
                {"sequence":1,"stationCode":"JP","stationName":"Jaipur","scheduledDeparture":"2026-08-21T06:20:00+05:30","actualDeparture":"2026-08-21T06:20:00+05:30","distance":0,"status":"DEPARTED"},
                {"sequence":2,"stationCode":"UDZ","stationName":"Udaipur","scheduledArrival":"2026-08-21T13:05:00","actualArrival":"2026-08-21T13:12:00","distance":383,"status":"ARRIVED"}
            ]
        }}
    """.trimIndent()

    /** Departs 23:10, reaches AII (seq 2) at 01:50 the next morning: the day rolls over once. */
    private val liveOvernight = """
        {"success":true,"data":{
            "startDate":"2026-08-21","delayMinutes":0,
            "currentLocation":{"stationCode":"AII","sequence":2},
            "route":[
                {"sequence":1,"stationCode":"JP","stationName":"Jaipur","scheduledDeparture":"23:10","actualDeparture":"23:10","distance":0,"status":"DEPARTED"},
                {"sequence":2,"stationCode":"AII","stationName":"Ajmer","scheduledArrival":"01:40","actualArrival":"01:40","scheduledDeparture":"01:50","actualDeparture":"01:50","distance":300,"status":"DEPARTED"},
                {"sequence":3,"stationCode":"UDZ","stationName":"Udaipur","scheduledArrival":"05:15","distance":383,"status":"UPCOMING"}
            ]
        }}
    """.trimIndent()

    /** No current location, no actuals, no startDate: a run that has not begun. */
    private val liveNotStarted = """
        {"success":true,"data":{
            "delayMinutes":0,
            "nextHalt":{"stationCode":"JP","stationName":"Jaipur"},
            "route":[
                {"sequence":1,"stationCode":"JP","stationName":"Jaipur","scheduledDeparture":"06:20","distance":0,"status":"UPCOMING"},
                {"sequence":2,"stationCode":"UDZ","stationName":"Udaipur","scheduledArrival":"13:05","distance":383,"status":"UPCOMING"}
            ]
        }}
    """.trimIndent()

    /** Terminus reached: it has an actual arrival but never an actual departure. */
    private val liveArrived = """
        {"success":true,"data":{
            "startDate":"2026-08-21",
            "currentLocation":{"stationCode":"UDZ","sequence":2},
            "route":[
                {"sequence":1,"stationCode":"JP","stationName":"Jaipur","scheduledDeparture":"06:20","actualDeparture":"06:20","distance":0,"status":"DEPARTED"},
                {"sequence":2,"stationCode":"UDZ","stationName":"Udaipur","scheduledArrival":"13:05","actualArrival":"13:12","distance":383,"status":"ARRIVED"}
            ]
        }}
    """.trimIndent()

    /** delayMinutes and one sequence/distance are quoted; one distance is a decimal. */
    private val liveMixedTypes = """
        {"success":true,"data":{
            "delayMinutes":"12","startDate":"2026-08-21",
            "currentLocation":{"stationCode":"B","sequence":2},
            "route":[
                {"sequence":1,"stationCode":"A","stationName":"Alpha","scheduledDeparture":"06:00","actualDeparture":"06:00","distance":0,"status":"DEPARTED"},
                {"sequence":"2","stationCode":"B","stationName":"Bravo","scheduledArrival":"07:00","actualArrival":"07:05","distance":"150","status":"DEPARTED"},
                {"sequence":3,"stationCode":"C","stationName":"Charlie","scheduledArrival":"08:30","distance":300.5,"status":"UPCOMING"}
            ]
        }}
    """.trimIndent()

    /** A good row, a row with neither code nor name, then another good row. */
    private val liveJunkRow = """
        {"success":true,"data":{
            "startDate":"2026-08-21",
            "route":[
                {"sequence":1,"stationCode":"A","stationName":"Alpha","scheduledDeparture":"06:00","actualDeparture":"06:00","distance":0,"status":"DEPARTED"},
                {"sequence":2,"note":"signal failure"},
                {"sequence":3,"stationCode":"C","stationName":"Charlie","scheduledArrival":"08:30","distance":300,"status":"UPCOMING"}
            ]
        }}
    """.trimIndent()

    /** The second stop has a code but no name. */
    private val liveCodeOnly = """
        {"success":true,"data":{
            "startDate":"2026-08-21",
            "route":[
                {"sequence":1,"stationCode":"A","stationName":"Alpha","scheduledDeparture":"06:00","actualDeparture":"06:00","distance":0,"status":"DEPARTED"},
                {"sequence":2,"stationCode":"XYZ","scheduledArrival":"07:00","distance":200,"status":"UPCOMING"}
            ]
        }}
    """.trimIndent()

    /** Real schedule shape: station nested under `station:{code,name}`, bare `HH:mm`, 1-based days. */
    private val scheduleFull = """
        {"success":true,"data":{"route":[
            {"sequence":1,"station":{"code":"JP","name":"Jaipur"},"departure":"06:20","departureDay":1,"distance":0},
            {"sequence":2,"station":{"code":"AII","name":"Ajmer"},"arrival":"08:35","arrivalDay":1,"departure":"08:40","departureDay":1,"distance":135},
            {"sequence":3,"station":{"code":"UDZ","name":"Udaipur"},"arrival":"13:05","arrivalDay":1,"distance":383}
        ]}}
    """.trimIndent()

    /** Nested station, no day fields: the day rollover must be derived from the times themselves. */
    private val scheduleOvernight = """
        {"success":true,"data":{"route":[
            {"sequence":1,"station":{"code":"ORG","name":"Origin"},"departure":"22:40","distance":0},
            {"sequence":2,"station":{"code":"MID","name":"Middle"},"arrival":"01:20","departure":"01:30","distance":200},
            {"sequence":3,"station":{"code":"END","name":"Endpoint"},"arrival":"05:15","distance":383}
        ]}}
    """.trimIndent()

    /** Bravo arrives 23:58 and leaves 00:03 — the rollover happens inside the halt, days derived. */
    private val scheduleMidnightHalt = """
        {"success":true,"data":{"route":[
            {"sequence":1,"station":{"code":"A","name":"Alpha"},"departure":"20:00","distance":0},
            {"sequence":2,"station":{"code":"B","name":"Bravo"},"arrival":"23:58","departure":"00:03","distance":300},
            {"sequence":3,"station":{"code":"C","name":"Charlie"},"arrival":"02:30","distance":400}
        ]}}
    """.trimIndent()

    private val pnrFull = """
        {"success":true,"data":{
            "pnrNumber":"2957123456",
            "train":{"number":"12991","name":"Udaipur City SF Express",
                "source":{"code":"JP","name":"Jaipur"},
                "destination":{"code":"UDZ","name":"Udaipur City"},
                "boardingPoint":{"code":"AII","name":"Ajmer Junction"}},
            "journey":{"date":"2026-08-21","class":"3A","quota":"GN"},
            "passengers":[
                {"passengerNumber":1,"bookingStatus":"CNF/S3/56/SU","currentStatus":"CNF/S3/56","coach":"S3","berthNumber":56,"berthCode":"SU","isConfirmed":true},
                {"passengerNumber":2,"bookingStatus":"CNF/S3/57/SL","currentStatus":"CNF/S3/57","coach":"S3","berthNumber":57,"berthCode":"SL","isConfirmed":true}
            ]
        }}
    """.trimIndent()

    private val pnrWaitlist = """
        {"success":true,"data":{
            "pnrNumber":"2957123457","train":{"number":"12991"},
            "passengers":[
                {"passengerNumber":1,"bookingStatus":"GNWL/45","currentStatus":"WL/24","isWaitlisted":true}
            ]
        }}
    """.trimIndent()

    private val pnrCancelled = """
        {"success":true,"data":{
            "pnrNumber":"2957123458","train":{"number":"12991"},
            "passengers":[
                {"passengerNumber":1,"currentStatus":"CAN","isCancelled":true,"coach":"S3","berthNumber":56,"berthCode":"SU"}
            ]
        }}
    """.trimIndent()

    private val pnrTextStatus = """
        {"success":true,"data":{
            "pnrNumber":"2957123459","train":{"number":"12991"},
            "passengers":[
                {"passengerNumber":1,"bookingStatus":"CNF/B2/12/LB","currentStatus":"CNF/B2/12","coach":"B2","berthNumber":12,"berthCode":"LB"}
            ]
        }}
    """.trimIndent()

    private val pnrNoPassengers = """
        {"success":true,"data":{"pnrNumber":"2957123460","train":{"number":"12991"},"passengers":[]}}
    """.trimIndent()
}
