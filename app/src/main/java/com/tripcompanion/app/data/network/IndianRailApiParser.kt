package com.tripcompanion.app.data.network

import com.tripcompanion.app.domain.engine.TrainProgress
import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.model.TrainStopStatus
import com.tripcompanion.app.domain.service.TrainStatusError
import com.tripcompanion.app.domain.service.TrainStatusException
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Turns the railway API's JSON into the app's own types.
 *
 * Pure and separate from the provider that fetches it, because this is where the bugs are.
 * The API's conventions are all textual and all slightly different from each other:
 *
 * - Times arrive as `"04:27PM"` on the live endpoint and `"16:27:00"` on the schedule endpoint.
 * - Delays arrive as `"14 M"`, and `"00 M"` for on time.
 * - Anything unavailable is the string `"-"`.
 * - The origin's arrival field reads `"Source"`; the terminus' departure field reads
 *   `"Destination"`. Those are labels, not times, and they are parsed away here rather than
 *   carried into the app as sentinel text.
 * - The request takes `yyyyMMdd`; the response answers in `dd-MM-yyyy`.
 * - Numbers may be quoted or bare, so every numeric read goes through the same tolerant path.
 * - `Day` is 1-based and marks overnight rollover. The schedule endpoint has no `Day` at all,
 *   so rollover there is derived from times running backwards along the route.
 *
 * Two failure policies, matching [NominatimLocationSearchProvider]: a document that cannot be
 * understood at all raises [TrainStatusError.MALFORMED_RESPONSE], but a single unusable station
 * row is skipped. A route of forty stations should not be lost to one of them.
 */
internal object IndianRailApiParser {

    /** `yyyyMMdd`, which is what the live-status URL wants. */
    private val requestDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)

    /** `dd-MM-yyyy`, which is what the response answers with. Not the same thing. */
    private val responseDateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)

    /**
     * Matches every time shape the two endpoints use: `4:27PM`, `04:27 PM`, `16:27`, `16:27:00`.
     *
     * Seconds are matched so they can be discarded — §9 says a time in this app is
     * hours and minutes, and letting a stray `:30` through would eventually render one.
     */
    private val timePattern = Regex("""^(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)?$""")

    /** `"14 M"`, `"14M"`, `"14 MIN"`, `"-5 M"`, `"0"`. */
    private val delayPattern = Regex("""^(-?\d+)\s*(?:M|MIN|MINS|MINUTES)?$""")

    /** First integer in a string, so `"734"`, `"734.5"` and `" 734 km"` all read as 734. */
    private val integerPattern = Regex("""-?\d+""")

    /**
     * Values that mean "there is nothing here".
     *
     * `SOURCE` and `DESTINATION` are in the list because the API puts them where a time goes.
     * `NULL` is there because Android's `org.json` stringifies a JSON null as `"null"`.
     */
    private val absentValues = setOf("", "-", "--", "---", "N/A", "NA", "NULL", "SOURCE", "DESTINATION")

    /** Phrases the API returns with a 200 when the train number simply isn't running. */
    private val notFoundPhrases = listOf(
        "not found", "no record", "not available", "invalid train", "no train", "no data"
    )

    // ── Public surface ───────────────────────────────────────────────────────────────────

    /** Formats [date] for the live-status URL. */
    fun formatRequestDate(date: LocalDate): String = date.format(requestDateFormat)

    /**
     * Parses a live-status response into a snapshot.
     *
     * @param fetchedAt passed in rather than read from the clock, so the snapshot's age is the
     *   caller's fact and this stays testable.
     * @param requestedDate used when the response omits or mangles `StartDate`. The date the
     *   request asked about is a better answer than today.
     */
    fun parseLiveStatus(
        json: String,
        trainId: Long,
        fetchedAt: LocalDateTime,
        requestedDate: LocalDate
    ): TrainRunStatus {
        val root = readDocument(json)
        checkResponse(root)

        val route = root.optJSONArray("TrainRoute")
            ?: root.optJSONArray("Route")
            ?: throw TrainStatusException(
                TrainStatusError.MALFORMED_RESPONSE,
                "Response carried no route array"
            )

        val currentStation = root.optJSONObject("CurrentStation")
        val currentSerial = currentStation?.readInt("SerialNo")
        val currentCode = currentStation?.readString("StationCode") ?: ""

        val stops = readRunStops(route, currentSerial, currentCode)
        if (stops.isEmpty()) {
            throw TrainStatusException(
                TrainStatusError.MALFORMED_RESPONSE,
                "Route array held no usable stations"
            )
        }

        val ordered = stops.sortedBy { it.serialNo }
        val flagged = ordered.firstOrNull { it.isCurrent }
        val lastDeparted = ordered.filter { it.isDeparted }.maxByOrNull { it.serialNo }
        val delay = TrainProgress.currentDelayMinutes(ordered)
        val next = TrainProgress.nextStop(ordered)

        return TrainRunStatus(
            trainId = trainId,
            fetchedAt = fetchedAt,
            runDate = parseResponseDate(root.readString("StartDate", "TrainStartDate"))
                ?: requestedDate,
            source = TrainRunSource.LIVE,
            currentStationCode = flagged?.stationCode
                ?: currentCode.ifBlank { lastDeparted?.stationCode ?: "" },
            currentStationName = flagged?.stationName
                ?: currentStation?.readString("StationName")?.ifBlank { null }
                ?: lastDeparted?.stationName
                ?: "",
            delayMinutes = delay,
            lastDepartedSerial = TrainProgress.lastDepartedSerial(ordered),
            progressFraction = TrainProgress.progressFraction(ordered),
            nextStopCode = next?.stationCode ?: "",
            nextStopName = next?.stationName ?: "",
            nextStopEta = next?.let { TrainProgress.etaFor(it, delay) },
            averageSpeedKmph = TrainProgress.averageSpeedKmph(ordered),
            message = root.readString("Message", "Status"),
            stops = ordered
        )
    }

    /**
     * Parses a timetable response.
     *
     * The returned stops carry no `trainId` — that is the repository's to assign — and no
     * delay information, because a timetable does not have any.
     */
    fun parseSchedule(json: String): List<TrainStop> {
        val root = readDocument(json)
        checkResponse(root)

        val route = root.optJSONArray("Route")
            ?: root.optJSONArray("TrainRoute")
            ?: throw TrainStatusException(
                TrainStatusError.MALFORMED_RESPONSE,
                "Response carried no route array"
            )

        val stops = readScheduleStops(route)
        if (stops.isEmpty()) {
            throw TrainStatusException(
                TrainStatusError.MALFORMED_RESPONSE,
                "Route array held no usable stations"
            )
        }
        return assignDayOffsets(trimEndpoints(stops.sortedBy { it.serialNo }))
    }

    // ── Value parsing ────────────────────────────────────────────────────────────────────

    /**
     * `"04:27PM"` / `"16:27:00"` / `"-"` → a time or null.
     *
     * Null for anything unparseable rather than an exception: a single missing arrival time is
     * a normal thing on a route, and it should cost that one field, not the whole request.
     */
    fun parseTime(raw: String?): LocalTime? {
        val value = raw?.trim()?.uppercase(Locale.US) ?: return null
        if (value in absentValues) return null

        val match = timePattern.matchEntire(value) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null

        when (match.groupValues[4]) {
            "AM" -> if (hour == 12) hour = 0
            "PM" -> if (hour < 12) hour += 12
        }
        if (hour > 23 || minute > 59) return null
        // Seconds are matched and dropped on purpose. See [timePattern].
        return LocalTime.of(hour, minute)
    }

    /** `"14 M"` → 14, `"00 M"` → 0, `"-"` → null, `"Right Time"` → 0. */
    fun parseDelayMinutes(raw: String?): Int? {
        val value = raw?.trim()?.uppercase(Locale.US) ?: return null
        if (value in absentValues) return null
        if (value.startsWith("RIGHT TIME") || value == "ON TIME") return 0
        return delayPattern.matchEntire(value)?.groupValues?.get(1)?.toIntOrNull()
    }

    /** `"21-08-2026"` → a date, or null when the field is missing or misshapen. */
    fun parseResponseDate(raw: String?): LocalDate? {
        val value = raw?.trim() ?: return null
        if (value.uppercase(Locale.US) in absentValues) return null
        return try {
            LocalDate.parse(value, responseDateFormat)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    // ── Row reading ──────────────────────────────────────────────────────────────────────

    private fun readRunStops(
        route: JSONArray,
        currentSerial: Int?,
        currentCode: String
    ): List<TrainStopStatus> {
        val stops = mutableListOf<TrainStopStatus>()
        for (index in 0 until route.length()) {
            val row = route.optJSONObject(index) ?: continue

            val code = row.readString("StationCode")
            val name = row.readString("StationName")
            if (code.isEmpty() && name.isEmpty()) continue

            val serial = row.readInt("SerialNo") ?: (index + 1)
            val isCurrent = when {
                currentSerial != null -> serial == currentSerial
                currentCode.isNotEmpty() -> code.equals(currentCode, ignoreCase = true)
                // No current station reported: leave every stop unflagged rather than guess.
                // TrainProgress falls back to the furthest departed station, which is a fact.
                else -> false
            }

            stops += TrainStopStatus(
                serialNo = serial,
                stationCode = code,
                stationName = name.ifEmpty { code },
                scheduledArrival = parseTime(row.readString("ScheduleArrival", "ArrivalTime")),
                actualArrival = parseTime(row.readString("ActualArrival")),
                scheduledDeparture = parseTime(row.readString("ScheduleDeparture", "DepartureTime")),
                actualDeparture = parseTime(row.readString("ActualDeparture")),
                arrivalDelayMinutes = parseDelayMinutes(row.readString("DelayInArrival")),
                departureDelayMinutes = parseDelayMinutes(row.readString("DelayInDeparture")),
                distanceKm = row.readInt("Distance") ?: 0,
                // `Day` is 1-based in the response and 0-based here, so day one is offset zero.
                dayOffset = ((row.readInt("Day") ?: 1) - 1).coerceAtLeast(0),
                isDeparted = row.readBoolean("IsDeparted"),
                isCurrent = isCurrent
            )
        }
        return stops
    }

    private fun readScheduleStops(route: JSONArray): List<TrainStop> {
        val stops = mutableListOf<TrainStop>()
        for (index in 0 until route.length()) {
            val row = route.optJSONObject(index) ?: continue

            val code = row.readString("StationCode")
            val name = row.readString("StationName")
            if (code.isEmpty() && name.isEmpty()) continue

            stops += TrainStop(
                serialNo = row.readInt("SerialNo") ?: (index + 1),
                stationCode = code,
                stationName = name.ifEmpty { code },
                scheduledArrival = parseTime(row.readString("ArrivalTime", "ScheduleArrival")),
                scheduledDeparture = parseTime(row.readString("DepartureTime", "ScheduleDeparture")),
                distanceKm = row.readInt("Distance") ?: 0
            )
        }
        return stops
    }

    /**
     * Drops the arrival time at the origin and the departure time at the terminus.
     *
     * The API repeats the same time in both fields at each end of the route. A train does not
     * arrive at the station it starts from, and it does not leave the one it terminates at, so
     * carrying those values would put a phantom arrival on the first row of every route screen.
     */
    private fun trimEndpoints(stops: List<TrainStop>): List<TrainStop> {
        if (stops.isEmpty()) return stops
        if (stops.size == 1) return listOf(stops.first().copy(scheduledArrival = null))
        return stops.mapIndexed { index, stop ->
            when (index) {
                0 -> stop.copy(scheduledArrival = null)
                stops.lastIndex -> stop.copy(scheduledDeparture = null)
                else -> stop
            }
        }
    }

    /**
     * Works out which day of the run each stop falls on.
     *
     * The schedule endpoint reports clock times with no date, so a train leaving at 22:40 and
     * arriving at 05:15 looks like it goes backwards. Every time the clock runs backwards along
     * the route, another day has passed. Also handles a halt that straddles midnight — arrive
     * 23:58, leave 00:03 — by rolling over from the *following* stop.
     */
    private fun assignDayOffsets(stops: List<TrainStop>): List<TrainStop> {
        var day = 0
        var previous: LocalTime? = null
        return stops.map { stop ->
            val last = previous
            val entry = stop.scheduledArrival ?: stop.scheduledDeparture
            if (entry != null && last != null && entry < last) day++

            val dated = stop.copy(dayOffset = day)

            val arrival = stop.scheduledArrival
            val departure = stop.scheduledDeparture
            if (arrival != null && departure != null && departure < arrival) day++
            previous = departure ?: arrival ?: last

            dated
        }
    }

    // ── Document and status handling ──────────────────────────────────────────────────────

    private fun readDocument(json: String): JSONObject =
        try {
            JSONObject(json)
        } catch (e: Exception) {
            throw TrainStatusException(
                TrainStatusError.MALFORMED_RESPONSE,
                "Response was not a JSON object",
                e
            )
        }

    /**
     * Rejects an error response before anything is read out of it.
     *
     * Two shapes have to be caught. The documented one puts an HTTP-like code in
     * `ResponseCode`. The other returns 200 with an explanatory `Message` and no route, which
     * is how this API says "that train isn't running today" — a real answer to a real question,
     * and the one case where the phrasing has to be inspected rather than a code.
     */
    private fun checkResponse(root: JSONObject) {
        val code = root.readInt("ResponseCode")
        if (code != null && code != 200) {
            throw TrainStatusException(
                when (code) {
                    204, 404 -> TrainStatusError.TRAIN_NOT_FOUND
                    429 -> TrainStatusError.RATE_LIMITED
                    else -> TrainStatusError.PROVIDER_ERROR
                },
                "Provider responded with code $code"
            )
        }

        val message = root.readString("Message", "Status", "ErrorMessage")
        if (message.isEmpty() || message.equals("SUCCESS", ignoreCase = true)) return

        val lowered = message.lowercase(Locale.US)
        if (notFoundPhrases.any { lowered.contains(it) }) {
            throw TrainStatusException(TrainStatusError.TRAIN_NOT_FOUND, message)
        }
        if (lowered.contains("limit") || lowered.contains("quota")) {
            throw TrainStatusException(TrainStatusError.RATE_LIMITED, message)
        }
        if (lowered.contains("key") || lowered.contains("unauthor") || lowered.contains("subscri")) {
            throw TrainStatusException(TrainStatusError.PROVIDER_ERROR, message)
        }
        // Anything else with a route attached is allowed through: some successful responses
        // carry a note rather than the word SUCCESS, and discarding a valid route over its
        // wording would lose real data.
        if (root.optJSONArray("TrainRoute") == null && root.optJSONArray("Route") == null) {
            throw TrainStatusException(TrainStatusError.PROVIDER_ERROR, message)
        }
    }

    // ── Tolerant field readers ───────────────────────────────────────────────────────────

    /**
     * First non-empty value among [keys].
     *
     * Several keys per call because the two endpoints name the same fact differently
     * (`ScheduleArrival` against `ArrivalTime`), and one reader for both keeps the row-building
     * code from having to know which endpoint it came from.
     */
    private fun JSONObject.readString(vararg keys: String): String {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = optString(key, "").trim()
            if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) return value
        }
        return ""
    }

    /** First readable integer among [keys], quoted or bare, or null. */
    private fun JSONObject.readInt(vararg keys: String): Int? {
        for (key in keys) {
            val raw = readString(key)
            if (raw.isEmpty()) continue
            val digits = integerPattern.find(raw)?.value ?: continue
            digits.toIntOrNull()?.let { return it }
        }
        return null
    }

    /** `true`, `"true"`, `"1"`, `"Y"`, `"Yes"` — the API has used more than one of these. */
    private fun JSONObject.readBoolean(vararg keys: String): Boolean {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            if (opt(key) is Boolean) return optBoolean(key, false)
            val raw = readString(key).uppercase(Locale.US)
            if (raw.isEmpty()) continue
            return raw == "TRUE" || raw == "1" || raw == "Y" || raw == "YES"
        }
        return false
    }
}
