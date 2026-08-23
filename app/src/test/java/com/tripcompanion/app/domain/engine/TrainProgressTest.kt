package com.tripcompanion.app.domain.engine

import com.tripcompanion.app.domain.model.TrainStopStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/**
 * The arithmetic a passenger reads off a platform.
 *
 * [TrainProgress] is pure so that the route shapes that actually break it — nothing departed,
 * standing at a station, overnight, terminated, a schedule with no distances — can be written
 * down rather than waited for. The two rules the engine documents are what these tests hold it
 * to: progress is measured in kilometres and never in station counts, and anything that cannot
 * be worked out honestly comes back null instead of estimated.
 *
 * Station names here are ordinals because none of this depends on where the train is going.
 */
class TrainProgressTest {

    // ---- progressFraction --------------------------------------------------

    @Test
    fun `an empty route has no progress`() {
        assertEquals(0f, TrainProgress.progressFraction(emptyList()), 0.0001f)
    }

    /**
     * A schedule with no kilometres in it gets a bar at zero, not a guess.
     *
     * Some routes come back with every `Distance` empty. Zero reads as "not started", which is
     * at least defensible; a fraction derived from station counts reads as precision.
     */
    @Test
    fun `a route with no distances reports zero rather than guessing`() {
        val stops = listOf(
            stop(1, km = 0, departed = true),
            stop(2, km = 0, departed = true),
            stop(3, km = 0, current = true)
        )
        assertEquals(0f, TrainProgress.progressFraction(stops), 0.0001f)
    }

    @Test
    fun `a run with every station behind it is complete`() {
        val stops = listOf(
            stop(1, km = 0, departed = true),
            stop(2, km = 200, departed = true),
            stop(3, km = 400, departed = true)
        )
        assertEquals(1f, TrainProgress.progressFraction(stops), 0.0001f)
    }

    @Test
    fun `a train that has not moved has no progress`() {
        val stops = listOf(stop(1, km = 0), stop(2, km = 200), stop(3, km = 400))
        assertEquals(0f, TrainProgress.progressFraction(stops), 0.0001f)
    }

    /**
     * The assertion the whole engine exists for.
     *
     * These distances are deliberately uneven: by station count the train is a third of the way
     * along, by kilometre it is seven eighths. A bar showing 33% here would be wrong by half the
     * journey, and it is the reading someone decides whether to leave for the station on.
     */
    @Test
    fun `progress is measured in kilometres, not in stations passed`() {
        val stops = listOf(
            stop(1, km = 0, departed = true),
            stop(2, km = 350, current = true),
            stop(3, km = 380),
            stop(4, km = 400)
        )

        assertEquals(0.875f, TrainProgress.progressFraction(stops), 0.0001f)
    }

    @Test
    fun `the station the provider calls current wins over the furthest one departed`() {
        val stops = listOf(
            stop(1, km = 0, departed = true),
            stop(2, km = 100, departed = true),
            stop(3, km = 300, current = true),
            stop(4, km = 400)
        )

        assertEquals(0.75f, TrainProgress.progressFraction(stops), 0.0001f)
    }

    /** The offline projection has no notion of "current", so the furthest departed answers. */
    @Test
    fun `with nothing flagged current the furthest departed station answers`() {
        val stops = listOf(
            stop(1, km = 0, departed = true),
            stop(2, km = 100, departed = true),
            stop(3, km = 300),
            stop(4, km = 400)
        )

        assertEquals(0.25f, TrainProgress.progressFraction(stops), 0.0001f)
    }

    // ---- lastDepartedSerial ------------------------------------------------

    @Test
    fun `nothing departed is serial zero, not serial one`() {
        val stops = listOf(stop(1, km = 0), stop(2, km = 200))
        assertEquals(0, TrainProgress.lastDepartedSerial(stops))
    }

    @Test
    fun `the last departed station is the highest departed serial`() {
        val stops = listOf(
            stop(1, km = 0, departed = true),
            stop(2, km = 100, departed = true),
            stop(3, km = 300)
        )
        assertEquals(2, TrainProgress.lastDepartedSerial(stops))
    }

    /** Route order comes from `serialNo`, never from the order rows happened to arrive in. */
    @Test
    fun `serial order beats list order`() {
        val stops = listOf(
            stop(3, km = 300, departed = true),
            stop(1, km = 0, departed = true),
            stop(2, km = 100, departed = true)
        )
        assertEquals(3, TrainProgress.lastDepartedSerial(stops))
    }

    // ---- nextStop ----------------------------------------------------------

    @Test
    fun `the next stop is the first one not yet departed`() {
        val stops = listOf(
            stop(1, km = 0, departed = true),
            stop(2, km = 100, departed = true),
            stop(3, km = 300),
            stop(4, km = 400)
        )
        assertEquals(3, TrainProgress.nextStop(stops)?.serialNo)
    }

    /**
     * Standing at a station, that station is still "next".
     *
     * This matches what a platform display means by the words: the next place the doors open,
     * not the one after this. Skipping to stop 4 while the train sits at stop 3 would tell
     * someone waiting at stop 3 that they had missed it.
     */
    @Test
    fun `the station being stood at is the next stop`() {
        val stops = listOf(
            stop(1, km = 0, departed = true),
            stop(2, km = 100, departed = true),
            stop(3, km = 300, current = true),
            stop(4, km = 400)
        )
        assertEquals(3, TrainProgress.nextStop(stops)?.serialNo)
    }

    @Test
    fun `a finished run has no next stop`() {
        val stops = listOf(
            stop(1, km = 0, departed = true),
            stop(2, km = 400, departed = true)
        )
        assertNull(TrainProgress.nextStop(stops))
    }

    @Test
    fun `the next stop is found in an unsorted route`() {
        val stops = listOf(
            stop(4, km = 400),
            stop(2, km = 100, departed = true),
            stop(3, km = 300),
            stop(1, km = 0, departed = true)
        )
        assertEquals(3, TrainProgress.nextStop(stops)?.serialNo)
    }

    // ---- etaFor ------------------------------------------------------------

    @Test
    fun `a reported arrival wins outright over any estimate`() {
        val arrived = stop(
            2,
            km = 100,
            arrival = t(8, 30),
            actualArrival = t(8, 47),
            departed = true
        )
        assertEquals(t(8, 47), TrainProgress.etaFor(arrived, delayMinutes = 60))
    }

    /**
     * The assumption every station display makes: a train 20 down stays 20 down.
     *
     * It is not a prediction so much as the only honest extrapolation available — and it is
     * why the ETA moves when the delay does.
     */
    @Test
    fun `an unreported arrival is the schedule pushed back by the delay being carried`() {
        val ahead = stop(3, km = 300, arrival = t(11, 40))
        assertEquals(t(11, 54), TrainProgress.etaFor(ahead, delayMinutes = 14))
    }

    @Test
    fun `an on-time train arrives when the timetable says`() {
        val ahead = stop(3, km = 300, arrival = t(11, 40))
        assertEquals(t(11, 40), TrainProgress.etaFor(ahead, delayMinutes = 0))
    }

    @Test
    fun `a train running early arrives early`() {
        val ahead = stop(3, km = 300, arrival = t(11, 40))
        assertEquals(t(11, 35), TrainProgress.etaFor(ahead, delayMinutes = -5))
    }

    /** The origin has nothing to be late for. */
    @Test
    fun `a stop with no scheduled arrival has no ETA`() {
        val origin = stop(1, km = 0, departure = t(6, 20))
        assertNull(TrainProgress.etaFor(origin, delayMinutes = 14))
    }

    /** An ETA past midnight wraps the clock rather than overflowing it. */
    @Test
    fun `a delay across midnight wraps the clock`() {
        val ahead = stop(5, km = 700, arrival = t(23, 50))
        assertEquals(t(0, 20), TrainProgress.etaFor(ahead, delayMinutes = 30))
    }

    // ---- currentDelayMinutes -----------------------------------------------

    @Test
    fun `the delay is read off the current station`() {
        val stops = listOf(
            stop(1, km = 0, departed = true, departureDelay = 0),
            stop(2, km = 300, current = true, arrivalDelay = 22)
        )
        assertEquals(22, TrainProgress.currentDelayMinutes(stops))
    }

    /**
     * Departure delay outranks arrival delay at the same station.
     *
     * A train that arrives 20 down and sits for an extra ten leaves 30 down, and 30 is what the
     * rest of the route inherits.
     */
    @Test
    fun `a departure delay outranks an arrival delay at the same station`() {
        val stops = listOf(
            stop(1, km = 0, departed = true, departureDelay = 0),
            stop(2, km = 300, current = true, arrivalDelay = 20, departureDelay = 30)
        )
        assertEquals(30, TrainProgress.currentDelayMinutes(stops))
    }

    /**
     * A current station with nothing reported falls back to the last one left.
     *
     * This is the common mid-section shape: the railway names the station being approached but
     * has no times for it yet, and the delay carried out of the previous station is the answer.
     */
    @Test
    fun `an unreported current station falls back to the last station left`() {
        val stops = listOf(
            stop(1, km = 0, departed = true, departureDelay = 0),
            stop(2, km = 135, departed = true, arrivalDelay = 14, departureDelay = 14),
            stop(3, km = 248, current = true),
            stop(4, km = 383)
        )
        assertEquals(14, TrainProgress.currentDelayMinutes(stops))
    }

    @Test
    fun `the fallback takes the highest departed serial, not the last row`() {
        val stops = listOf(
            stop(2, km = 135, departed = true, departureDelay = 14),
            stop(1, km = 0, departed = true, departureDelay = 3),
            stop(3, km = 248)
        )
        assertEquals(14, TrainProgress.currentDelayMinutes(stops))
    }

    /** No delay reported anywhere means on time — the railway's own default. */
    @Test
    fun `a route with no delay information is on time`() {
        val stops = listOf(stop(1, km = 0, departed = true), stop(2, km = 300))
        assertEquals(0, TrainProgress.currentDelayMinutes(stops))
    }

    @Test
    fun `a train yet to start carries no delay`() {
        val stops = listOf(stop(1, km = 0, departureDelay = 12), stop(2, km = 300))
        assertEquals(0, TrainProgress.currentDelayMinutes(stops))
    }

    // ---- averageSpeedKmph --------------------------------------------------

    @Test
    fun `average speed is ground covered over time taken`() {
        val stops = listOf(
            stop(1, km = 0, departed = true, actualDeparture = t(6, 0)),
            stop(2, km = 240, departed = true, actualDeparture = t(9, 0))
        )

        val speed = TrainProgress.averageSpeedKmph(stops)
        assertNotNull(speed)
        assertEquals(80.0, speed!!, 0.001)
    }

    /**
     * One reported time is not an average.
     *
     * Before the second station there is nothing to divide, and the screen shows an em dash
     * rather than the origin's own speed of zero.
     */
    @Test
    fun `a single reported time yields no average`() {
        val stops = listOf(
            stop(1, km = 0, departed = true, actualDeparture = t(6, 0)),
            stop(2, km = 240)
        )
        assertNull(TrainProgress.averageSpeedKmph(stops))
    }

    @Test
    fun `a route with no reported times yields no average`() {
        val stops = listOf(stop(1, km = 0, departure = t(6, 0)), stop(2, km = 240, arrival = t(9, 0)))
        assertNull(TrainProgress.averageSpeedKmph(stops))
    }

    @Test
    fun `a departure time is preferred over an arrival time at the same station`() {
        val stops = listOf(
            stop(1, km = 0, departed = true, actualArrival = t(6, 0), actualDeparture = t(6, 30)),
            stop(2, km = 150, departed = true, actualArrival = t(8, 0), actualDeparture = t(8, 10))
        )

        // 150 km between the two departures, 100 minutes apart. Off the arrivals it would be 75.
        assertEquals(90.0, TrainProgress.averageSpeedKmph(stops)!!, 0.001)
    }

    /** Without folding the day in, this is minus twenty-one hours and a 900 km/h train. */
    @Test
    fun `an overnight run folds the day offset in before subtracting`() {
        val stops = listOf(
            stop(1, km = 0, departed = true, actualDeparture = t(23, 0), day = 0),
            stop(2, km = 200, departed = true, actualDeparture = t(2, 0), day = 1)
        )

        val speed = TrainProgress.averageSpeedKmph(stops)
        assertNotNull(speed)
        assertEquals(66.667, speed!!, 0.001)
    }

    @Test
    fun `a route reporting no distance progress yields no average`() {
        val stops = listOf(
            stop(1, km = 100, departed = true, actualDeparture = t(6, 0)),
            stop(2, km = 100, departed = true, actualDeparture = t(9, 0))
        )
        assertNull(TrainProgress.averageSpeedKmph(stops))
    }

    /** Times running backwards without a day offset to explain them are not survivable. */
    @Test
    fun `time running backwards yields no average rather than a negative one`() {
        val stops = listOf(
            stop(1, km = 0, departed = true, actualDeparture = t(23, 0), day = 0),
            stop(2, km = 200, departed = true, actualDeparture = t(2, 0), day = 0)
        )
        assertNull(TrainProgress.averageSpeedKmph(stops))
    }

    @Test
    fun `two stations reported at the same minute yield no average`() {
        val stops = listOf(
            stop(1, km = 0, departed = true, actualDeparture = t(6, 0)),
            stop(2, km = 40, departed = true, actualDeparture = t(6, 0))
        )
        assertNull(TrainProgress.averageSpeedKmph(stops))
    }

    /** The whole covered stretch, not just the last leg. */
    @Test
    fun `the average spans the first and last reported stations`() {
        val stops = listOf(
            stop(1, km = 0, departed = true, actualDeparture = t(6, 0)),
            stop(2, km = 60, departed = true, actualDeparture = t(7, 0)),
            stop(3, km = 300, departed = true, actualDeparture = t(9, 0)),
            stop(4, km = 400)
        )

        // 300 km over three hours, not the 120 km/h of the final leg alone.
        assertEquals(100.0, TrainProgress.averageSpeedKmph(stops)!!, 0.001)
    }

    // ---- Helpers -----------------------------------------------------------

    private fun t(hour: Int, minute: Int): LocalTime = LocalTime.of(hour, minute)

    private fun stop(
        serial: Int,
        km: Int = 0,
        departed: Boolean = false,
        current: Boolean = false,
        arrival: LocalTime? = null,
        departure: LocalTime? = null,
        actualArrival: LocalTime? = null,
        actualDeparture: LocalTime? = null,
        arrivalDelay: Int? = null,
        departureDelay: Int? = null,
        day: Int = 0
    ) = TrainStopStatus(
        serialNo = serial,
        stationCode = "S$serial",
        stationName = "Station $serial",
        scheduledArrival = arrival,
        actualArrival = actualArrival,
        scheduledDeparture = departure,
        actualDeparture = actualDeparture,
        arrivalDelayMinutes = arrivalDelay,
        departureDelayMinutes = departureDelay,
        distanceKm = km,
        dayOffset = day,
        isDeparted = departed,
        isCurrent = current
    )
}
