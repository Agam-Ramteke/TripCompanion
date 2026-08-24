package com.tripcompanion.app.data.network.railradar

import com.tripcompanion.app.domain.engine.TrainProgress
import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.ParsedTicket
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.TicketImportWarning
import com.tripcompanion.app.domain.model.TrainAllotment
import com.tripcompanion.app.domain.model.TrainBookingStatus
import com.tripcompanion.app.domain.model.TrainPassenger
import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.model.TrainStopStatus
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Turns RailRadar's JSON into the app's own types.
 *
 * Pure and separate from [RailRadarClient] that fetches it, because this is where the bugs are.
 * RailRadar is tidier than the vendor this replaced — real JSON numbers instead of `"14 M"`,
 * one envelope shape for every endpoint — but three things still need care:
 *
 * - Every response is wrapped `{ success, data, meta }`. Nothing is read until [checkEnvelope]
 *   has confirmed `success` and produced the inner `data`.
 * - Times are **not** documented as `HH:mm` or ISO, so [parseTime] pulls the first `H:mm` out of
 *   whatever string arrives — which reads `"16:27"`, `"2026-08-24T16:27:00"`, and
 *   `"...T16:27:00+05:30"` identically. Confirmed against a live device response.
 * - The two endpoints shape a station row differently: the schedule nests it under
 *   `station:{code,name}` with bare `arrival`/`departure`, the live route carries `stationCode`/
 *   `stationName` flat with ISO `scheduledArrival`/`scheduledDeparture`. The readers try both.
 * - Each stop states its day of the run (`arrivalDay`/`departureDay`, 1-based), which is trusted;
 *   only a response that omits it falls back to deriving rollover from times running backwards.
 *
 * Two failure policies, matching the old parser: a document that cannot be understood raises
 * [RailRadarException] with [RailRadarErrorKind.MALFORMED], but a single unusable station row is
 * skipped. A route of forty stations should not be lost to one of them.
 */
internal object RailRadarParser {

    private val responseDateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US)

    /** First `H:mm` anywhere in a string: reads a bare time and an ISO datetime the same way. */
    private val timePattern = Regex("""(\d{1,2}):(\d{2})""")

    /** First integer in a string, so `"734"`, `"734.5"` and `"734 km"` all read as 734. */
    private val integerPattern = Regex("""-?\d+""")

    /** Values that mean "there is nothing here". `NULL` because `org.json` stringifies null. */
    private val absentValues = setOf("", "-", "--", "---", "N/A", "NA", "NULL")

    /** Phrases RailRadar returns with `success: false` when the thing asked about isn't there. */
    private val notFoundPhrases = listOf(
        "not found", "no record", "not available", "invalid", "no train", "no data", "does not exist"
    )

    /** Per-stop `status` strings that mean the train has already left this station. */
    private val departedStatuses = setOf("DEPARTED", "LEFT", "CROSSED", "PASSED")

    // ── Live status ──────────────────────────────────────────────────────────────────────

    /**
     * Parses a `GET /v1/trains/{number}/live` response into a snapshot.
     *
     * @param fetchedAt passed in rather than read from the clock, so the snapshot's age is the
     *   caller's fact and this stays testable.
     * @param requestedDate used when `data.startDate` is missing or unparseable. The date the
     *   run was expected to begin is a better answer than today.
     */
    fun parseLiveStatus(
        json: String,
        trainId: Long,
        fetchedAt: LocalDateTime,
        requestedDate: LocalDate
    ): TrainRunStatus {
        val data = checkEnvelope(readDocument(json))

        val route = data.optJSONArray("route")
            ?: throw RailRadarException(RailRadarErrorKind.MALFORMED, "Live response carried no route array")

        val currentLocation = data.optJSONObject("currentLocation")
        val currentSerial = currentLocation?.readInt("sequence")
        val currentCode = currentLocation?.readString("stationCode") ?: ""

        val stops = readRunStops(route, currentSerial, currentCode)
        if (stops.isEmpty()) {
            throw RailRadarException(RailRadarErrorKind.MALFORMED, "Route array held no usable stations")
        }

        // RailRadar states each stop's day explicitly (arrivalDay/departureDay); trust it and only
        // derive from midnight rollovers when a response omits the field.
        val sorted = stops.sortedBy { it.serialNo }
        val dayed = if (routeHasExplicitDays(route)) sorted else assignRunDayOffsets(sorted)
        val ordered = markCompletion(trimRunEndpoints(dayed))
        val flagged = ordered.firstOrNull { it.isCurrent }
        val lastDeparted = ordered.filter { it.isDeparted }.maxByOrNull { it.serialNo }

        // RailRadar reports the whole-train delay at the top level; trust it over a per-stop
        // reading, and fall back to the route only when it is absent.
        val delay = data.readInt("delayMinutes") ?: TrainProgress.currentDelayMinutes(ordered)

        val nextHalt = data.optJSONObject("nextHalt")
        val nextHaltCode = nextHalt?.readString("stationCode") ?: ""
        val nextHaltName = nextHalt?.readString("stationName") ?: ""
        val routeNext = TrainProgress.nextStop(ordered)
        // The route stop the ETA is computed from — matched to nextHalt by code when possible.
        val etaStop = ordered.firstOrNull { it.stationCode.equals(nextHaltCode, ignoreCase = true) }
            ?: routeNext

        return TrainRunStatus(
            trainId = trainId,
            fetchedAt = fetchedAt,
            runDate = parseDate(data.readString("startDate")) ?: requestedDate,
            source = TrainRunSource.LIVE,
            currentStationCode = flagged?.stationCode
                ?: currentCode.ifBlank { lastDeparted?.stationCode ?: "" },
            currentStationName = flagged?.stationName ?: lastDeparted?.stationName ?: "",
            delayMinutes = delay,
            lastDepartedSerial = TrainProgress.lastDepartedSerial(ordered),
            progressFraction = TrainProgress.progressFraction(ordered),
            nextStopCode = nextHaltCode.ifBlank { routeNext?.stationCode ?: "" },
            nextStopName = nextHaltName.ifBlank { routeNext?.stationName ?: "" },
            nextStopEta = etaStop?.let { TrainProgress.etaFor(it, delay) },
            averageSpeedKmph = TrainProgress.averageSpeedKmph(ordered),
            message = readMessage(data),
            stops = ordered
        )
    }

    // ── Schedule ─────────────────────────────────────────────────────────────────────────

    /**
     * Parses a `GET /v1/trains/{number}` timetable response.
     *
     * The returned stops carry no `trainId` — that is the repository's to assign — and no delay
     * information, because a timetable does not have any. Day offsets come from each row's
     * `arrivalDay`/`departureDay`, falling back to deriving them from midnight rollovers across the
     * scheduled times when a response omits the field.
     */
    fun parseSchedule(json: String): List<TrainStop> {
        val data = checkEnvelope(readDocument(json))

        val route = data.optJSONArray("route")
            ?: data.optJSONObject("train")?.optJSONArray("route")
            ?: throw RailRadarException(RailRadarErrorKind.MALFORMED, "Schedule response carried no route array")

        val stops = readScheduleStops(route)
        if (stops.isEmpty()) {
            throw RailRadarException(RailRadarErrorKind.MALFORMED, "Route array held no usable stations")
        }
        val sortedTrimmed = trimEndpoints(stops.sortedBy { it.serialNo })
        return if (routeHasExplicitDays(route)) sortedTrimmed else assignDayOffsets(sortedTrimmed)
    }

    // ── PNR ──────────────────────────────────────────────────────────────────────────────

    /**
     * Parses a `GET /v1/pnr/{pnr}` response into a [ParsedTicket].
     *
     * Reuses the e-ticket import's type so the result lands in the same editable form. Two things
     * a PNR cannot give are deliberately left blank rather than invented:
     *
     * - **Times.** A PNR carries a journey *date* but no departure or arrival clock time, so both
     *   stay null and the editor keeps whatever the form already has.
     * - **Names, ages, genders.** Indian Railways never exposes these over a PNR for privacy, so
     *   passenger rows come across with their coach, berth and status filled and their identity
     *   empty — for the traveller to complete. Surfaced to the caller, not hidden.
     */
    fun parsePnr(json: String): ParsedTicket {
        val data = checkEnvelope(readDocument(json))

        val train = data.optJSONObject("train")
        val journey = data.optJSONObject("journey")
        val source = train?.optJSONObject("source")
        val destination = train?.optJSONObject("destination")
        val boarding = train?.optJSONObject("boardingPoint")

        val passengers = readPnrPassengers(data.optJSONArray("passengers"))

        return ParsedTicket(
            pnr = data.readString("pnrNumber"),
            trainNumber = train?.readString("number") ?: "",
            trainName = train?.readString("name") ?: "",
            travelClass = journey?.readString("class") ?: "",
            quota = journey?.readString("quota") ?: "",
            originCode = source?.readString("code") ?: "",
            originName = source?.readString("name") ?: "",
            destinationCode = destination?.readString("code") ?: "",
            destinationName = destination?.readString("name") ?: "",
            boardingCode = boarding?.readString("code") ?: "",
            boardingName = boarding?.readString("name") ?: "",
            // A PNR carries no clock times; the editor keeps the form's own dates.
            departure = null,
            arrival = null,
            passengers = passengers,
            warnings = if (passengers.isEmpty()) listOf(TicketImportWarning.NO_PASSENGERS_FOUND) else emptyList()
        )
    }

    // ── Value parsing ────────────────────────────────────────────────────────────────────

    /**
     * `"16:27"` / `"2026-08-24T16:27:00"` / `"-"` → a time or null.
     *
     * Null for anything unparseable rather than an exception: a single missing arrival time is a
     * normal thing on a route, and it should cost that one field, not the whole request.
     */
    fun parseTime(raw: String?): LocalTime? {
        val value = raw?.trim() ?: return null
        if (value.uppercase(Locale.US) in absentValues) return null

        val match = timePattern.find(value) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        if (hour > 23 || minute > 59) return null
        return LocalTime.of(hour, minute)
    }

    /** ISO (`2026-08-24`, or the date half of a datetime) or `dd-MM-yyyy` → a date, or null. */
    fun parseDate(raw: String?): LocalDate? {
        val value = raw?.trim() ?: return null
        if (value.uppercase(Locale.US) in absentValues) return null
        // The date half of an ISO datetime parses on its own; a bare ISO date is unchanged.
        try {
            return LocalDate.parse(value.take(10))
        } catch (_: DateTimeParseException) {
        }
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

            val code = row.readStation("code", "stationCode")
            val name = row.readStation("name", "stationName")
            if (code.isEmpty() && name.isEmpty()) continue

            val serial = row.readInt("sequence") ?: (index + 1)
            val isCurrent = when {
                currentSerial != null -> serial == currentSerial
                currentCode.isNotEmpty() -> code.equals(currentCode, ignoreCase = true)
                else -> false
            }
            val statusText = row.readString("status").uppercase(Locale.US)
            val isDeparted = row.readString("actualDeparture").let { parseTime(it) != null } ||
                (currentSerial != null && serial < currentSerial) ||
                statusText in departedStatuses

            stops += TrainStopStatus(
                serialNo = serial,
                stationCode = code,
                stationName = name.ifEmpty { code },
                scheduledArrival = parseTime(row.readString("scheduledArrival", "arrival")),
                actualArrival = parseTime(row.readString("actualArrival")),
                scheduledDeparture = parseTime(row.readString("scheduledDeparture", "departure")),
                actualDeparture = parseTime(row.readString("actualDeparture")),
                arrivalDelayMinutes = row.readInt("delayArrival"),
                departureDelayMinutes = row.readInt("delayDeparture"),
                distanceKm = row.readInt("distance") ?: 0,
                // arrivalDay/departureDay are 1-based; a run that omits them falls to assignRunDayOffsets.
                dayOffset = row.readInt("arrivalDay", "departureDay")?.minus(1) ?: 0,
                isDeparted = isDeparted,
                isCurrent = isCurrent
            )
        }
        return stops
    }

    private fun readScheduleStops(route: JSONArray): List<TrainStop> {
        val stops = mutableListOf<TrainStop>()
        for (index in 0 until route.length()) {
            val row = route.optJSONObject(index) ?: continue

            val code = row.readStation("code", "stationCode")
            val name = row.readStation("name", "stationName")
            if (code.isEmpty() && name.isEmpty()) continue

            stops += TrainStop(
                serialNo = row.readInt("sequence") ?: (index + 1),
                stationCode = code,
                stationName = name.ifEmpty { code },
                scheduledArrival = parseTime(row.readString("arrival", "scheduledArrival")),
                scheduledDeparture = parseTime(row.readString("departure", "scheduledDeparture")),
                distanceKm = row.readInt("distance") ?: 0,
                // arrivalDay/departureDay are 1-based; a schedule that omits them falls to assignDayOffsets.
                dayOffset = row.readInt("arrivalDay", "departureDay")?.minus(1) ?: 0
            )
        }
        return stops
    }

    private fun readPnrPassengers(passengers: JSONArray?): List<TrainPassenger> {
        if (passengers == null) return emptyList()
        val result = mutableListOf<TrainPassenger>()
        for (index in 0 until passengers.length()) {
            val row = passengers.optJSONObject(index) ?: continue

            val bookingText = row.readString("bookingStatus")
            val currentText = row.readString("currentStatus")
            val status = passengerStatus(row, currentText)

            result += TrainPassenger(
                serialNo = row.readInt("passengerNumber") ?: (index + 1),
                // A PNR never carries names, ages or genders — left for the traveller to fill.
                name = "",
                age = null,
                gender = PassengerGender.UNSPECIFIED,
                allotment = passengerAllotment(
                    status = status,
                    coach = row.readString("coach"),
                    berthNumber = row.readInt("berthNumber"),
                    berthCode = row.readString("berthCode"),
                    currentText = currentText
                ),
                bookingStatusText = bookingText,
                currentStatusText = currentText
            )
        }
        return result
    }

    /** The passenger's current state, from RailRadar's boolean flags first, its text as a backstop. */
    private fun passengerStatus(row: JSONObject, currentText: String): TrainBookingStatus = when {
        row.readBoolean("isCancelled") -> TrainBookingStatus.CANCELLED
        row.readBoolean("isConfirmed") -> TrainBookingStatus.CONFIRMED
        row.readBoolean("isRAC") -> TrainBookingStatus.RAC
        row.readBoolean("isWaitlisted") -> TrainBookingStatus.WAITLISTED
        currentText.isNotEmpty() -> TrainBookingStatus.fromAllotmentCode(
            currentText.substringBefore('/').substringBefore(' ')
        )
        else -> TrainBookingStatus.NOT_BOOKED
    }

    /**
     * Builds a seat allotment from RailRadar's structured coach/berth for a confirmed passenger,
     * and from the parsed status string for a queued one — where the number is a place in line,
     * not a berth, and [TrainAllotment.parse] already knows not to render it as "Berth 24".
     */
    private fun passengerAllotment(
        status: TrainBookingStatus,
        coach: String,
        berthNumber: Int?,
        berthCode: String,
        currentText: String
    ): TrainAllotment = when (status) {
        TrainBookingStatus.CANCELLED -> TrainAllotment(status = status)
        TrainBookingStatus.WAITLISTED, TrainBookingStatus.RAC -> {
            val parsed = TrainAllotment.parse(currentText)
            if (parsed.queuePosition != null) {
                TrainAllotment(
                    status = status,
                    queuePosition = parsed.queuePosition,
                    queueKind = parsed.queueKind.ifBlank { status.allotmentCode }
                )
            } else {
                TrainAllotment(status = status)
            }
        }
        else -> TrainAllotment(
            status = status,
            coach = coach,
            berth = berthNumber?.takeIf { it > 0 }?.toString() ?: "",
            berthType = BerthType.parse(berthCode)
        )
    }

    /** Whatever RailRadar said about the run in words: an exception note, else the status. */
    private fun readMessage(data: JSONObject): String {
        val exceptions = data.optJSONArray("exceptions")
        if (exceptions != null) {
            for (index in 0 until exceptions.length()) {
                val note = exceptions.optJSONObject(index)?.readString("message", "description") ?: ""
                if (note.isNotEmpty()) return note
            }
        }
        return data.readString("status")
    }

    // ── Day-offset derivation ──────────────────────────────────────────────────────────────

    /**
     * Drops the arrival time at the origin and the departure time at the terminus.
     *
     * A train does not arrive at the station it starts from, and does not leave the one it
     * terminates at; carrying those values would put a phantom arrival on the first route row.
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

    /** [trimEndpoints] for the run's stops — scheduled halves only; actuals are left untouched. */
    private fun trimRunEndpoints(stops: List<TrainStopStatus>): List<TrainStopStatus> {
        if (stops.size <= 1) return stops
        return stops.mapIndexed { index, stop ->
            when (index) {
                0 -> stop.copy(scheduledArrival = null)
                stops.lastIndex -> stop.copy(scheduledDeparture = null)
                else -> stop
            }
        }
    }

    /**
     * Marks the terminus departed once the train has actually reached it.
     *
     * Every other stop reads as departed from its `actualDeparture`, but the terminus has none — a
     * train does not leave the station it terminates at. Without this a finished run would keep its
     * final stop listed as "next", and [TrainProgress] would never see the whole route as behind
     * the train. A terminus with a reported arrival is a completed run, so it reads as one.
     */
    private fun markCompletion(stops: List<TrainStopStatus>): List<TrainStopStatus> {
        val last = stops.lastOrNull() ?: return stops
        if (!last.isDeparted && last.actualArrival != null) {
            return stops.dropLast(1) + last.copy(isDeparted = true)
        }
        return stops
    }

    /**
     * Works out which day of the run each stop falls on, from the scheduled times alone.
     *
     * Every time the clock runs backwards along the route, another day has passed. Also handles a
     * halt that straddles midnight — arrive 23:58, leave 00:03 — by rolling over within the stop.
     */
    private fun assignDayOffsets(stops: List<TrainStop>): List<TrainStop> {
        val offsets = dayOffsets(stops.map { it.scheduledArrival to it.scheduledDeparture })
        return stops.mapIndexed { index, stop -> stop.copy(dayOffset = offsets[index]) }
    }

    private fun assignRunDayOffsets(stops: List<TrainStopStatus>): List<TrainStopStatus> {
        val offsets = dayOffsets(stops.map { it.scheduledArrival to it.scheduledDeparture })
        return stops.mapIndexed { index, stop -> stop.copy(dayOffset = offsets[index]) }
    }

    /** The shared rollover arithmetic, over (arrival, departure) pairs in route order. */
    private fun dayOffsets(times: List<Pair<LocalTime?, LocalTime?>>): List<Int> {
        var day = 0
        var previous: LocalTime? = null
        return times.map { (arrival, departure) ->
            val last = previous
            val entry = arrival ?: departure
            if (entry != null && last != null && entry < last) day++
            val here = day
            if (arrival != null && departure != null && departure < arrival) day++
            previous = departure ?: arrival ?: last
            here
        }
    }

    /**
     * True when the route states each stop's day (`arrivalDay`/`departureDay`) itself.
     *
     * When it does, that 1-based field is authoritative and rollover is not derived — a schedule of
     * bare `HH:mm` times cannot always be told apart from an overnight one otherwise.
     */
    private fun routeHasExplicitDays(route: JSONArray): Boolean {
        for (index in 0 until route.length()) {
            val row = route.optJSONObject(index) ?: continue
            if (row.has("arrivalDay") || row.has("departureDay")) return true
        }
        return false
    }

    // ── Document and envelope handling ──────────────────────────────────────────────────────

    private fun readDocument(json: String): JSONObject =
        try {
            JSONObject(json)
        } catch (e: Exception) {
            throw RailRadarException(RailRadarErrorKind.MALFORMED, "Response was not a JSON object", e)
        }

    /**
     * Unwraps the `{ success, data, meta }` envelope, rejecting a failure before anything is read.
     *
     * A `success: false` is classified by its message so the caller can tell "no such PNR" from
     * "key rejected" from "slow down". A 200 with no `data` is malformed — RailRadar always
     * carries the payload there.
     */
    private fun checkEnvelope(root: JSONObject): JSONObject {
        if (root.has("success") && !root.optBoolean("success", false)) {
            // A structured failure sits under `error:{code,message}` (e.g. a flushed PNR); a simpler
            // one carries a flat top-level message. Read the nested object first, then fall back.
            val nested = root.optJSONObject("error")?.readString("message", "code") ?: ""
            val message = nested.ifEmpty { root.readString("message", "error", "errorMessage") }
            val lowered = message.lowercase(Locale.US)
            val kind = when {
                notFoundPhrases.any { lowered.contains(it) } -> RailRadarErrorKind.NOT_FOUND
                lowered.contains("limit") || lowered.contains("quota") -> RailRadarErrorKind.RATE_LIMITED
                lowered.contains("key") || lowered.contains("unauthor") || lowered.contains("subscri") ->
                    RailRadarErrorKind.KEY_REJECTED
                else -> RailRadarErrorKind.PROVIDER_ERROR
            }
            throw RailRadarException(kind, message.ifEmpty { "RailRadar reported failure" })
        }
        return root.optJSONObject("data")
            ?: throw RailRadarException(RailRadarErrorKind.MALFORMED, "Response carried no data object")
    }

    // ── Tolerant field readers ───────────────────────────────────────────────────────────

    /** First non-empty value among [keys]. */
    private fun JSONObject.readString(vararg keys: String): String {
        for (key in keys) {
            if (!has(key) || isNull(key)) continue
            val value = optString(key, "").trim()
            if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) return value
        }
        return ""
    }

    /**
     * A station's code or name wherever the row keeps it.
     *
     * The schedule endpoint nests it under `station:{code,name}`; the live route carries it flat as
     * `stationCode`/`stationName`. Nested is tried first, then the flat [flatKeys].
     */
    private fun JSONObject.readStation(nestedKey: String, vararg flatKeys: String): String {
        optJSONObject("station")?.let { station ->
            val nested = station.readString(nestedKey)
            if (nested.isNotEmpty()) return nested
        }
        return readString(*flatKeys)
    }

    /** First readable integer among [keys], real number or quoted, or null. */
    private fun JSONObject.readInt(vararg keys: String): Int? {
        for (key in keys) {
            val raw = readString(key)
            if (raw.isEmpty()) continue
            val digits = integerPattern.find(raw)?.value ?: continue
            digits.toIntOrNull()?.let { return it }
        }
        return null
    }

    /** `true`, `"true"`, `"1"`, `"Y"`, `"Yes"` — RailRadar uses JSON booleans, the rest is defence. */
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
