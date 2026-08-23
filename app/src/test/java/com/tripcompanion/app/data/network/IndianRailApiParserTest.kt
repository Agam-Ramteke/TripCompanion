package com.tripcompanion.app.data.network

import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.service.TrainStatusError
import com.tripcompanion.app.domain.service.TrainStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The railway API's text conventions, one at a time.
 *
 * This is the file the plan called out as carrying the heaviest coverage, because every fact
 * this API reports arrives as a string in a slightly different shape from the last one, and a
 * parser that quietly mistakes `"Source"` for a time puts a phantom arrival on the first row
 * of every route screen. Nothing here touches a network: the parser is pure by design so that
 * the payloads it has to survive can be written down.
 *
 * The fixtures use real station codes for the same reason [com.tripcompanion.app.data.SampleTripSeeder]
 * does — §4 forbids *code* that branches on a place, not test data that looks like the wire.
 */
class IndianRailApiParserTest {

    private val fetchedAt = LocalDateTime.of(2026, 8, 21, 9, 30)
    private val runDate = LocalDate.of(2026, 8, 21)

    // ---- Times -------------------------------------------------------------

    @Test
    fun `a twelve-hour afternoon time becomes a twenty-four hour time`() {
        assertEquals(LocalTime.of(16, 27), IndianRailApiParser.parseTime("04:27PM"))
    }

    @Test
    fun `a space before the meridiem is tolerated`() {
        assertEquals(LocalTime.of(16, 27), IndianRailApiParser.parseTime("04:27 PM"))
    }

    @Test
    fun `midnight is parsed as zero rather than twelve`() {
        assertEquals(LocalTime.of(0, 5), IndianRailApiParser.parseTime("12:05AM"))
    }

    @Test
    fun `noon stays at twelve`() {
        assertEquals(LocalTime.of(12, 30), IndianRailApiParser.parseTime("12:30PM"))
    }

    /** §9: a time in this app is hours and minutes. A stray `:00` must not survive parsing. */
    @Test
    fun `the schedule endpoint's seconds are dropped`() {
        assertEquals(LocalTime.of(16, 27), IndianRailApiParser.parseTime("16:27:00"))
    }

    @Test
    fun `a single-digit hour is accepted`() {
        assertEquals(LocalTime.of(4, 27), IndianRailApiParser.parseTime("4:27"))
    }

    @Test
    fun `the API's absent marker is not a time`() {
        assertNull(IndianRailApiParser.parseTime("-"))
        assertNull(IndianRailApiParser.parseTime("--"))
        assertNull(IndianRailApiParser.parseTime("N/A"))
    }

    /**
     * The two label strings the API puts where a time goes.
     *
     * These are the reason the app never renders "Source" in a time slot: they are parsed away
     * at the boundary instead of being carried inwards as sentinel text.
     */
    @Test
    fun `Source and Destination are labels, not times`() {
        assertNull(IndianRailApiParser.parseTime("Source"))
        assertNull(IndianRailApiParser.parseTime("Destination"))
    }

    @Test
    fun `a null or empty time is null`() {
        assertNull(IndianRailApiParser.parseTime(null))
        assertNull(IndianRailApiParser.parseTime(""))
        assertNull(IndianRailApiParser.parseTime("   "))
    }

    @Test
    fun `an impossible clock reading is rejected rather than wrapped`() {
        assertNull(IndianRailApiParser.parseTime("25:00"))
        assertNull(IndianRailApiParser.parseTime("10:75"))
        assertNull(IndianRailApiParser.parseTime("tomorrow"))
    }

    // ---- Delays ------------------------------------------------------------

    @Test
    fun `a delay in minutes is read out of its unit suffix`() {
        assertEquals(14, IndianRailApiParser.parseDelayMinutes("14 M"))
        assertEquals(14, IndianRailApiParser.parseDelayMinutes("14M"))
        assertEquals(14, IndianRailApiParser.parseDelayMinutes("14 MIN"))
        assertEquals(14, IndianRailApiParser.parseDelayMinutes("14 MINUTES"))
    }

    @Test
    fun `a zero-padded delay is on time, not missing`() {
        assertEquals(0, IndianRailApiParser.parseDelayMinutes("00 M"))
    }

    /** A train can run early, and the sign has to survive. */
    @Test
    fun `a negative delay means early`() {
        assertEquals(-5, IndianRailApiParser.parseDelayMinutes("-5 M"))
    }

    @Test
    fun `the railway's words for punctual are zero`() {
        assertEquals(0, IndianRailApiParser.parseDelayMinutes("Right Time"))
        assertEquals(0, IndianRailApiParser.parseDelayMinutes("RIGHT TIME"))
        assertEquals(0, IndianRailApiParser.parseDelayMinutes("On Time"))
    }

    /**
     * Null, not zero.
     *
     * An unreported delay is not a claim that the train is punctual, and the difference decides
     * whether the screen shows "On time" or an em dash.
     */
    @Test
    fun `an unreported delay is null rather than zero`() {
        assertNull(IndianRailApiParser.parseDelayMinutes("-"))
        assertNull(IndianRailApiParser.parseDelayMinutes(null))
        assertNull(IndianRailApiParser.parseDelayMinutes("unknown"))
    }

    // ---- Dates -------------------------------------------------------------

    /** The request asks in `yyyyMMdd`; the response answers in `dd-MM-yyyy`. Not the same. */
    @Test
    fun `the request date format is not the response date format`() {
        assertEquals("20260821", IndianRailApiParser.formatRequestDate(runDate))
        assertEquals(runDate, IndianRailApiParser.parseResponseDate("21-08-2026"))
    }

    @Test
    fun `an ISO date is not what this API sends and is refused`() {
        assertNull(IndianRailApiParser.parseResponseDate("2026-08-21"))
    }

    @Test
    fun `a missing start date is null so the caller can fall back`() {
        assertNull(IndianRailApiParser.parseResponseDate("-"))
        assertNull(IndianRailApiParser.parseResponseDate(null))
    }

    // ---- Live status: the whole document -----------------------------------

    @Test
    fun `a live response mid-run yields position, delay, next stop and speed`() {
        val status = IndianRailApiParser.parseLiveStatus(
            json = liveMidRun,
            trainId = 7L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        )

        assertEquals(7L, status.trainId)
        assertEquals(fetchedAt, status.fetchedAt)
        assertEquals(TrainRunSource.LIVE, status.source)
        assertEquals(runDate, status.runDate)
        assertEquals("SUCCESS", status.message)

        // CurrentStation names serial 3, so that is the position — not the last one departed.
        assertEquals("COR", status.currentStationCode)
        assertEquals("Chittaurgarh", status.currentStationName)

        // Distance covered over distance total: 248 of 383 km. Never a station count.
        assertEquals(248f / 383f, status.progressFraction, 0.0001f)

        assertEquals(2, status.lastDepartedSerial)
        assertTrue(status.hasStarted)
        assertFalse(status.hasArrived)

        // The current station reports no delay of its own, so the last departed one answers.
        assertEquals(14, status.delayMinutes)
        assertTrue(status.isDelayed)

        assertEquals("COR", status.nextStopCode)
        assertEquals("Chittaurgarh", status.nextStopName)
        // 11:40 scheduled, pushed back by the 14 minutes already being carried.
        assertEquals(LocalTime.of(11, 54), status.nextStopEta)

        // 135 km between two reported departures 169 minutes apart.
        assertNotNull(status.averageSpeedKmph)
        assertEquals(47.93, status.averageSpeedKmph!!, 0.01)
    }

    @Test
    fun `the origin has no arrival and the terminus has no departure`() {
        val stops = IndianRailApiParser.parseLiveStatus(
            json = liveMidRun,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        ).stops

        assertEquals(4, stops.size)
        assertNull("\"Source\" must not become a time", stops.first().scheduledArrival)
        assertNull("\"Destination\" must not become a time", stops.last().scheduledDeparture)
        assertEquals(LocalTime.of(6, 20), stops.first().scheduledDeparture)
        assertEquals(LocalTime.of(13, 5), stops.last().scheduledArrival)
    }

    /**
     * `Day` is 1-based on the wire and 0-based in the app.
     *
     * Live status trusts this field rather than deriving rollover from the clock, because the
     * railway knows which day of the run a station belongs to and a very late train can reach
     * a stop whose scheduled time reads earlier than the last actual departure without any
     * midnight having passed.
     */
    @Test
    fun `the Day field is normalised so day one is offset zero`() {
        val stops = IndianRailApiParser.parseLiveStatus(
            json = liveOvernight,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        ).stops

        assertEquals(listOf(0, 1, 1), stops.map { it.dayOffset })
    }

    /**
     * An overnight run must not come out as a negative elapsed time.
     *
     * Without folding `dayOffset` in, a 23:10 departure followed by a 01:40 arrival reads as
     * minus twenty-one hours and produces a nonsense speed.
     */
    @Test
    fun `speed across midnight stays positive`() {
        val status = IndianRailApiParser.parseLiveStatus(
            json = liveOvernight,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        )

        assertNotNull(status.averageSpeedKmph)
        assertTrue(
            "speed across midnight was ${status.averageSpeedKmph}",
            status.averageSpeedKmph!! > 0.0
        )
        // 300 km in the 150 minutes between 23:10 on day one and 01:40 on day two.
        assertEquals(120.0, status.averageSpeedKmph!!, 0.01)
    }

    /**
     * When the API contradicts itself, the screen shows an em dash rather than a lie.
     *
     * A stop that reports a 01:40 arrival while still claiming to be on day one implies a
     * journey that took minus twenty-one hours. There is no honest speed to derive from that,
     * so none is derived — the alternative is a route screen confidently reporting a negative
     * or wildly inflated figure.
     */
    @Test
    fun `an inconsistent Day yields no speed rather than a nonsense one`() {
        val status = IndianRailApiParser.parseLiveStatus(
            json = liveInconsistentDay,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        )

        assertNull(status.averageSpeedKmph)
        // The rest of the document still parses: one bad derivation is not a failed request.
        assertEquals(2, status.stops.size)
        assertEquals(300, status.stops.last().distanceKm)
    }

    @Test
    fun `a train that has left no station reports no progress and no speed`() {        val status = IndianRailApiParser.parseLiveStatus(
            json = liveNotStarted,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        )

        assertEquals(0, status.lastDepartedSerial)
        assertFalse(status.hasStarted)
        assertEquals(0f, status.progressFraction, 0.0001f)
        assertNull("one reported time cannot make an average", status.averageSpeedKmph)
        assertEquals("JP", status.nextStopCode)
    }

    @Test
    fun `a completed run reads as fully arrived`() {
        val status = IndianRailApiParser.parseLiveStatus(
            json = liveArrived,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        )

        assertEquals(1f, status.progressFraction, 0.0001f)
        assertTrue(status.hasArrived)
        assertNull("nothing is next once the run is over", status.nextStop())
        assertEquals("", status.nextStopCode)
    }

    @Test
    fun `a missing StartDate falls back to the date that was requested`() {
        val status = IndianRailApiParser.parseLiveStatus(
            json = liveNoStartDate,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        )

        assertEquals(runDate, status.runDate)
    }

    /** Quoted numbers, bare numbers and the several spellings of yes the API has used. */
    @Test
    fun `numbers and booleans are read whether quoted or bare`() {
        val stops = IndianRailApiParser.parseLiveStatus(
            json = liveMixedTypes,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        ).stops

        assertEquals(listOf(0, 135, 248), stops.map { it.distanceKm })
        assertEquals(listOf(true, true, false), stops.map { it.isDeparted })
    }

    /** One bad row should cost that row, not a forty-station route. */
    @Test
    fun `an unusable station row is skipped rather than failing the request`() {
        val stops = IndianRailApiParser.parseLiveStatus(
            json = liveWithJunkRow,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        ).stops

        assertEquals(2, stops.size)
        assertEquals(listOf("JP", "AII"), stops.map { it.stationCode })
    }

    @Test
    fun `a station with a code but no name falls back to the code`() {
        val stops = IndianRailApiParser.parseLiveStatus(
            json = liveWithJunkRow,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        ).stops

        assertEquals("AII", stops.last().stationName)
    }

    // ---- Live status: failures ---------------------------------------------

    @Test
    fun `a response code of 404 means the train was not found`() {
        assertLiveError(TrainStatusError.TRAIN_NOT_FOUND, """{"ResponseCode":404}""")
    }

    @Test
    fun `a response code of 204 also means the train was not found`() {
        assertLiveError(TrainStatusError.TRAIN_NOT_FOUND, """{"ResponseCode":204}""")
    }

    @Test
    fun `a response code of 429 means rate limited`() {
        assertLiveError(TrainStatusError.RATE_LIMITED, """{"ResponseCode":429}""")
    }

    @Test
    fun `any other non-200 code is a provider error`() {
        assertLiveError(TrainStatusError.PROVIDER_ERROR, """{"ResponseCode":503}""")
    }

    /**
     * The shape that matters most: HTTP 200, no route, and an explanation in words.
     *
     * This is how this API says "that train isn't running today", and reading it as success
     * would leave the screen showing an empty route with no reason given.
     */
    @Test
    fun `a successful code with a not-found message is still not found`() {
        assertLiveError(
            TrainStatusError.TRAIN_NOT_FOUND,
            """{"ResponseCode":200,"Message":"No record found for this train"}"""
        )
    }

    @Test
    fun `a quota message is rate limited`() {
        assertLiveError(
            TrainStatusError.RATE_LIMITED,
            """{"ResponseCode":200,"Message":"Daily quota exceeded"}"""
        )
    }

    @Test
    fun `a key message is a provider error`() {
        assertLiveError(
            TrainStatusError.PROVIDER_ERROR,
            """{"ResponseCode":200,"Message":"Invalid API key"}"""
        )
    }

    @Test
    fun `text that is not JSON is malformed`() {
        assertLiveError(TrainStatusError.MALFORMED_RESPONSE, "<html>gateway timeout</html>")
    }

    @Test
    fun `a success with no route array is malformed`() {
        assertLiveError(
            TrainStatusError.MALFORMED_RESPONSE,
            """{"ResponseCode":200,"Message":"SUCCESS"}"""
        )
    }

    @Test
    fun `a route array with nothing usable in it is malformed`() {
        assertLiveError(
            TrainStatusError.MALFORMED_RESPONSE,
            """{"ResponseCode":200,"Message":"SUCCESS","TrainRoute":[{"Foo":"bar"}]}"""
        )
    }

    /**
     * A note is not an error.
     *
     * Some successful responses carry a remark instead of the word SUCCESS. Throwing away a
     * complete route over its wording would lose real data, so a message with a route attached
     * is allowed through.
     */
    @Test
    fun `an unrecognised message with a route attached is allowed through`() {
        val status = IndianRailApiParser.parseLiveStatus(
            json = liveWithRemark,
            trainId = 1L,
            fetchedAt = fetchedAt,
            requestedDate = runDate
        )

        assertEquals("Train is running late due to fog", status.message)
        assertEquals(2, status.stops.size)
    }

    // ---- Schedule ----------------------------------------------------------

    @Test
    fun `a schedule is read in route order with its endpoints trimmed`() {
        val stops = IndianRailApiParser.parseSchedule(scheduleOvernight)

        assertEquals(3, stops.size)
        assertEquals(listOf(1, 2, 3), stops.map { it.serialNo })
        assertEquals(listOf("JP", "AII", "UDZ"), stops.map { it.stationCode })

        assertTrue("the origin is where the train starts", stops.first().isOrigin)
        assertNull(stops.first().scheduledArrival)
        assertTrue("the terminus is where it stops", stops.last().isTerminus)
        assertNull(stops.last().scheduledDeparture)
    }

    /**
     * The schedule endpoint has no `Day` field at all, so rollover has to be derived.
     *
     * Every time the clock runs backwards along the route, another midnight has passed.
     */
    @Test
    fun `a day offset is derived from times running backwards`() {
        val stops = IndianRailApiParser.parseSchedule(scheduleOvernight)

        assertEquals(listOf(0, 1, 1), stops.map { it.dayOffset })
    }

    /** Arrive 23:58, leave 00:03: the rollover belongs to the stop after the halt. */
    @Test
    fun `a halt straddling midnight rolls the following stop over`() {
        val stops = IndianRailApiParser.parseSchedule(scheduleMidnightHalt)

        assertEquals(listOf(0, 0, 1), stops.map { it.dayOffset })
    }

    @Test
    fun `a schedule carries distances and no delay information`() {
        val stops = IndianRailApiParser.parseSchedule(scheduleOvernight)

        assertEquals(listOf(0, 200, 383), stops.map { it.distanceKm })
        // TrainStop has no delay fields at all — that is TrainStopStatus's job — so the only
        // thing to assert is that the timetable times survived intact.
        assertEquals(LocalTime.of(22, 40), stops.first().scheduledDeparture)
        assertEquals(LocalTime.of(5, 15), stops.last().scheduledArrival)
    }

    @Test
    fun `a schedule with no route is malformed`() {
        try {
            IndianRailApiParser.parseSchedule("""{"ResponseCode":200,"Status":"SUCCESS"}""")
            fail("expected a MALFORMED_RESPONSE")
        } catch (e: TrainStatusException) {
            assertEquals(TrainStatusError.MALFORMED_RESPONSE, e.error)
        }
    }

    @Test
    fun `a single-stop schedule keeps its departure and loses its arrival`() {
        val stops = IndianRailApiParser.parseSchedule(scheduleSingleStop)

        assertEquals(1, stops.size)
        assertNull(stops.single().scheduledArrival)
        assertEquals(LocalTime.of(6, 20), stops.single().scheduledDeparture)
    }

    // ---- Helpers -----------------------------------------------------------

    private fun assertLiveError(expected: TrainStatusError, json: String) {
        try {
            IndianRailApiParser.parseLiveStatus(json, 1L, fetchedAt, runDate)
            fail("expected $expected but the document parsed")
        } catch (e: TrainStatusException) {
            assertEquals(expected, e.error)
        }
    }

    // ---- Fixtures ----------------------------------------------------------

    /**
     * A train two stations out, running fourteen minutes down.
     *
     * Serial 3 is flagged current while serials 1 and 2 have departed, which is the case that
     * separates "where the railway says it is" from "the furthest station it has left".
     */
    private val liveMidRun = """
        {
          "ResponseCode": 200,
          "StartDate": "21-08-2026",
          "TrainNumber": "12992",
          "CurrentPosition": "20 KM before Chittaurgarh",
          "CurrentStation": {
            "SerialNo": "3",
            "StationName": "Chittaurgarh",
            "StationCode": "COR"
          },
          "TrainRoute": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "Distance": "0", "IsDeparted": true, "Day": "1",
              "ScheduleArrival": "Source", "ActualArrival": "Source",
              "ScheduleDeparture": "06:20AM", "ActualDeparture": "06:20AM",
              "DelayInArrival": "-", "DelayInDeparture": "00 M"
            },
            {
              "SerialNo": "2", "StationCode": "AII", "StationName": "Ajmer Junction",
              "Distance": "135", "IsDeparted": true, "Day": "1",
              "ScheduleArrival": "08:35AM", "ActualArrival": "08:49AM",
              "ScheduleDeparture": "08:55AM", "ActualDeparture": "09:09AM",
              "DelayInArrival": "14 M", "DelayInDeparture": "14 M"
            },
            {
              "SerialNo": "3", "StationCode": "COR", "StationName": "Chittaurgarh",
              "Distance": "248", "IsDeparted": false, "Day": "1",
              "ScheduleArrival": "11:40AM", "ActualArrival": "-",
              "ScheduleDeparture": "11:50AM", "ActualDeparture": "-",
              "DelayInArrival": "-", "DelayInDeparture": "-"
            },
            {
              "SerialNo": "4", "StationCode": "UDZ", "StationName": "Udaipur City",
              "Distance": "383", "IsDeparted": false, "Day": "1",
              "ScheduleArrival": "01:05PM", "ActualArrival": "-",
              "ScheduleDeparture": "Destination", "ActualDeparture": "Destination",
              "DelayInArrival": "-", "DelayInDeparture": "-"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()

    /** Departs 23:10 on day one, second stop 01:40 on day two. */
    private val liveOvernight = """
        {
          "ResponseCode": 200,
          "StartDate": "21-08-2026",
          "CurrentStation": { "SerialNo": "2", "StationCode": "AII" },
          "TrainRoute": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "Distance": "0", "IsDeparted": true, "Day": "1",
              "ScheduleArrival": "Source", "ScheduleDeparture": "11:10PM",
              "ActualDeparture": "11:10PM", "DelayInDeparture": "00 M"
            },
            {
              "SerialNo": "2", "StationCode": "AII", "StationName": "Ajmer Junction",
              "Distance": "300", "IsDeparted": true, "Day": "2",
              "ScheduleArrival": "01:40AM", "ScheduleDeparture": "01:50AM",
              "ActualArrival": "01:40AM", "ActualDeparture": "01:40AM",
              "DelayInArrival": "00 M", "DelayInDeparture": "00 M"
            },
            {
              "SerialNo": "3", "StationCode": "UDZ", "StationName": "Udaipur City",
              "Distance": "383", "IsDeparted": false, "Day": "2",
              "ScheduleArrival": "05:15AM", "ScheduleDeparture": "Destination",
              "DelayInArrival": "-"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()

    /** The same run with the rollover missing from the wire, which is not survivable arithmetic. */
    private val liveInconsistentDay = """
        {
          "ResponseCode": 200,
          "StartDate": "21-08-2026",
          "TrainRoute": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "Distance": "0", "IsDeparted": true, "Day": "1",
              "ScheduleArrival": "Source", "ScheduleDeparture": "11:10PM",
              "ActualDeparture": "11:10PM"
            },
            {
              "SerialNo": "2", "StationCode": "AII", "StationName": "Ajmer Junction",
              "Distance": "300", "IsDeparted": true, "Day": "1",
              "ScheduleArrival": "01:40AM", "ActualArrival": "01:40AM",
              "ScheduleDeparture": "01:50AM", "ActualDeparture": "01:40AM"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()

    private val liveNotStarted = """
        {
          "ResponseCode": 200,
          "StartDate": "21-08-2026",
          "TrainRoute": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "Distance": "0", "IsDeparted": false, "Day": "1",
              "ScheduleArrival": "Source", "ScheduleDeparture": "06:20AM",
              "ActualDeparture": "06:20AM"
            },
            {
              "SerialNo": "2", "StationCode": "UDZ", "StationName": "Udaipur City",
              "Distance": "383", "IsDeparted": false, "Day": "1",
              "ScheduleArrival": "01:05PM", "ScheduleDeparture": "Destination"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()

    private val liveArrived = """
        {
          "ResponseCode": 200,
          "StartDate": "21-08-2026",
          "CurrentStation": { "SerialNo": "2", "StationCode": "UDZ" },
          "TrainRoute": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "Distance": "0", "IsDeparted": true, "Day": "1",
              "ScheduleDeparture": "06:20AM", "ActualDeparture": "06:20AM",
              "DelayInDeparture": "00 M"
            },
            {
              "SerialNo": "2", "StationCode": "UDZ", "StationName": "Udaipur City",
              "Distance": "383", "IsDeparted": true, "Day": "1",
              "ScheduleArrival": "01:05PM", "ActualArrival": "01:12PM",
              "DelayInArrival": "07 M"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()

    private val liveNoStartDate = """
        {
          "ResponseCode": 200,
          "TrainRoute": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "Distance": "0", "IsDeparted": true, "ScheduleDeparture": "06:20AM"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()

    /** Distances bare, quoted and suffixed; three spellings of departed. */
    private val liveMixedTypes = """
        {
          "ResponseCode": 200,
          "TrainRoute": [
            {
              "SerialNo": 1, "StationCode": "JP", "StationName": "Jaipur",
              "Distance": 0, "IsDeparted": true, "ScheduleDeparture": "06:20AM"
            },
            {
              "SerialNo": "2", "StationCode": "AII", "StationName": "Ajmer Junction",
              "Distance": "135 km", "IsDeparted": "Y", "ScheduleArrival": "08:35AM"
            },
            {
              "SerialNo": "3", "StationCode": "COR", "StationName": "Chittaurgarh",
              "Distance": "248", "IsDeparted": "0", "ScheduleArrival": "11:40AM"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()

    /** Row two identifies nothing and must be dropped; row three has a code but no name. */
    private val liveWithJunkRow = """
        {
          "ResponseCode": 200,
          "TrainRoute": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "Distance": "0", "IsDeparted": true, "ScheduleDeparture": "06:20AM"
            },
            { "SerialNo": "2", "StationCode": "", "StationName": "", "Distance": "70" },
            {
              "SerialNo": "3", "StationCode": "AII", "StationName": "",
              "Distance": "135", "IsDeparted": false, "ScheduleArrival": "08:35AM"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()

    private val liveWithRemark = """
        {
          "ResponseCode": 200,
          "TrainRoute": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "Distance": "0", "IsDeparted": true, "ScheduleDeparture": "06:20AM"
            },
            {
              "SerialNo": "2", "StationCode": "UDZ", "StationName": "Udaipur City",
              "Distance": "383", "IsDeparted": false, "ScheduleArrival": "01:05PM"
            }
          ],
          "Message": "Train is running late due to fog"
        }
    """.trimIndent()

    /** No `Day` field anywhere, and times that run backwards twice. */
    private val scheduleOvernight = """
        {
          "ResponseCode": 200,
          "Status": "SUCCESS",
          "Route": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "ArrivalTime": "22:40:00", "DepartureTime": "22:40:00", "Distance": "0"
            },
            {
              "SerialNo": "2", "StationCode": "AII", "StationName": "Ajmer Junction",
              "ArrivalTime": "01:20:00", "DepartureTime": "01:30:00", "Distance": "200"
            },
            {
              "SerialNo": "3", "StationCode": "UDZ", "StationName": "Udaipur City",
              "ArrivalTime": "05:15:00", "DepartureTime": "05:15:00", "Distance": "383"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()

    private val scheduleMidnightHalt = """
        {
          "ResponseCode": 200,
          "Status": "SUCCESS",
          "Route": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "ArrivalTime": "20:00:00", "DepartureTime": "20:00:00", "Distance": "0"
            },
            {
              "SerialNo": "2", "StationCode": "AII", "StationName": "Ajmer Junction",
              "ArrivalTime": "23:58:00", "DepartureTime": "00:03:00", "Distance": "200"
            },
            {
              "SerialNo": "3", "StationCode": "UDZ", "StationName": "Udaipur City",
              "ArrivalTime": "02:00:00", "DepartureTime": "02:00:00", "Distance": "383"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()

    private val scheduleSingleStop = """
        {
          "ResponseCode": 200,
          "Status": "SUCCESS",
          "Route": [
            {
              "SerialNo": "1", "StationCode": "JP", "StationName": "Jaipur",
              "ArrivalTime": "06:20:00", "DepartureTime": "06:20:00", "Distance": "0"
            }
          ],
          "Message": "SUCCESS"
        }
    """.trimIndent()
}
