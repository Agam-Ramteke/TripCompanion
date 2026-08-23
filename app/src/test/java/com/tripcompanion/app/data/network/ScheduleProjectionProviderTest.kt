package com.tripcompanion.app.data.network

import com.tripcompanion.app.core.time.FixedTimeProvider
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.service.TrainStatusError
import com.tripcompanion.app.domain.service.TrainStatusException
import kotlinx.coroutines.test.runTest
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
 * The offline projection: where the timetable says the train should be.
 *
 * This is the provider that runs with no API key and on a train with no signal, so the tests
 * that matter are the ones about honesty. It must place the train correctly against the clock,
 * it must honour a delay the user typed, and it must refuse to produce an actual time or a
 * speed for a train it has never observed.
 *
 * The route is overnight on purpose. A 22:40 departure reaching 05:15 the next morning is the
 * shape that breaks naive date arithmetic, and the app's own train is one.
 */
class ScheduleProjectionProviderTest {

    private val runDate = LocalDate.of(2026, 11, 3)

    /** Departs 22:40 day one, halts 01:20–01:30 day two, terminates 05:15 day two. */
    private val schedule = listOf(
        TrainStop(
            serialNo = 1,
            stationCode = "ORG",
            stationName = "Origin",
            scheduledArrival = null,
            scheduledDeparture = LocalTime.of(22, 40),
            distanceKm = 0,
            dayOffset = 0
        ),
        TrainStop(
            serialNo = 2,
            stationCode = "MID",
            stationName = "Middle",
            scheduledArrival = LocalTime.of(1, 20),
            scheduledDeparture = LocalTime.of(1, 30),
            distanceKm = 200,
            dayOffset = 1
        ),
        TrainStop(
            serialNo = 3,
            stationCode = "TRM",
            stationName = "Terminus",
            scheduledArrival = LocalTime.of(5, 15),
            scheduledDeparture = null,
            distanceKm = 383,
            dayOffset = 1
        )
    )

    // ---- Identity ----------------------------------------------------------

    /** It never claims to be live, because the screens key their wording off this. */
    @Test
    fun `the projection does not describe itself as live`() {
        val provider = providerAt(at(3, 20, 0))

        assertFalse(provider.isLive)
        assertEquals("Timetable projection", provider.providerName)
    }

    /**
     * A timetable is this provider's input, not something it can fetch.
     *
     * Null rather than an error, so the screen offers to enter the route by hand instead of a
     * retry button for a request that will never be made.
     */
    @Test
    fun `it cannot supply a schedule`() = runTest {
        assertNull(providerAt(at(3, 20, 0)).fetchSchedule("12992"))
    }

    @Test
    fun `with no timetable there is nothing to project from`() = runTest {
        try {
            providerAt(at(3, 20, 0)).fetchStatus(train(), emptyList())
            fail("expected NO_SCHEDULE")
        } catch (e: TrainStatusException) {
            assertEquals(TrainStatusError.NO_SCHEDULE, e.error)
        }
    }

    // ---- Placing the train against the clock -------------------------------

    @Test
    fun `before departure nothing has been left behind`() = runTest {
        val status = providerAt(at(3, 20, 0)).fetchStatus(train(), schedule)

        assertEquals(TrainRunSource.PROJECTED, status.source)
        assertEquals(0, status.lastDepartedSerial)
        assertFalse(status.hasStarted)
        assertEquals(0f, status.progressFraction, 0.0001f)
        assertEquals("", status.currentStationCode)
        assertEquals("ORG", status.nextStopCode)
    }

    /**
     * You cannot be late for your own origin.
     *
     * The first stop has no scheduled arrival, so the next-stop ETA is genuinely unknowable
     * before departure and comes back null rather than borrowing the departure time.
     */
    @Test
    fun `the origin has no arrival to be due at`() = runTest {
        val status = providerAt(at(3, 20, 0)).fetchStatus(train(), schedule)

        assertEquals("ORG", status.nextStopCode)
        assertNull(status.nextStopEta)
    }

    @Test
    fun `after departure the origin is behind and the next halt is ahead`() = runTest {
        val status = providerAt(at(3, 22, 45)).fetchStatus(train(), schedule)

        assertEquals(1, status.lastDepartedSerial)
        assertTrue(status.hasStarted)
        assertEquals("ORG", status.currentStationCode)
        assertEquals("MID", status.nextStopCode)
        assertEquals(LocalTime.of(1, 20), status.nextStopEta)
    }

    /**
     * The test that proves `dayOffset` is applied rather than ignored.
     *
     * At 00:30 on the second day, a projection that dropped the day offset would read the
     * halt's 01:20 arrival as *yesterday* morning, mark it departed, and put the train two
     * hundred kilometres further on than it is.
     */
    @Test
    fun `an overnight halt is not treated as having happened yesterday`() = runTest {
        val status = providerAt(at(4, 0, 30)).fetchStatus(train(), schedule)

        assertEquals(1, status.lastDepartedSerial)
        assertFalse(status.stops[1].isDeparted)
        assertEquals("MID", status.nextStopCode)
    }

    @Test
    fun `standing at a halt the train is at that station`() = runTest {
        val status = providerAt(at(4, 1, 25)).fetchStatus(train(), schedule)

        assertTrue(status.stops[1].isCurrent)
        assertFalse("still due out at 01:30", status.stops[1].isDeparted)
        assertEquals("MID", status.currentStationCode)
        assertEquals("Middle", status.currentStationName)
        assertEquals(200f / 383f, status.progressFraction, 0.0001f)
        // The doors are open here, so this is still the next stop.
        assertEquals("MID", status.nextStopCode)
    }

    /**
     * Between stations nothing is current, and progress rests on the last station left.
     *
     * Interpolating a position along the leg would be a smoother bar and a fabricated one: the
     * timetable says nothing about where the train is between two halts.
     */
    @Test
    fun `between stations progress rests on the last station left`() = runTest {
        val status = providerAt(at(4, 2, 0)).fetchStatus(train(), schedule)

        assertTrue(status.stops.none { it.isCurrent })
        assertEquals(2, status.lastDepartedSerial)
        assertEquals("MID", status.currentStationCode)
        assertEquals(200f / 383f, status.progressFraction, 0.0001f)
        assertEquals("TRM", status.nextStopCode)
        assertEquals(LocalTime.of(5, 15), status.nextStopEta)
    }

    /**
     * A run that has reached its last station is over.
     *
     * The terminus has no departure, so "departed" there means "due to have arrived" — without
     * that the bar would stick one stop short for ever.
     */
    @Test
    fun `once the terminus is due the run reads as complete`() = runTest {
        val status = providerAt(at(4, 6, 0)).fetchStatus(train(), schedule)

        assertEquals(3, status.lastDepartedSerial)
        assertTrue(status.hasArrived)
        assertEquals(1f, status.progressFraction, 0.0001f)
        assertEquals("", status.nextStopCode)
        assertNull(status.nextStop())
    }

    // ---- The delay the user typed ------------------------------------------

    /**
     * A typed delay moves the whole projection, not just the label.
     *
     * At 01:25 an on-time train is standing at the halt. Thirty minutes down, it has not got
     * there yet — and that is the difference between "you are here" and "you have ten minutes".
     */
    @Test
    fun `a known delay pushes the train back along the route`() = runTest {
        val onTime = providerAt(at(4, 1, 25)).fetchStatus(train(), schedule)
        assertTrue(onTime.stops[1].isCurrent)

        val late = providerAt(at(4, 1, 25)).fetchStatus(train(delay = 30), schedule)

        assertFalse("30 minutes down, it has not reached the halt", late.stops[1].isCurrent)
        assertFalse(late.stops[1].isDeparted)
        assertEquals(1, late.lastDepartedSerial)
        assertEquals(0f, late.progressFraction, 0.0001f)
    }

    @Test
    fun `a known delay pushes the next-stop ETA back by the same amount`() = runTest {
        val status = providerAt(at(4, 1, 25)).fetchStatus(train(delay = 30), schedule)

        assertEquals("MID", status.nextStopCode)
        assertEquals(LocalTime.of(1, 50), status.nextStopEta)
        assertEquals(30, status.delayMinutes)
        assertTrue(status.isDelayed)
    }

    /**
     * The timetable time and the delay stay separate fields.
     *
     * The route screen shows "01:20" struck through beside "+30", so the scheduled time it is
     * handed has to be the published one — not one already pushed back, which would double the
     * delay on screen.
     */
    @Test
    fun `the scheduled time reported is the published one, not the delayed one`() = runTest {
        val status = providerAt(at(4, 1, 25)).fetchStatus(train(delay = 30), schedule)

        assertEquals(LocalTime.of(1, 20), status.stops[1].scheduledArrival)
        assertEquals(LocalTime.of(1, 30), status.stops[1].scheduledDeparture)
        assertEquals(30, status.stops[1].arrivalDelayMinutes)
        assertEquals(30, status.stops[1].departureDelayMinutes)
    }

    /** No arrival at the origin and no departure at the terminus, so no delay against either. */
    @Test
    fun `a delay is only reported against a time that exists`() = runTest {
        val status = providerAt(at(4, 6, 0)).fetchStatus(train(delay = 20), schedule)

        assertNull(status.stops.first().arrivalDelayMinutes)
        assertEquals(20, status.stops.first().departureDelayMinutes)
        assertEquals(20, status.stops.last().arrivalDelayMinutes)
        assertNull(status.stops.last().departureDelayMinutes)
    }

    @Test
    fun `an on-time train is not flagged as delayed`() = runTest {
        val status = providerAt(at(4, 2, 0)).fetchStatus(train(), schedule)

        assertEquals(0, status.delayMinutes)
        assertFalse(status.isDelayed)
    }

    // ---- What it refuses to invent -----------------------------------------

    /**
     * The central promise of this class.
     *
     * A projection has not seen the train, so it reports no observed time at any station and
     * therefore no speed. Every one of these nulls is what puts an em dash on the screen
     * instead of a number someone might act on.
     */
    @Test
    fun `a projection reports no observed times and no speed`() = runTest {
        val status = providerAt(at(4, 6, 0)).fetchStatus(train(), schedule)

        assertNull(status.averageSpeedKmph)
        assertTrue(status.stops.all { it.actualArrival == null })
        assertTrue(status.stops.all { it.actualDeparture == null })
    }

    @Test
    fun `a projection carries no provider message`() = runTest {
        val status = providerAt(at(4, 2, 0)).fetchStatus(train(), schedule)
        assertEquals("", status.message)
    }

    // ---- Run date and ordering ---------------------------------------------

    /**
     * The run date comes off the booking, not the clock.
     *
     * An overnight train boarded on the 3rd is still the 3rd's train at 01:00 on the 4th, and
     * opening the screen days later must not re-date the journey to today.
     */
    @Test
    fun `the run date is the train's own departure date`() = runTest {
        val status = providerAt(at(9, 14, 0)).fetchStatus(train(), schedule)

        assertEquals(runDate, status.runDate)
    }

    @Test
    fun `the snapshot is stamped with the time it was made`() = runTest {
        val now = at(4, 2, 0)
        val status = providerAt(now).fetchStatus(train(), schedule)

        assertEquals(now, status.fetchedAt)
    }

    @Test
    fun `a shuffled timetable comes back in route order`() = runTest {
        val shuffled = listOf(schedule[2], schedule[0], schedule[1])
        val status = providerAt(at(4, 2, 0)).fetchStatus(train(), shuffled)

        assertEquals(listOf(1, 2, 3), status.stops.map { it.serialNo })
        assertEquals(listOf("ORG", "MID", "TRM"), status.stops.map { it.stationCode })
    }

    @Test
    fun `the snapshot is keyed to the train it describes`() = runTest {
        val status = providerAt(at(4, 2, 0)).fetchStatus(train(id = 42L), schedule)
        assertEquals(42L, status.trainId)
    }

    /**
     * A timetable with no kilometres in it gets a bar at zero, mid-run and all.
     *
     * Deliberate, and inherited from [com.tripcompanion.app.domain.engine.TrainProgress]:
     * a fraction derived from station counts would move smoothly and mean nothing.
     */
    @Test
    fun `a timetable with no distances reports no progress`() = runTest {
        val noDistances = schedule.map { it.copy(distanceKm = 0) }
        val status = providerAt(at(4, 2, 0)).fetchStatus(train(), noDistances)

        assertEquals(2, status.lastDepartedSerial)
        assertEquals(0f, status.progressFraction, 0.0001f)
    }

    // ---- Helpers -----------------------------------------------------------

    private fun providerAt(now: LocalDateTime) =
        ScheduleProjectionTrainStatusProvider(FixedTimeProvider(now))

    /** A time on the run's November days, so the fixtures read as a clock rather than a date. */
    private fun at(day: Int, hour: Int, minute: Int): LocalDateTime =
        LocalDateTime.of(2026, 11, day, hour, minute)

    private fun train(id: Long = 1L, delay: Int = 0) = Train(
        id = id,
        tripId = 1L,
        number = "12992",
        name = "A train",
        originCode = "ORG",
        originName = "Origin",
        destinationCode = "TRM",
        destinationName = "Terminus",
        departureTime = LocalDateTime.of(runDate, LocalTime.of(22, 40)),
        arrivalTime = LocalDateTime.of(runDate.plusDays(1), LocalTime.of(5, 15)),
        knownDelayMinutes = delay
    )
}
