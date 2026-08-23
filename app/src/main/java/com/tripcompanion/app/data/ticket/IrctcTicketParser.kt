package com.tripcompanion.app.data.ticket

import com.tripcompanion.app.data.pdf.PdfText
import com.tripcompanion.app.data.pdf.PdfTextLine
import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.ParsedTicket
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.TicketImportWarning
import com.tripcompanion.app.domain.model.TrainAllotment
import com.tripcompanion.app.domain.model.TrainFare
import com.tripcompanion.app.domain.model.TrainPassenger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Reads an IRCTC Electronic Reservation Slip into a [ParsedTicket].
 *
 * Pure: takes extracted text, returns a reading, touches nothing else. That is what lets it be
 * tested against real ticket bytes with no Android around it.
 *
 * ## How it finds things
 *
 * The slip is a table, and a table in a PDF is not rows — it is glyphs at coordinates. Two
 * facts about these documents make it tractable:
 *
 * 1. **The panel and the boilerplate are separate content streams.** A page of terms and
 *    conditions runs down the same band of the page as the passenger table, so grouping by y
 *    alone splices "unauthorized agents" into the middle of a passenger's name. Taking the one
 *    stream that holds both `PNR` and `Booking Status` discards the boilerplate exactly, and
 *    [PdfText.streamContainingAll] returns null rather than guessing if two streams claim it.
 * 2. **Headers and values are separate lines that share column positions.** So a value is
 *    found as "the run on the row below, nearest the header's x" — see
 *    [PdfTextLine.runNear]. That survives an empty cell, which pairing by index does not: a
 *    passenger with no age recorded would otherwise take the next column's value and be
 *    recorded as female, aged Female.
 *
 * ## What varies between layouts
 *
 * Both live templates are handled, and none of the differences are switched on a station, a
 * train or an agent — every one of them is a shape, per §4:
 *
 * | | older slip | newer slip |
 * |---|---|---|
 * | station cell | `UDAIPUR CITY` | `KAZIPET JN (KZJ)` |
 * | distance | `610` | `1348 kms` |
 * | quota | `GENERAL` | `General` |
 * | arrival cell | `Arrival* Mathura Jn` — a **name** | `Arrival* 04:25 6 Sep 2026` |
 * | booking date | `02-Aug-2026 12:49:25` | `2-Aug-2026 09:13:26` |
 * | current status | `CNF/S4/8` | `CNF/S3/56/NA` |
 * | fare cell | `790.00` | `. 0.9` — a stray period |
 * | agent line | `MakeMyTrip ID: …` | `Goibibo ID: …` |
 *
 * The arrival one is the reason [ParsedTicket.arrival] is nullable. A slip that prints the
 * destination's *name* where the arrival time belongs is not a slip this can guess at, so it
 * comes back null with [TicketImportWarning.ARRIVAL_TIME_MISSING] and the editor asks.
 */
object IrctcTicketParser {

    /**
     * The two labels that identify the reservation panel among the page's streams.
     *
     * Both, not either: `PNR` alone appears in the terms and conditions ("PNRs having fully
     * waitlisted status…"), so it would match the boilerplate stream too and the selection
     * would come back ambiguous.
     */
    private val PANEL_MARKERS = arrayOf("PNR", "Booking Status")

    /** How far a value may sit from its header's x and still belong to that column. */
    private const val COLUMN_TOLERANCE = 40f

    fun parse(text: PdfText): ParsedTicket {
        val panel = text.streamContainingAll(*PANEL_MARKERS) ?: return ParsedTicket()
        val lines = panel.lines()
        val warnings = mutableSetOf<TicketImportWarning>()

        val stations = readStations(lines, warnings)
        val times = readTimes(lines, warnings)
        val identity = readLabelledRow(lines, "PNR", "Train No./Name", "Class")
        val booking = readLabelledRow(lines, "Quota", "Distance", "Booking Date")
        val trainCell = identity.getOrNull(1).orEmpty()

        val passengers = readPassengers(lines)
        if (passengers.isEmpty()) warnings += TicketImportWarning.NO_PASSENGERS_FOUND

        val fare = readFare(lines)
        if (!fare.isEmpty && fare.totalFare == null) warnings += TicketImportWarning.FARE_INCOMPLETE

        val agent = readAgent(lines)

        return ParsedTicket(
            pnr = identity.getOrNull(0).orEmpty().filter { it.isDigit() },
            trainNumber = trainCell.substringBefore('/').trim(),
            // Left exactly as the railway prints it. "NZM TVC SF EXP" is a designation, not a
            // sentence, and title-casing it produces "Nzm Tvc Sf Exp" — which is not how anyone
            // refers to that train, on a platform board or anywhere else.
            trainName = trainCell.substringAfter('/', "").trim(),
            travelClass = readTravelClass(identity.getOrNull(2)),
            quota = titleCase(booking.getOrNull(0).orEmpty()),
            distanceKm = readDistance(booking.getOrNull(1)),
            originCode = stations.origin.code,
            originName = stations.origin.name,
            destinationCode = stations.destination.code,
            destinationName = stations.destination.name,
            boardingCode = stations.boarding.code,
            boardingName = stations.boarding.name,
            departure = times.departure,
            arrival = times.arrival,
            bookedAt = readBookedAt(booking.getOrNull(2)),
            agentName = agent?.first.orEmpty(),
            agentBookingId = agent?.second.orEmpty(),
            transactionId = readAfterLabel(lines, "Transaction ID:").orEmpty(),
            fare = fare,
            passengers = passengers,
            warnings = warnings.toList()
        )
    }

    // ── Stations ────────────────────────────────────────────────────────────────

    private data class Station(val code: String, val name: String)

    private data class Stations(
        val origin: Station,
        val boarding: Station,
        val destination: Station
    )

    /**
     * Booked from, boarding point, and destination — read by position, not by header.
     *
     * The header row above says only `Booked From` and `To`, two labels for three values: the
     * boarding point is printed between them with no label of its own. So the columns cannot
     * come from header anchors here, and the row is read left to right instead. That ordering is
     * the one thing about this row that holds on every slip: a boarding point is by definition
     * a station between where the ticket was bought from and where it ends.
     *
     * Two values rather than three means the slip did not print a separate boarding point,
     * which means it is the station the ticket was booked from.
     */
    private fun readStations(
        lines: List<PdfTextLine>,
        warnings: MutableSet<TicketImportWarning>
    ): Stations {
        val blank = Station("", "")
        val header = lines.indexOfFirst { it.contains("Booked From") }
        val cells = if (header == -1) {
            emptyList()
        } else {
            lines.getOrNull(header + 1)?.runs?.map { it.text.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()
        }

        val parsed = cells.map(::readStationCell)
        val stations = when (parsed.size) {
            0 -> Stations(blank, blank, blank)
            1 -> Stations(parsed[0], parsed[0], blank)
            2 -> Stations(parsed[0], parsed[0], parsed[1])
            else -> Stations(parsed.first(), parsed[1], parsed.last())
        }

        // The older slip prints station names with no codes at all. Worth saying so: the code is
        // what a timetable lookup needs, and the user is the only one who can supply it.
        if (stations.origin.name.isNotBlank() && stations.origin.code.isBlank()) {
            warnings += TicketImportWarning.STATION_CODES_MISSING
        }
        return stations
    }

    /** `"KAZIPET JN (KZJ)"` → code `KZJ`, name `Kazipet Jn`. `"UDAIPUR CITY"` → no code. */
    private fun readStationCell(cell: String): Station {
        val match = Regex("^(.*?)\\s*\\(([A-Z]{2,6})\\)$").find(cell.trim())
        return if (match == null) {
            Station("", titleCase(cell))
        } else {
            Station(match.groupValues[2], titleCase(match.groupValues[1]))
        }
    }

    // ── Times ───────────────────────────────────────────────────────────────────

    private data class Times(val departure: LocalDateTime?, val arrival: LocalDateTime?)

    /**
     * The `Start Date* … Departure* … Arrival* …` row.
     *
     * Each cell carries its own label, so they are matched by name and their order on the page
     * does not matter. The start date is the fallback for a cell that gives a time and no date,
     * which is a shape the newer slip does not use but the older one is one revision away from.
     */
    private fun readTimes(
        lines: List<PdfTextLine>,
        warnings: MutableSet<TicketImportWarning>
    ): Times {
        val line = lines.firstOrNull { it.contains("Departure*") || it.contains("Start Date*") }
        val startDate = readDate(cellAfter(line, "Start Date*"))
        val departure = readDateTime(cellAfter(line, "Departure*"), startDate)
        val arrival = readDateTime(cellAfter(line, "Arrival*"), startDate ?: departure?.toLocalDate())

        if (departure == null) warnings += TicketImportWarning.DEPARTURE_TIME_MISSING
        if (arrival == null) warnings += TicketImportWarning.ARRIVAL_TIME_MISSING
        return Times(departure, arrival)
    }

    /** The text of the cell beginning with [label], with the label itself removed. */
    private fun cellAfter(line: PdfTextLine?, label: String): String =
        line?.runStartingWith(label)?.text?.trim()?.removePrefix(label)?.trim().orEmpty()

    // ── The two three-column boxes ──────────────────────────────────────────────

    /**
     * A header row of [headers] and the values on the row below it, in header order.
     *
     * Anchored on x rather than paired by index, so a slip that leaves the middle cell empty
     * returns an empty middle value instead of shifting the last one left. The header is found
     * by its first label; matching on all three would break on the punctuation that varies
     * (`Passenger Details` against `Passenger Details:`).
     */
    private fun readLabelledRow(
        lines: List<PdfTextLine>,
        vararg headers: String
    ): List<String> {
        val index = lines.indexOfFirst { line ->
            line.runs.any { it.text.trim().equals(headers.first(), ignoreCase = true) }
        }
        if (index == -1) return emptyList()
        val header = lines[index]
        val values = lines.getOrNull(index + 1) ?: return emptyList()

        return headers.map { label ->
            val anchor = header.runs.firstOrNull {
                it.text.trim().equals(label, ignoreCase = true)
            } ?: return@map ""
            values.runNear(anchor.x, COLUMN_TOLERANCE)?.text?.trim().orEmpty()
        }
    }

    // ── The passenger table ─────────────────────────────────────────────────────

    /**
     * Everything between the `# Name Age Gender …` header and whatever ends the table.
     *
     * A row is claimed only if its first cell is `1.`, `2.` — the serial number the slip prints.
     * That is what stops the acronym legend and the agent's booking id, which sit directly
     * underneath with no blank line between, from being read as passengers three and four.
     *
     * Both status columns are kept verbatim as well as parsed, because the pair carries what the
     * parse cannot: someone booked on the waitlist and since confirmed wants to see both, and a
     * string this parser failed to understand is still worth showing to the person holding the
     * ticket.
     */
    private fun readPassengers(lines: List<PdfTextLine>): List<TrainPassenger> {
        val headerIndex = lines.indexOfFirst { line ->
            line.contains("Booking Status") && line.contains("Current Status")
        }
        if (headerIndex == -1) return emptyList()
        val header = lines[headerIndex]

        fun anchor(label: String): Float? = header.runs
            .firstOrNull { it.text.trim().equals(label, ignoreCase = true) }?.x

        val nameX = anchor("Name")
        val ageX = anchor("Age")
        val genderX = anchor("Gender")
        val bookedX = anchor("Booking Status")
        val currentX = anchor("Current Status")

        val passengers = mutableListOf<TrainPassenger>()
        for (line in lines.drop(headerIndex + 1)) {
            val serial = line.runs.firstOrNull()?.text?.trim()
                ?.removeSuffix(".")?.toIntOrNull()
                ?: break

            fun cell(x: Float?): String =
                x?.let { line.runNear(it, COLUMN_TOLERANCE)?.text?.trim() }.orEmpty()

            // A serial number is small and a passenger has a name. Both, because the fare block
            // further down the panel is also "a number then a value", and `790.00` trimmed of a
            // trailing period is not an integer but `790` on some other layout would be.
            val name = titleCase(cell(nameX))
            if (serial !in 1..99 || name.isBlank()) break

            val bookedText = cell(bookedX)
            val currentText = cell(currentX)

            // The chart is the newer fact, so it wins where it says anything. Where it prints
            // `NA` for the berth type the booking's `SU` is still true, so the two are merged
            // rather than one replacing the other outright.
            val booked = TrainAllotment.parse(bookedText)
            val current = TrainAllotment.parse(currentText)
            val allotment = when {
                currentText.isBlank() -> booked
                current.berthType == BerthType.UNKNOWN && booked.berthType != BerthType.UNKNOWN &&
                    current.coach == booked.coach && current.berth == booked.berth ->
                    current.copy(berthType = booked.berthType)
                else -> current
            }

            passengers += TrainPassenger(
                serialNo = serial,
                name = name,
                age = cell(ageX).filter { it.isDigit() }.toIntOrNull(),
                gender = PassengerGender.parse(cell(genderX)),
                allotment = allotment,
                bookingStatusText = bookedText,
                currentStatusText = currentText
            )
        }
        return passengers
    }

    // ── Payment ─────────────────────────────────────────────────────────────────

    /**
     * The fare block, one labelled line each.
     *
     * The amount is the last run on the line rather than a column anchor, because the two
     * layouts put the figures 150 points apart and there is no header row above them to anchor
     * against. Nothing is ever added up here: a total this app computed would disagree with the
     * printed one the moment a charge failed to read, and the printed one is the receipt.
     */
    private fun readFare(lines: List<PdfTextLine>): TrainFare = TrainFare(
        ticketFare = amountFor(lines, "Ticket Fare"),
        convenienceFee = amountFor(lines, "IRCTC Convenience Fee"),
        insurancePremium = amountFor(lines, "Travel Insurance Premium"),
        agentServiceCharge = amountFor(lines, "Agent Service Charge"),
        paymentGatewayCharge = amountFor(lines, "PG Charge"),
        totalFare = amountFor(lines, "Total Fare")
    )

    private fun amountFor(lines: List<PdfTextLine>, label: String): Double? {
        val line = lines.firstOrNull { candidate ->
            candidate.runs.firstOrNull()?.text?.trim()?.startsWith(label, ignoreCase = true) == true
        } ?: return null
        return readAmount(line.runs.lastOrNull()?.text)
    }

    /**
     * `"790.00"`, `" 1300.0"`, `". 0.9"` → a number.
     *
     * The leading period is real: one layout draws the insurance premium with a stray `.` in
     * front of it. Keeping only digits and the decimal point would turn that into `.0.9`, so
     * the number is matched out of the cell instead of the cell being cleaned up.
     */
    private fun readAmount(raw: String?): Double? {
        if (raw == null) return null
        return Regex("\\d+(?:\\.\\d+)?").find(raw)?.value?.toDoubleOrNull()
    }

    // ── Booking metadata ────────────────────────────────────────────────────────

    /**
     * `"1348 kms"`, `"610"` → kilometres. Zero when it was not printed or not a number, which
     * [ParsedTicket] treats as "not known" for a distance.
     */
    private fun readDistance(raw: String?): Int =
        raw?.let { Regex("\\d+").find(it)?.value?.toIntOrNull() } ?: 0

    /**
     * The class a berth was booked in, as the app spells it.
     *
     * The slip prints the class as a word — `SLEEPER` — and the editor offers the railway's own
     * codes, so an import that passed the word through landed on a form with no class selected
     * and the word sitting invisibly in the state behind it. This is the same kind of table as
     * [BerthType.parse]: railway-wide reservation codes, nothing to do with any particular
     * train or route.
     *
     * Anything not in the table comes through verbatim rather than blank. A class this table has
     * not heard of is still information, and the user can see it and correct it.
     */
    private fun readTravelClass(raw: String?): String {
        val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return ""
        // Already a code — a two-or-three character cell is not a word.
        if (cleaned.length <= 3 && cleaned.none { it == ' ' }) return cleaned.uppercase()
        return TRAVEL_CLASS_CODES[cleaned.uppercase()] ?: cleaned
    }

    private val TRAVEL_CLASS_CODES = mapOf(
        "FIRST AC" to "1A",
        "AC FIRST CLASS" to "1A",
        "SECOND AC" to "2A",
        "AC 2 TIER" to "2A",
        "THIRD AC" to "3A",
        "AC 3 TIER" to "3A",
        "THIRD AC ECONOMY" to "3E",
        "AC 3 ECONOMY" to "3E",
        "SLEEPER" to "SL",
        "CHAIR CAR" to "CC",
        "AC CHAIR CAR" to "CC",
        "EXECUTIVE CLASS" to "EC",
        "EXECUTIVE CHAIR CAR" to "EC",
        "SECOND SITTING" to "2S",
        "SECOND CLASS" to "2S",
        "FIRST CLASS" to "FC",
        "GENERAL" to "GN",
        "UNRESERVED" to "GN"
    )

    /**
     * The agent that sold the ticket, and its own booking reference.
     *
     * Matched as "a line reading `<something> ID: <reference>`" rather than against a list of
     * agent names — there are dozens of IRCTC agents and hard-coding the two on these files
     * would make every other ticket lose its reference. `Transaction ID` is the one line of
     * that shape that is not an agent, so it is excluded by name.
     */
    private fun readAgent(lines: List<PdfTextLine>): Pair<String, String>? {
        val match = lines.asSequence()
            .mapNotNull { line ->
                Regex("^([A-Za-z][A-Za-z ]{2,24}) ID:\\s*(\\S+)$").find(line.text.trim())
            }
            .firstOrNull { !it.groupValues[1].equals("Transaction", ignoreCase = true) }
            ?: return null
        return match.groupValues[1].trim() to match.groupValues[2].trim()
    }

    private fun readAfterLabel(lines: List<PdfTextLine>, label: String): String? = lines
        .firstOrNull { it.text.trim().startsWith(label, ignoreCase = true) }
        ?.text?.trim()?.removePrefix(label)?.trim()
        ?.takeIf { it.isNotBlank() }

    // ── Dates and times ─────────────────────────────────────────────────────────

    /**
     * `d-MMM-yyyy H:mm:ss`, with the day of the month one digit on one layout and two on the
     * other. `d` accepts both, so this is one pattern rather than two.
     */
    private val BOOKED_AT = DateTimeFormatter.ofPattern("d-MMM-yyyy H:mm:ss", Locale.ENGLISH)

    /** `10 Sep 2026`, as the slip prints a journey date. */
    private val JOURNEY_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    private fun readBookedAt(raw: String?): LocalDateTime? {
        val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDateTime.parse(cleaned, BOOKED_AT)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun readDate(raw: String): LocalDate? {
        val cleaned = raw.trim().takeIf { it.isNotEmpty() } ?: return null
        return try {
            LocalDate.parse(cleaned, JOURNEY_DATE)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * `"18:30 10 Sep 2026"` → that moment. `"Mathura Jn"` → null.
     *
     * Null rather than a guess is the whole point: the cell that should hold the arrival time
     * sometimes holds the destination's name instead, and an import that invented a time from
     * the station name would put a countdown on a screen against a moment nobody chose.
     *
     * A cell that gives a time and no date falls back to [fallbackDate]. That is the journey's
     * start date, so an arrival on a later day would land a day early — which is exactly why the
     * editor's "arrives next day" switch exists and why its `end > start` check has to hold
     * before anything can be saved.
     */
    private fun readDateTime(raw: String, fallbackDate: LocalDate?): LocalDateTime? {
        val cleaned = raw.trim().ifEmpty { return null }
        val time = Regex("^(\\d{1,2}):(\\d{2})").find(cleaned) ?: return null
        val hour = time.groupValues[1].toIntOrNull() ?: return null
        val minute = time.groupValues[2].toIntOrNull() ?: return null
        if (hour > 23 || minute > 59) return null

        val date = readDate(cleaned.removeRange(time.range).trim()) ?: fallbackDate ?: return null
        return LocalDateTime.of(date, LocalTime.of(hour, minute))
    }

    // ── Text ────────────────────────────────────────────────────────────────────

    /**
     * `"UDAIPUR CITY"` → `"Udaipur City"`, leaving anything already mixed-case alone.
     *
     * The slip shouts its station and passenger names. The brief forbids shouting back, and a
     * name is the last place to make an exception — so the shouting is undone, but only where
     * the whole cell is upper case. A cell that already has lower-case letters in it was typed
     * that way by whoever booked, and `"McDonald"` is not improved by this function.
     *
     * Two-letter words are lowered along with the rest, which is what turns `KAZIPET JN` into
     * `Kazipet Jn` — the same spelling the slip itself uses in the one place it prints a station
     * name in mixed case.
     */
    private fun titleCase(raw: String): String {
        val cleaned = raw.trim()
        if (cleaned.isEmpty() || cleaned.any { it.isLowerCase() }) return cleaned
        return cleaned.split(' ').joinToString(" ") { word ->
            if (word.isEmpty()) word else word[0] + word.substring(1).lowercase()
        }
    }
}
