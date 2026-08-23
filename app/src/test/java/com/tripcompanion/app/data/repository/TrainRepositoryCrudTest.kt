package com.tripcompanion.app.data.repository

import com.tripcompanion.app.data.local.fake.InMemoryTripDatabase
import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainAllotment
import com.tripcompanion.app.domain.model.TrainBookingStatus
import com.tripcompanion.app.domain.model.TrainPassenger
import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.model.TrainStopStatus
import com.tripcompanion.app.domain.model.Trip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The train feature's three tables, through the real repository.
 *
 * A train is stored in three pieces — the booking the user typed, the timetable fetched or
 * entered for it, and the last running-status snapshot — and the pieces have different
 * lifetimes. The booking is edited by hand, the timetable is replaced wholesale when a new
 * one arrives, and the snapshot is thrown away and rewritten every couple of minutes. Most
 * of what can go wrong here is one of those writes leaking into another train's rows, or a
 * replace turning into an append.
 *
 * Runs [TrainRepositoryImpl] against [InMemoryTripDatabase], so both legs of every mapper
 * are covered as well: an enum that round-trips through a string column and a nullable
 * speed that must come back null rather than zero.
 */
class TrainRepositoryCrudTest {

    private lateinit var db: InMemoryTripDatabase
    private lateinit var trips: TripRepositoryImpl
    private lateinit var trains: TrainRepositoryImpl

    @Before
    fun setUp() {
        db = InMemoryTripDatabase()
        trips = TripRepositoryImpl(db.tripDao)
        trains = TrainRepositoryImpl(
            db.trainDao, db.trainPassengerDao, db.trainStopDao, db.trainRunStatusDao
        )
    }

    // ---- The booking -------------------------------------------------------

    @Test
    fun `a train can be created, read back, edited and deleted`() = runTest {
        val tripId = seedTrip()
        val id = trains.insertTrain(
            train(
                tripId = tripId,
                number = "12992",
                pnr = "8412345678",
                travelClass = "CC",
                bookingStatus = TrainBookingStatus.CONFIRMED,
                platform = "3"
            ).copy(passengers = listOf(passenger(1, "Someone", "C4", "36")))
        )

        val created = trains.getTrainById(id).first()
        assertNotNull(created)
        assertEquals("12992", created!!.number)
        assertEquals("8412345678", created.pnr)
        // The allotment now comes back off the party, joined in from its own table.
        assertEquals("C4", created.coachSummary)
        assertEquals("36", created.berthSummary)
        assertEquals(1, created.passengers.size)
        // The status is a string column, so this is the enum surviving both legs of the mapper.
        assertEquals(TrainBookingStatus.CONFIRMED, created.bookingStatus)
        assertEquals("3", created.platform)

        trains.updateTrain(
            created.copy(
                passengers = listOf(passenger(1, "Someone", "C5", "12")),
                bookingStatus = TrainBookingStatus.RAC,
                knownDelayMinutes = 25
            )
        )

        val edited = trains.getTrainById(id).first()!!
        assertEquals("C5", edited.coachSummary)
        assertEquals("12", edited.berthSummary)
        assertEquals(TrainBookingStatus.RAC, edited.bookingStatus)
        assertEquals(25, edited.knownDelayMinutes)
        assertEquals(id, edited.id)

        trains.deleteTrain(id)
        assertNull(trains.getTrainById(id).first())
    }

    /** A booking with nothing but the essentials saved is still a valid booking. */
    @Test
    fun `a train saves with no ticket details at all`() = runTest {
        val id = trains.insertTrain(train(tripId = seedTrip()))

        val saved = trains.getTrainByIdOnce(id)!!

        assertEquals("", saved.pnr)
        assertEquals(emptyList<TrainPassenger>(), saved.passengers)
        assertEquals("", saved.coachSummary)
        assertEquals("", saved.platform)
        assertEquals(TrainBookingStatus.NOT_BOOKED, saved.bookingStatus)
        assertEquals(0, saved.knownDelayMinutes)
    }

    @Test
    fun `updating a train refreshes updatedAt but not createdAt`() = runTest {
        val stamped = LocalDateTime.of(2026, 8, 1, 9, 0)
        val id = trains.insertTrain(
            train(tripId = seedTrip()).copy(createdAt = stamped, updatedAt = stamped)
        )

        val beforeEdit = LocalDateTime.now()
        trains.updateTrain(trains.getTrainByIdOnce(id)!!.copy(platform = "5"))

        val after = trains.getTrainByIdOnce(id)!!
        assertEquals(stamped, after.createdAt)
        assertTrue(
            "updatedAt should be stamped at edit time, not carried over",
            !after.updatedAt.isBefore(beforeEdit)
        )
    }

    /**
     * Trains list by when they leave, which is the order a journey happens in.
     *
     * Insertion order is whatever order the user happened to add tickets in, and on a
     * multi-leg trip that is frequently the return leg first.
     */
    @Test
    fun `trains list in departure order regardless of insertion order`() = runTest {
        val tripId = seedTrip()
        trains.insertTrain(train(tripId, number = "return", departure = at(6, 21, 5)))
        trains.insertTrain(train(tripId, number = "outbound", departure = at(3, 22, 40)))
        trains.insertTrain(train(tripId, number = "connection", departure = at(4, 6, 15)))

        assertEquals(
            listOf("outbound", "connection", "return"),
            trains.getTrainsForTrip(tripId).first().map { it.number }
        )
    }

    /** Two legs leaving at the same minute is unusual but not impossible, so the tie is fixed. */
    @Test
    fun `two trains leaving at the same minute break the tie by id`() = runTest {
        val tripId = seedTrip()
        val first = trains.insertTrain(train(tripId, number = "first", departure = at(3, 22, 40)))
        val second = trains.insertTrain(train(tripId, number = "second", departure = at(3, 22, 40)))

        val listed = trains.getTrainsForTrip(tripId).first()

        assertEquals(listOf(first, second), listed.map { it.id })
    }

    @Test
    fun `each trip sees only its own trains`() = runTest {
        val mine = seedTrip()
        val other = seedTrip()
        trains.insertTrain(train(mine, number = "mine"))
        trains.insertTrain(train(other, number = "theirs"))

        assertEquals(listOf("mine"), trains.getTrainsForTrip(mine).first().map { it.number })
        assertEquals(listOf("theirs"), trains.getTrainsForTrip(other).first().map { it.number })
        assertEquals(2, trains.getAllTrains().first().size)
    }

    /**
     * The event link is advisory, so an unlinked event has no train rather than an error.
     *
     * The itinerary asks this question for every JOURNEY row it draws, most of which have no
     * ticket attached yet.
     */
    @Test
    fun `an event with no train linked reports none`() = runTest {
        trains.insertTrain(train(seedTrip()))
        assertNull(trains.getTrainForEvent(99L).first())
    }

    @Test
    fun `an event's linked train is found`() = runTest {
        val tripId = seedTrip()
        trains.insertTrain(train(tripId, number = "unlinked"))
        trains.insertTrain(train(tripId, number = "linked").copy(eventId = 7L))

        assertEquals("linked", trains.getTrainForEvent(7L).first()?.number)
    }

    /** `LIMIT 1` on the query, so a duplicate link resolves rather than throwing. */
    @Test
    fun `two trains on one event resolve to the first`() = runTest {
        val tripId = seedTrip()
        val first = trains.insertTrain(train(tripId, number = "first").copy(eventId = 7L))
        trains.insertTrain(train(tripId, number = "second").copy(eventId = 7L))

        assertEquals(first, trains.getTrainForEvent(7L).first()?.id)
    }

    @Test
    fun `an empty database reports no trains rather than failing`() = runTest {
        assertEquals(emptyList<Train>(), trains.getAllTrains().first())
        assertNull(trains.getTrainById(999).first())
        assertNull(trains.getTrainByIdOnce(999))
    }

    // ---- The timetable ----------------------------------------------------

    @Test
    fun `a train has no schedule until one is stored`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))

        assertFalse(trains.hasSchedule(id))
        assertEquals(emptyList<TrainStop>(), trains.getStops(id).first())

        trains.replaceSchedule(id, schedule())

        assertTrue(trains.hasSchedule(id))
    }

    @Test
    fun `a stored schedule reads back in route order`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.replaceSchedule(id, schedule().reversed())

        val stops = trains.getStops(id).first()

        assertEquals(listOf(1, 2, 3), stops.map { it.serialNo })
        assertEquals(listOf("ORG", "MID", "TRM"), stops.map { it.stationCode })
        assertEquals(stops, trains.getStopsOnce(id))
    }

    /**
     * A schedule from a provider arrives with every id at zero.
     *
     * Carrying those ids through would make all four rows the same row, and the timetable
     * would render as a single station. The repository zeroes them deliberately; this is the
     * assertion that catches it if that ever stops happening.
     */
    @Test
    fun `stops arriving without ids each get their own row`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.replaceSchedule(id, schedule().map { it.copy(id = 0) })

        val stops = trains.getStopsOnce(id)

        assertEquals(3, stops.size)
        assertEquals(3, stops.map { it.id }.distinct().size)
    }

    /** Ids the caller supplied are dropped too, so a re-fetch cannot collide with itself. */
    @Test
    fun `stops arriving with borrowed ids each get their own row`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.replaceSchedule(id, schedule().map { it.copy(id = 99L) })

        assertEquals(3, trains.getStopsOnce(id).size)
    }

    /**
     * A new timetable replaces the old one rather than merging into it.
     *
     * Stations get added and dropped between revisions, and a merge would leave a phantom
     * halt on the route for ever.
     */
    @Test
    fun `a second schedule replaces the first rather than appending`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.replaceSchedule(id, schedule())

        trains.replaceSchedule(
            id,
            listOf(stop(1, "NEW", "New origin", departure = LocalTime.of(23, 15)))
        )

        val stops = trains.getStopsOnce(id)
        assertEquals(1, stops.size)
        assertEquals("NEW", stops.single().stationCode)
    }

    @Test
    fun `replacing a schedule with nothing clears it`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.replaceSchedule(id, schedule())

        trains.replaceSchedule(id, emptyList())

        assertFalse(trains.hasSchedule(id))
        assertEquals(emptyList<TrainStop>(), trains.getStopsOnce(id))
    }

    /**
     * The shape of the route survives storage: no arrival at the origin, no departure at the
     * terminus, and the day offset that makes an overnight run legible.
     */
    @Test
    fun `the ends of a route and its overnight offset survive storage`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.replaceSchedule(id, schedule())

        val stops = trains.getStopsOnce(id)

        assertNull(stops.first().scheduledArrival)
        assertTrue(stops.first().isOrigin)
        assertNull(stops.last().scheduledDeparture)
        assertTrue(stops.last().isTerminus)
        assertEquals(listOf(0, 1, 1), stops.map { it.dayOffset })
        assertEquals(listOf(0, 200, 383), stops.map { it.distanceKm })
    }

    @Test
    fun `one train's schedule is not another's`() = runTest {
        val tripId = seedTrip()
        val mine = trains.insertTrain(train(tripId, number = "mine"))
        val other = trains.insertTrain(train(tripId, number = "other"))

        trains.replaceSchedule(mine, schedule())

        assertTrue(trains.hasSchedule(mine))
        assertFalse(trains.hasSchedule(other))
        assertEquals(emptyList<TrainStop>(), trains.getStopsOnce(other))
    }

    // ---- The running snapshot ---------------------------------------------

    @Test
    fun `a train has no cached status until one is saved`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))

        assertNull(trains.observeRunStatus(id).first())
        assertNull(trains.getRunStatusOnce(id))
    }

    @Test
    fun `a saved snapshot reads back whole, header and stops together`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.saveRunStatus(snapshot(id))

        val saved = trains.getRunStatusOnce(id)!!

        assertEquals(id, saved.trainId)
        // The source is a string column, like the booking status.
        assertEquals(TrainRunSource.LIVE, saved.source)
        assertEquals("MID", saved.currentStationCode)
        assertEquals(14, saved.delayMinutes)
        assertEquals(0.5f, saved.progressFraction, 0.0001f)
        assertEquals("TRM", saved.nextStopCode)
        assertEquals(LocalTime.of(5, 29), saved.nextStopEta)
        assertEquals(72.5, saved.averageSpeedKmph!!, 0.0001)
        assertEquals(2, saved.stops.size)
        assertEquals(listOf(1, 2), saved.stops.map { it.serialNo })
        assertTrue(saved.stops.first().isDeparted)
        assertTrue(saved.stops.last().isCurrent)
        assertEquals(LocalTime.of(23, 12), saved.stops.first().actualDeparture)
    }

    /**
     * A figure the provider could not work out stays absent through storage.
     *
     * Zero would be a different claim entirely — a stationary train rather than an unknown
     * speed — and it is the one the screen would print instead of an em dash.
     */
    @Test
    fun `an unknown speed and an unknown ETA come back unknown`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.saveRunStatus(
            snapshot(id).copy(averageSpeedKmph = null, nextStopEta = null)
        )

        val saved = trains.getRunStatusOnce(id)!!

        assertNull(saved.averageSpeedKmph)
        assertNull(saved.nextStopEta)
    }

    /**
     * A refresh overwrites the last observation rather than adding to it.
     *
     * Two snapshots' stop rows in one table would double the route on screen, and half the
     * times shown would be minutes old.
     */
    @Test
    fun `a refreshed snapshot replaces the previous one`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.saveRunStatus(snapshot(id))

        trains.saveRunStatus(
            snapshot(id).copy(
                fetchedAt = LocalDateTime.of(2026, 11, 4, 2, 30),
                currentStationCode = "TRM",
                delayMinutes = 6,
                progressFraction = 1f,
                stops = listOf(runStop(1, "ORG", departed = true))
            )
        )

        val saved = trains.getRunStatusOnce(id)!!
        assertEquals("TRM", saved.currentStationCode)
        assertEquals(6, saved.delayMinutes)
        assertEquals(1, saved.stops.size)
        assertEquals(1, db.rowCounts().runStatus)
        assertEquals(1, db.rowCounts().runStops)
    }

    @Test
    fun `clearing a snapshot removes its stops as well as its header`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.saveRunStatus(snapshot(id))

        trains.clearRunStatus(id)

        assertNull(trains.getRunStatusOnce(id))
        assertEquals(0, db.rowCounts().runStatus)
        assertEquals(0, db.rowCounts().runStops)
    }

    @Test
    fun `one train's snapshot is not another's`() = runTest {
        val tripId = seedTrip()
        val mine = trains.insertTrain(train(tripId, number = "mine"))
        val other = trains.insertTrain(train(tripId, number = "other"))

        trains.saveRunStatus(snapshot(mine))

        assertNotNull(trains.getRunStatusOnce(mine))
        assertNull(trains.getRunStatusOnce(other))
    }

    @Test
    fun `clearing one train's snapshot leaves another's alone`() = runTest {
        val tripId = seedTrip()
        val mine = trains.insertTrain(train(tripId, number = "mine"))
        val other = trains.insertTrain(train(tripId, number = "other"))
        trains.saveRunStatus(snapshot(mine))
        trains.saveRunStatus(snapshot(other))

        trains.clearRunStatus(mine)

        assertNull(trains.getRunStatusOnce(mine))
        assertNotNull(trains.getRunStatusOnce(other))
        assertEquals(2, trains.getRunStatusOnce(other)!!.stops.size)
    }

    // ---- Cascades ---------------------------------------------------------

    /**
     * Deleting a train takes its timetable and its cache with it.
     *
     * Both are derived data with no meaning on their own, and orphaned rows keyed by a
     * reused id would attach a stale route to whatever train is added next.
     */
    @Test
    fun `deleting a train removes its schedule and its snapshot`() = runTest {
        val id = trains.insertTrain(train(seedTrip()))
        trains.replaceSchedule(id, schedule())
        trains.saveRunStatus(snapshot(id))

        trains.deleteTrain(id)

        val counts = db.rowCounts()
        assertEquals(0, counts.trains)
        assertEquals(0, counts.trainStops)
        assertEquals(0, counts.runStatus)
        assertEquals(0, counts.runStops)
    }

    @Test
    fun `deleting a trip removes its trains and everything beneath them`() = runTest {
        val doomed = seedTrip()
        val kept = seedTrip()

        val doomedTrain = trains.insertTrain(train(doomed, number = "doomed"))
        trains.replaceSchedule(doomedTrain, schedule())
        trains.saveRunStatus(snapshot(doomedTrain))

        val keptTrain = trains.insertTrain(train(kept, number = "kept"))
        trains.replaceSchedule(keptTrain, schedule())
        trains.saveRunStatus(snapshot(keptTrain))

        trips.deleteTrip(doomed)

        assertNull(trains.getTrainByIdOnce(doomedTrain))
        assertNotNull(trains.getTrainByIdOnce(keptTrain))

        val counts = db.rowCounts()
        assertEquals(1, counts.trains)
        assertEquals(3, counts.trainStops)
        assertEquals(1, counts.runStatus)
        assertEquals(2, counts.runStops)
    }

    // ---- Helpers ----------------------------------------------------------

    private suspend fun seedTrip(): Long = trips.insertTrip(
        Trip(
            name = "Trip",
            startDate = LocalDate.of(2026, 11, 3),
            endDate = LocalDate.of(2026, 11, 6)
        )
    )

    private fun at(day: Int, hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(2026, 11, day, hour, minute)

    private fun train(
        tripId: Long,
        number: String = "12992",
        departure: LocalDateTime = at(3, 22, 40),
        pnr: String = "",
        travelClass: String = "",
        bookingStatus: TrainBookingStatus = TrainBookingStatus.NOT_BOOKED,
        platform: String = ""
    ) = Train(
        tripId = tripId,
        number = number,
        name = "A train",
        originCode = "ORG",
        originName = "Origin",
        destinationCode = "TRM",
        destinationName = "Terminus",
        departureTime = departure,
        arrivalTime = departure.plusHours(7),
        travelClass = travelClass,
        pnr = pnr,
        bookingStatus = bookingStatus,
        platform = platform
    )

    private fun passenger(
        serialNo: Int,
        name: String,
        coach: String,
        berth: String,
        status: TrainBookingStatus = TrainBookingStatus.CONFIRMED,
        berthType: BerthType = BerthType.SIDE_UPPER
    ) = TrainPassenger(
        serialNo = serialNo,
        name = name,
        age = 21,
        gender = PassengerGender.FEMALE,
        allotment = TrainAllotment(
            status = status,
            coach = coach,
            berth = berth,
            berthType = berthType
        ),
        // Built from the value object rather than spelled out, so the fixture carries the
        // railway's own abbreviations ("CNF", not "CON") without a second copy of the mapping.
        bookingStatusText = TrainAllotment(status, coach, berth, berthType).text,
        currentStatusText = TrainAllotment(status, coach, berth).text
    )

    /** Origin, one halt, terminus — the shape that exercises both ends and a day rollover. */
    private fun schedule() = listOf(
        stop(1, "ORG", "Origin", departure = LocalTime.of(22, 40), km = 0, day = 0),
        stop(
            2, "MID", "Middle",
            arrival = LocalTime.of(1, 20), departure = LocalTime.of(1, 30), km = 200, day = 1
        ),
        stop(3, "TRM", "Terminus", arrival = LocalTime.of(5, 15), km = 383, day = 1)
    )

    private fun stop(
        serial: Int,
        code: String,
        name: String,
        arrival: LocalTime? = null,
        departure: LocalTime? = null,
        km: Int = 0,
        day: Int = 0
    ) = TrainStop(
        serialNo = serial,
        stationCode = code,
        stationName = name,
        scheduledArrival = arrival,
        scheduledDeparture = departure,
        distanceKm = km,
        dayOffset = day
    )

    private fun snapshot(trainId: Long) = TrainRunStatus(
        trainId = trainId,
        fetchedAt = LocalDateTime.of(2026, 11, 4, 1, 25),
        runDate = LocalDate.of(2026, 11, 3),
        source = TrainRunSource.LIVE,
        currentStationCode = "MID",
        currentStationName = "Middle",
        delayMinutes = 14,
        lastDepartedSerial = 1,
        progressFraction = 0.5f,
        nextStopCode = "TRM",
        nextStopName = "Terminus",
        nextStopEta = LocalTime.of(5, 29),
        averageSpeedKmph = 72.5,
        message = "SUCCESS",
        stops = listOf(
            runStop(1, "ORG", departed = true, actualDeparture = LocalTime.of(23, 12)),
            runStop(2, "MID", current = true, km = 200, day = 1)
        )
    )

    private fun runStop(
        serial: Int,
        code: String,
        departed: Boolean = false,
        current: Boolean = false,
        actualDeparture: LocalTime? = null,
        km: Int = 0,
        day: Int = 0
    ) = TrainStopStatus(
        serialNo = serial,
        stationCode = code,
        stationName = "Station $serial",
        actualDeparture = actualDeparture,
        distanceKm = km,
        dayOffset = day,
        isDeparted = departed,
        isCurrent = current
    )
}
