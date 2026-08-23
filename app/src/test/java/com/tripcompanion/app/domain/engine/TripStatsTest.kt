package com.tripcompanion.app.domain.engine

import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Home's four stat cards, and the line under a new trip's dates.
 *
 * The screens these numbers feed used to hold constants — a hardcoded "12 activities" survives
 * every refactor and is wrong the moment anything is added. So the contract worth testing is
 * not the arithmetic on its own but that each number is *derived*: change the events, and the
 * card changes with them.
 *
 * `compute` takes `today` rather than reading the clock, which is what makes "day 3 of 5"
 * assertable at all.
 */
class TripStatsTest {

    private val start = LocalDate.of(2026, 11, 3)
    private val end = LocalDate.of(2026, 11, 7)
    private val trip = Trip(id = 1L, name = "A trip", startDate = start, endDate = end)

    // ---- Duration ----------------------------------------------------------

    /** Inclusive of both ends: the 3rd to the 7th is five days away, not four. */
    @Test
    fun `a date range spans its days inclusively`() {
        val stats = TripStats.compute(trip, emptyList(), photoCount = 0, today = start)

        assertEquals(5, stats.totalDays)
        assertEquals(4, stats.nightCount)
    }

    @Test
    fun `a same-day trip lasts one day and no nights`() {
        val dayTrip = trip.copy(endDate = start)
        val stats = TripStats.compute(dayTrip, emptyList(), photoCount = 0, today = start)

        assertEquals(1, stats.totalDays)
        assertEquals(0, stats.nightCount)
        assertEquals("1 Day", stats.durationLabel)
    }

    /** A reversed range is a data problem, not a negative trip. */
    @Test
    fun `an end date before the start still lasts at least one day`() {
        val backwards = trip.copy(endDate = start.minusDays(3))
        val stats = TripStats.compute(backwards, emptyList(), photoCount = 0, today = start)

        assertEquals(1, stats.totalDays)
        assertEquals(0, stats.nightCount)
    }

    @Test
    fun `the duration label is the phrase the brief asks for`() {
        val stats = TripStats.compute(trip, emptyList(), photoCount = 0, today = start)

        assertEquals("5 Days · 4 Nights", stats.durationLabel)
    }

    @Test
    fun `one night is not pluralised`() {
        val twoDay = trip.copy(endDate = start.plusDays(1))
        val stats = TripStats.compute(twoDay, emptyList(), photoCount = 0, today = start)

        assertEquals("2 Days · 1 Night", stats.durationLabel)
    }

    @Test
    fun `with no trip there is no duration to label`() {
        assertEquals("", TripStats().durationLabel)
    }

    // ---- Day number --------------------------------------------------------

    @Test
    fun `the first day of the trip is day one`() {
        val stats = TripStats.compute(trip, emptyList(), photoCount = 0, today = start)
        assertEquals(1, stats.dayNumber)
    }

    @Test
    fun `the middle of the trip counts from the start date`() {
        val stats = TripStats.compute(
            trip,
            emptyList(),
            photoCount = 0,
            today = start.plusDays(2)
        )
        assertEquals(3, stats.dayNumber)
    }

    @Test
    fun `the last day is the total`() {
        val stats = TripStats.compute(trip, emptyList(), photoCount = 0, today = end)
        assertEquals(5, stats.dayNumber)
    }

    /**
     * Before the trip there is no day number, and after it there is no sixth day.
     *
     * The clamp is what lets a screen render "Day 3 of 5" without bounds-checking, so both
     * ends of it are worth pinning: an unclamped count would put "Day 9 of 5" on Home a week
     * after getting home.
     */
    @Test
    fun `a trip that has not started is on no day at all`() {
        val stats = TripStats.compute(
            trip,
            emptyList(),
            photoCount = 0,
            today = start.minusDays(1)
        )
        assertEquals(0, stats.dayNumber)
    }

    @Test
    fun `a long-finished trip does not run past its last day`() {
        val stats = TripStats.compute(
            trip,
            emptyList(),
            photoCount = 0,
            today = end.plusDays(30)
        )
        assertEquals(5, stats.dayNumber)
    }

    // ---- Counts derived from events ----------------------------------------

    @Test
    fun `the activity count is the events, not a constant`() {
        val stats = TripStats.compute(trip, events(3), photoCount = 0, today = start)
        assertEquals(3, stats.activityCount)

        // The assertion that matters: add one and the card moves.
        val more = TripStats.compute(trip, events(7), photoCount = 0, today = start)
        assertEquals(7, more.activityCount)
    }

    @Test
    fun `an empty itinerary counts nothing`() {
        val stats = TripStats.compute(trip, emptyList(), photoCount = 0, today = start)

        assertEquals(0, stats.activityCount)
        assertEquals(0, stats.completedCount)
        assertEquals(0, stats.placeCount)
        assertEquals(0, stats.remainingCount)
    }

    @Test
    fun `only completed events are counted as done`() {
        val list = listOf(
            event(1, status = EventStatus.COMPLETED),
            event(2, status = EventStatus.COMPLETED),
            event(3, status = EventStatus.SKIPPED),
            event(4, status = EventStatus.UPCOMING),
            event(5, status = EventStatus.ACTIVE)
        )

        val stats = TripStats.compute(trip, list, photoCount = 0, today = start)

        assertEquals(5, stats.activityCount)
        assertEquals(2, stats.completedCount)
        // Remaining is everything not completed, so the skipped one is still counted here.
        // That is deliberate: "3 to go" over a progress bar tracks the same denominator the
        // bar does, and a skipped item can be reopened.
        assertEquals(3, stats.remainingCount)
    }

    @Test
    fun `completion is a fraction of what is planned`() {
        val list = listOf(
            event(1, status = EventStatus.COMPLETED),
            event(2, status = EventStatus.COMPLETED),
            event(3),
            event(4)
        )

        assertEquals(
            0.5f,
            TripStats.compute(trip, list, photoCount = 0, today = start).completionFraction,
            0.0001f
        )
    }

    /** An empty itinerary is 0% done, not a crash. */
    @Test
    fun `nothing planned is nothing done rather than a division by zero`() {
        val stats = TripStats.compute(trip, emptyList(), photoCount = 0, today = start)
        assertEquals(0f, stats.completionFraction, 0.0001f)
    }

    @Test
    fun `a finished itinerary is fully complete`() {
        val list = List(4) { event(it.toLong() + 1, status = EventStatus.COMPLETED) }
        val stats = TripStats.compute(trip, list, photoCount = 0, today = start)

        assertEquals(1f, stats.completionFraction, 0.0001f)
        assertEquals(0, stats.remainingCount)
    }

    /**
     * Places counts distinct locations the itinerary visits.
     *
     * Two meals at the same restaurant is one place, and an event with nowhere attached is not
     * a place at all — otherwise the card counts rows rather than destinations.
     */
    @Test
    fun `places counts distinct locations, not events`() {
        val list = listOf(
            event(1, locationId = 10L),
            event(2, locationId = 20L),
            event(3, locationId = 10L),
            event(4, locationId = null),
            event(5, locationId = 30L)
        )

        val stats = TripStats.compute(trip, list, photoCount = 0, today = start)

        assertEquals(5, stats.activityCount)
        assertEquals(3, stats.placeCount)
    }

    @Test
    fun `an itinerary with nowhere attached visits no places`() {
        val list = listOf(event(1, locationId = null), event(2, locationId = null))
        val stats = TripStats.compute(trip, list, photoCount = 0, today = start)

        assertEquals(0, stats.placeCount)
    }

    // ---- Photo plans -------------------------------------------------------

    /** Photos are keyed by event rather than by trip, so the count is passed in and passed on. */
    @Test
    fun `the photo plan count is carried through`() {
        val stats = TripStats.compute(trip, events(2), photoCount = 9, today = start)
        assertEquals(9, stats.photoPlanCount)
    }

    /**
     * With no trip selected, the photo count is still the one real number available.
     *
     * Home renders before a trip is chosen, and zeroing a count it already has would make the
     * card flicker from a true number to a false one.
     */
    @Test
    fun `with no trip the photo count survives and everything else is zero`() {
        val stats = TripStats.compute(null, events(4), photoCount = 6, today = start)

        assertEquals(6, stats.photoPlanCount)
        assertEquals(0, stats.totalDays)
        assertEquals(0, stats.dayNumber)
        assertEquals(0, stats.activityCount)
        assertEquals(0, stats.completedCount)
        assertEquals(0, stats.placeCount)
    }

    // ---- Helpers -----------------------------------------------------------

    private fun events(count: Int): List<Event> = (1..count).map { event(it.toLong()) }

    private fun event(
        id: Long,
        status: EventStatus = EventStatus.UPCOMING,
        locationId: Long? = null
    ) = Event(
        id = id,
        tripId = 1L,
        type = EventType.VISIT,
        title = "Event $id",
        startTime = LocalDateTime.of(start, java.time.LocalTime.of(9, 0)).plusHours(id),
        endTime = LocalDateTime.of(start, java.time.LocalTime.of(10, 0)).plusHours(id),
        locationId = locationId,
        status = status
    )
}
