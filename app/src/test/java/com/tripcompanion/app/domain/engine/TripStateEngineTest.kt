package com.tripcompanion.app.domain.engine

import com.tripcompanion.app.domain.model.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TripStateEngineTest {

    private lateinit var engine: TripStateEngine
    private lateinit var baseTrip: Trip

    @Before
    fun setUp() {
        engine = TripStateEngine()
        baseTrip = Trip(
            id = 1,
            name = "Test Trip",
            startDate = LocalDate.of(2026, 8, 20),
            endDate = LocalDate.of(2026, 8, 23),
            status = TripStatus.ACTIVE
        )
    }

    private fun makeEvent(
        id: Long,
        startHour: Int,
        endHour: Int,
        day: Int = 20,
        order: Int = id.toInt(),
        status: EventStatus = EventStatus.UPCOMING,
        type: EventType = EventType.VISIT,
        title: String = "Event $id"
    ): Event = Event(
        id = id,
        tripId = 1,
        type = type,
        title = title,
        startTime = LocalDateTime.of(2026, 8, day, startHour, 0),
        endTime = LocalDateTime.of(2026, 8, day, endHour, 0),
        status = status,
        order = order
    )

    // ── Test 1: Empty trip ──

    @Test
    fun testEmptyTrip() {
        val state = engine.computeState(baseTrip, emptyList(), LocalDateTime.of(2026, 8, 20, 10, 0))
        assertNull(state.currentEvent)
        assertNull(state.nextEvent)
        assertNull(state.previousEvent)
        assertEquals(0, state.totalEventCount)
        assertEquals(0f, state.progressPercent)
    }

    // ── Test 2: Single event ──

    @Test
    fun testSingleEvent_beforeStart() {
        val event = makeEvent(1, 14, 16)
        val now = LocalDateTime.of(2026, 8, 20, 10, 0) // 10:00 — event at 14:00

        val state = engine.computeState(baseTrip, listOf(event), now)
        assertNull(state.currentEvent)
        assertNotNull(state.nextEvent)
        assertEquals(1L, state.nextEvent!!.id)
        assertEquals(EventStatus.UPCOMING, state.nextEvent!!.status)
    }

    @Test
    fun testSingleEvent_duringEvent() {
        val event = makeEvent(1, 14, 16)
        val now = LocalDateTime.of(2026, 8, 20, 15, 0) // 15:00 — inside [14, 16]

        val state = engine.computeState(baseTrip, listOf(event), now)
        assertNotNull(state.currentEvent)
        assertEquals(1L, state.currentEvent!!.id)
        assertEquals(EventStatus.ACTIVE, state.currentEvent!!.status)
        assertNull(state.nextEvent)
    }

    @Test
    fun testSingleEvent_afterEnd() {
        val event = makeEvent(1, 14, 16)
        val now = LocalDateTime.of(2026, 8, 20, 17, 0) // 17:00 — past [14, 16]

        val state = engine.computeState(baseTrip, listOf(event), now)
        assertNull(state.currentEvent)
        assertNull(state.nextEvent)
        // Should be MISSED since it wasn't completed
        assertEquals(EventStatus.MISSED, state.eventsWithComputedStatus[0].status)
    }

    // ── Test 3: Multiple events ──

    @Test
    fun testMultipleEvents_currentAndNext() {
        val events = listOf(
            makeEvent(1, 9, 11),
            makeEvent(2, 12, 14),
            makeEvent(3, 15, 17)
        )
        val now = LocalDateTime.of(2026, 8, 20, 10, 0) // During event 1

        val state = engine.computeState(baseTrip, events, now)
        assertEquals(1L, state.currentEvent!!.id)
        assertEquals(2L, state.nextEvent!!.id)
        assertNull(state.previousEvent)
    }

    @Test
    fun testMultipleEvents_betweenEvents() {
        val events = listOf(
            makeEvent(1, 9, 11),
            makeEvent(2, 14, 16)
        )
        val now = LocalDateTime.of(2026, 8, 20, 12, 0) // Between events

        val state = engine.computeState(baseTrip, events, now)
        assertNull(state.currentEvent) // No event active at 12:00
        // Event 1 should be MISSED (past end, not completed)
        assertEquals(EventStatus.MISSED, state.eventsWithComputedStatus[0].status)
        assertEquals(2L, state.nextEvent!!.id)
    }

    // ── Test 4: Multiple days ──

    @Test
    fun testMultipleDays() {
        val events = listOf(
            makeEvent(1, 10, 12, day = 20),
            makeEvent(2, 10, 12, day = 21),
            makeEvent(3, 10, 12, day = 22)
        )
        val now = LocalDateTime.of(2026, 8, 21, 11, 0) // Day 2, during event 2

        val state = engine.computeState(baseTrip, events, now)
        assertEquals(2L, state.currentEvent!!.id)
        assertEquals(2, state.currentTripDay) // Day 2 of trip
    }

    // ── Test 5: Current event ──

    @Test
    fun testCurrentEvent_atExactStartTime() {
        val event = makeEvent(1, 10, 12)
        val now = LocalDateTime.of(2026, 8, 20, 10, 0) // Exactly at start

        val state = engine.computeState(baseTrip, listOf(event), now)
        assertNotNull(state.currentEvent)
        assertEquals(EventStatus.ACTIVE, state.currentEvent!!.status)
    }

    // ── Test 6: No current event ──

    @Test
    fun testNoCurrentEvent_beforeFirstEvent() {
        val event = makeEvent(1, 14, 16)
        val now = LocalDateTime.of(2026, 8, 20, 8, 0) // Before event

        val state = engine.computeState(baseTrip, listOf(event), now)
        assertNull(state.currentEvent)
        assertEquals(1L, state.nextEvent!!.id)
    }

    // ── Test 7: Completed current event ──

    @Test
    fun testCompletedCurrentEvent_movesToNext() {
        val events = listOf(
            makeEvent(1, 10, 14, status = EventStatus.COMPLETED),
            makeEvent(2, 15, 17)
        )
        val now = LocalDateTime.of(2026, 8, 20, 12, 0) // During event 1's window

        val state = engine.computeState(baseTrip, events, now)
        // Event 1 is completed, so not current despite being in its window
        assertNull(state.currentEvent)
        assertEquals(2L, state.nextEvent!!.id)
        // Event 1 should be previous
        assertEquals(1L, state.previousEvent!!.id)
    }

    // ── Test 8: Skipped event ──

    @Test
    fun testSkippedEvent_isIgnored() {
        val events = listOf(
            makeEvent(1, 10, 12),
            makeEvent(2, 12, 14, status = EventStatus.SKIPPED),
            makeEvent(3, 15, 17)
        )
        val now = LocalDateTime.of(2026, 8, 20, 13, 0) // During event 2's window

        val state = engine.computeState(baseTrip, events, now)
        // Event 2 is skipped, so not current
        assertNull(state.currentEvent)
        // Next should be event 3
        assertEquals(3L, state.nextEvent!!.id)
    }

    // ── Test 9: Missed event ──

    @Test
    fun testMissedEvent() {
        val event = makeEvent(1, 10, 12)
        val now = LocalDateTime.of(2026, 8, 20, 14, 0) // Past end time

        val status = engine.computeEventStatus(event, now)
        assertEquals(EventStatus.MISSED, status)
    }

    // ── Test 10: Overlapping events (Priority Rule: startTime ascending, then order ascending, then id ascending) ──

    @Test
    fun testOverlappingEvents() {
        val events = listOf(
            makeEvent(1, 10, 14, order = 1),
            makeEvent(2, 12, 16, order = 2)
        )
        val now = LocalDateTime.of(2026, 8, 20, 13, 0) // Both overlap

        val state = engine.computeState(baseTrip, events, now)
        // First by startTime/order should be current
        assertEquals(1L, state.currentEvent!!.id)
        // Second should also be active in computed statuses
        assertEquals(EventStatus.ACTIVE, state.eventsWithComputedStatus[1].status)
    }

    // ── Test 11: Reordered events with different start times ──

    @Test
    fun testReorderedEvents() {
        val events = listOf(
            makeEvent(1, 14, 16, order = 2), // Order 2, 14:00
            makeEvent(2, 10, 12, order = 1)  // Order 1, 10:00
        )
        val now = LocalDateTime.of(2026, 8, 20, 11, 0) // During event 2

        val state = engine.computeState(baseTrip, events, now)
        // Event 2 is chronologically first (10:00), so it is current
        assertEquals(2L, state.currentEvent!!.id)
        assertEquals(1L, state.nextEvent!!.id)
    }

    // ── Test 12: Starting soon ──

    @Test
    fun testStartingSoon() {
        val event = makeEvent(1, 10, 12)
        val now = LocalDateTime.of(2026, 8, 20, 9, 45) // 15 min before start

        val status = engine.computeEventStatus(event, now)
        assertEquals(EventStatus.STARTING_SOON, status)
    }

    @Test
    fun testNotStartingSoon_tooEarly() {
        val event = makeEvent(1, 10, 12)
        val now = LocalDateTime.of(2026, 8, 20, 9, 0) // 60 min before start

        val status = engine.computeEventStatus(event, now)
        assertEquals(EventStatus.UPCOMING, status)
    }

    // ── Test 13: Manual overrides preserved ──

    @Test
    fun testManualOverrides_completedPreserved() {
        val event = makeEvent(1, 10, 12, status = EventStatus.COMPLETED)
        val now = LocalDateTime.of(2026, 8, 20, 11, 0) // During window

        val status = engine.computeEventStatus(event, now)
        assertEquals(EventStatus.COMPLETED, status) // Not overridden to ACTIVE
    }

    @Test
    fun testManualOverrides_skippedPreserved() {
        val event = makeEvent(1, 10, 12, status = EventStatus.SKIPPED)
        val now = LocalDateTime.of(2026, 8, 20, 9, 0) // Before event

        val status = engine.computeEventStatus(event, now)
        assertEquals(EventStatus.SKIPPED, status) // Not overridden to UPCOMING
    }

    // ── Test 14: Null trip ──

    @Test
    fun testNullTrip() {
        val state = engine.computeState(null, emptyList(), LocalDateTime.now())
        assertNull(state.trip)
        assertNull(state.currentEvent)
    }

    // ── Test 15: Trip day calculation ──

    @Test
    fun testTripDay_beforeTrip() {
        val now = LocalDateTime.of(2026, 8, 19, 10, 0) // Day before trip
        val state = engine.computeState(baseTrip, emptyList(), now)
        assertEquals(0, state.currentTripDay)
    }

    @Test
    fun testTripDay_firstDay() {
        val now = LocalDateTime.of(2026, 8, 20, 10, 0) // First day
        val state = engine.computeState(baseTrip, emptyList(), now)
        assertEquals(1, state.currentTripDay)
    }

    @Test
    fun testTripDay_lastDay() {
        val now = LocalDateTime.of(2026, 8, 23, 10, 0) // Last day
        val state = engine.computeState(baseTrip, emptyList(), now)
        assertEquals(4, state.currentTripDay)
        assertEquals(4, state.totalTripDays)
    }

    // ── Test 16: Progress calculation ──

    @Test
    fun testProgress() {
        val events = listOf(
            makeEvent(1, 9, 11, status = EventStatus.COMPLETED),
            makeEvent(2, 12, 14, status = EventStatus.COMPLETED),
            makeEvent(3, 15, 17)
        )
        val now = LocalDateTime.of(2026, 8, 20, 8, 0)

        val state = engine.computeState(baseTrip, events, now)
        assertEquals(2, state.completedEventCount)
        assertEquals(3, state.totalEventCount)
        assertEquals(2f / 3f, state.progressPercent, 0.01f)
    }

    // ── Test: Trip phase ──

    @Test
    fun testTripPhase_notStarted() {
        val now = LocalDateTime.of(2026, 8, 19, 10, 0)
        val state = engine.computeState(baseTrip, emptyList(), now)
        assertEquals(TripPhase.NOT_STARTED, state.currentTripState)
    }

    @Test
    fun testTripPhase_inProgress() {
        val now = LocalDateTime.of(2026, 8, 21, 10, 0)
        val state = engine.computeState(baseTrip, listOf(makeEvent(1, 10, 12, day = 21)), now)
        assertEquals(TripPhase.IN_PROGRESS, state.currentTripState)
    }

    @Test
    fun testTripPhase_completed_allEventsDone() {
        val events = listOf(
            makeEvent(1, 9, 11, status = EventStatus.COMPLETED),
            makeEvent(2, 12, 14, status = EventStatus.SKIPPED)
        )
        val now = LocalDateTime.of(2026, 8, 21, 10, 0)
        val state = engine.computeState(baseTrip, events, now)
        assertEquals(TripPhase.COMPLETED, state.currentTripState)
    }

    // ── Chronological Sorting Test 1: Random insertion times on same day ──

    @Test
    fun testChronologicalSorting_randomInsertionOrder() {
        val event1800 = makeEvent(1, 18, 20, order = 0, title = "Dinner")
        val event0800 = makeEvent(2, 8, 9, order = 1, title = "Breakfast")
        val event1400 = makeEvent(3, 14, 16, order = 2, title = "Museum")
        val event1000 = makeEvent(4, 10, 12, order = 3, title = "Palace")

        val state = engine.computeState(
            baseTrip,
            listOf(event1800, event0800, event1400, event1000),
            LocalDateTime.of(2026, 8, 20, 7, 0)
        )

        val sortedIds = state.eventsWithComputedStatus.map { it.id }
        assertEquals(listOf(2L, 4L, 3L, 1L), sortedIds)
        assertEquals(2L, state.nextEvent?.id) // Next is 08:00 Breakfast
    }

    // ── Chronological Sorting Test 2: Multiple dates in arbitrary insertion order ──

    @Test
    fun testChronologicalSorting_multipleDates() {
        val evDay22_10 = makeEvent(1, 10, 12, day = 22, order = 0)
        val evDay21_18 = makeEvent(2, 18, 20, day = 21, order = 1)
        val evDay23_09 = makeEvent(3, 9, 11, day = 23, order = 2)
        val evDay21_08 = makeEvent(4, 8, 10, day = 21, order = 3)

        val state = engine.computeState(
            baseTrip,
            listOf(evDay22_10, evDay21_18, evDay23_09, evDay21_08),
            LocalDateTime.of(2026, 8, 20, 10, 0)
        )

        val sortedIds = state.eventsWithComputedStatus.map { it.id }
        // Expected: 21 Aug 08:00 (id 4), 21 Aug 18:00 (id 2), 22 Aug 10:00 (id 1), 23 Aug 09:00 (id 3)
        assertEquals(listOf(4L, 2L, 1L, 3L), sortedIds)
    }

    // ── Chronological Sorting Test 3: Deterministic tie breaking ──

    @Test
    fun testDeterministicTieBreaking_sameStartTime() {
        val evA = makeEvent(10, 10, 12, day = 20, order = 1, title = "A")
        val evB = makeEvent(20, 10, 12, day = 20, order = 2, title = "B")

        val state = engine.computeState(
            baseTrip,
            listOf(evB, evA),
            LocalDateTime.of(2026, 8, 20, 9, 0)
        )

        val sortedIds = state.eventsWithComputedStatus.map { it.id }
        assertEquals(listOf(10L, 20L), sortedIds)
    }

    // ── Screenshot Bug Reproduction Test: Past events cannot be ACTIVE ──

    @Test
    fun testScreenshotBug_pastEventNotActive() {
        // Event on August 20 from 01:00 to 03:00
        val jagdishMandir = Event(
            id = 1,
            tripId = 1,
            type = EventType.VISIT,
            title = "Jagdish Mandir",
            startTime = LocalDateTime.of(2026, 8, 20, 1, 0),
            endTime = LocalDateTime.of(2026, 8, 20, 3, 0),
            status = EventStatus.UPCOMING,
            order = 0
        )

        // Event on August 21 from 00:00 to 02:00
        val cityPalace = Event(
            id = 2,
            tripId = 1,
            type = EventType.VISIT,
            title = "City Palace",
            startTime = LocalDateTime.of(2026, 8, 21, 0, 0),
            endTime = LocalDateTime.of(2026, 8, 21, 2, 0),
            status = EventStatus.COMPLETED,
            order = 1
        )

        // Current time: 21 August at 02:35
        val now = LocalDateTime.of(2026, 8, 21, 2, 35)

        val state = engine.computeState(baseTrip, listOf(jagdishMandir, cityPalace), now)

        // Jagdish Mandir (ended 23.5 hours ago) MUST NOT BE ACTIVE! It must be MISSED.
        val computedJagdish = state.eventsWithComputedStatus.first { it.id == 1L }
        assertEquals(EventStatus.MISSED, computedJagdish.status)
        assertNotEquals(EventStatus.ACTIVE, computedJagdish.status)

        // Current event at 02:35 should be null (both events have ended)
        assertNull("At 02:35 on Aug 21, no event should be active", state.currentEvent)
    }

    @Test
    fun testEventStateTransitions_boundaryValues() {
        val event = Event(
            id = 1,
            tripId = 1,
            type = EventType.VISIT,
            title = "Visit",
            startTime = LocalDateTime.of(2026, 8, 21, 1, 0),
            endTime = LocalDateTime.of(2026, 8, 21, 3, 0),
            status = EventStatus.UPCOMING,
            order = 0
        )

        // At 00:29 (31 min before) -> UPCOMING
        assertEquals(EventStatus.UPCOMING, engine.computeEventStatus(event, LocalDateTime.of(2026, 8, 21, 0, 29)))

        // At 00:31 (29 min before) -> STARTING_SOON
        assertEquals(EventStatus.STARTING_SOON, engine.computeEventStatus(event, LocalDateTime.of(2026, 8, 21, 0, 31)))

        // At 01:00 (exact start) -> ACTIVE
        assertEquals(EventStatus.ACTIVE, engine.computeEventStatus(event, LocalDateTime.of(2026, 8, 21, 1, 0)))

        // At 02:00 (during event) -> ACTIVE
        assertEquals(EventStatus.ACTIVE, engine.computeEventStatus(event, LocalDateTime.of(2026, 8, 21, 2, 0)))

        // At 03:00 (exact end) -> ACTIVE
        assertEquals(EventStatus.ACTIVE, engine.computeEventStatus(event, LocalDateTime.of(2026, 8, 21, 3, 0)))

        // At 03:01 (1 minute past end) -> MISSED
        assertEquals(EventStatus.MISSED, engine.computeEventStatus(event, LocalDateTime.of(2026, 8, 21, 3, 1)))
    }

    // ── Test: Complete Required Itinerary Specification ──

    @Test
    fun testSpecificationItineraryProgression() {
        val journey = makeEvent(1, 8, 10, order = 1, type = EventType.JOURNEY, title = "Journey")
        val visitA = makeEvent(2, 11, 13, order = 2, type = EventType.VISIT, title = "Visit A")
        val visitB = makeEvent(3, 17, 19, order = 3, type = EventType.VISIT, title = "Visit B")
        val itinerary = listOf(journey, visitA, visitB)

        // 10:30: Current = none, Next = Visit A (starting soon at 11:00)
        val state1030 = engine.computeState(baseTrip, itinerary, LocalDateTime.of(2026, 8, 20, 10, 30))
        assertNull("At 10:30 current should be null", state1030.currentEvent)
        assertEquals("At 10:30 next should be Visit A", 2L, state1030.nextEvent?.id)

        // 12:00: Current = Visit A, Next = Visit B
        val state1200 = engine.computeState(baseTrip, itinerary, LocalDateTime.of(2026, 8, 20, 12, 0))
        assertEquals("At 12:00 current should be Visit A", 2L, state1200.currentEvent?.id)
        assertEquals("At 12:00 next should be Visit B", 3L, state1200.nextEvent?.id)

        // 14:00: Current = none, Next = Visit B
        val state1400 = engine.computeState(baseTrip, itinerary, LocalDateTime.of(2026, 8, 20, 14, 0))
        assertNull("At 14:00 current should be null", state1400.currentEvent)
        assertEquals("At 14:00 next should be Visit B", 3L, state1400.nextEvent?.id)

        // 18:00: Current = Visit B, Next = none
        val state1800 = engine.computeState(baseTrip, itinerary, LocalDateTime.of(2026, 8, 20, 18, 0))
        assertEquals("At 18:00 current should be Visit B", 3L, state1800.currentEvent?.id)
        assertNull("At 18:00 next should be null", state1800.nextEvent)

        // After manually completing Visit A: Visit A must no longer be considered current
        val completedVisitA = visitA.copy(status = EventStatus.COMPLETED)
        val stateWithCompletedA = engine.computeState(
            baseTrip,
            listOf(journey, completedVisitA, visitB),
            LocalDateTime.of(2026, 8, 20, 12, 0)
        )
        assertNull("Completed Visit A must not be current at 12:00", stateWithCompletedA.currentEvent)
        assertEquals("Next event should be Visit B", 3L, stateWithCompletedA.nextEvent?.id)

        // After skipping Visit A: Visit B must become the next relevant event
        val skippedVisitA = visitA.copy(status = EventStatus.SKIPPED)
        val stateWithSkippedA = engine.computeState(
            baseTrip,
            listOf(journey, skippedVisitA, visitB),
            LocalDateTime.of(2026, 8, 20, 10, 30)
        )
        assertNull("Skipped Visit A must not be current at 10:30", stateWithSkippedA.currentEvent)
        assertEquals("Next event should be Visit B when Visit A is skipped", 3L, stateWithSkippedA.nextEvent?.id)
    }

    @Test
    fun testStayCheckIn_advancesToIntermediateActivitiesAndThenCheckOut() {
        val stay = Event(
            id = 10,
            tripId = 1,
            type = EventType.STAY,
            title = "Grand Palace Hotel",
            startTime = LocalDateTime.of(2026, 8, 20, 14, 0),
            endTime = LocalDateTime.of(2026, 8, 22, 11, 0),
            order = 1
        )
        val lunch = Event(
            id = 11,
            tripId = 1,
            type = EventType.FOOD,
            title = "Lunch at Spice Court",
            startTime = LocalDateTime.of(2026, 8, 20, 15, 30),
            endTime = LocalDateTime.of(2026, 8, 20, 16, 30),
            order = 2
        )
        val fortVisit = Event(
            id = 12,
            tripId = 1,
            type = EventType.VISIT,
            title = "Fort Tour",
            startTime = LocalDateTime.of(2026, 8, 21, 10, 0),
            endTime = LocalDateTime.of(2026, 8, 21, 13, 0),
            order = 3
        )
        val trainHome = Event(
            id = 13,
            tripId = 1,
            type = EventType.JOURNEY,
            title = "Train Home",
            startTime = LocalDateTime.of(2026, 8, 22, 14, 0),
            endTime = LocalDateTime.of(2026, 8, 22, 20, 0),
            order = 4
        )
        val itinerary = listOf(stay, lunch, fortVisit, trainHome)

        // 1. Before check-in (13:45): Current = null, Next = Stay (Check-in)
        val beforeCheckIn = engine.computeState(baseTrip, itinerary, LocalDateTime.of(2026, 8, 20, 13, 45))
        assertEquals(10L, beforeCheckIn.nextEvent?.id)

        // 2. User checks in at 14:00 (actualStartTime is set). At 14:15, Next = Lunch (15:30)
        val checkedInStay = stay.copy(actualStartTime = LocalDateTime.of(2026, 8, 20, 14, 0), status = EventStatus.ACTIVE)
        val afterCheckIn = engine.computeState(
            baseTrip,
            listOf(checkedInStay, lunch, fortVisit, trainHome),
            LocalDateTime.of(2026, 8, 20, 14, 15)
        )
        assertNull("Stay should yield currentEvent when checked in and intermediate activities are pending", afterCheckIn.currentEvent)
        assertEquals("Next up should be Lunch after checking in to hotel", 11L, afterCheckIn.nextEvent?.id)

        // 3. During Lunch (15:45): Current = Lunch, Next = Fort Tour
        val duringLunch = engine.computeState(
            baseTrip,
            listOf(checkedInStay, lunch, fortVisit, trainHome),
            LocalDateTime.of(2026, 8, 20, 15, 45)
        )
        assertEquals("Current event should be Lunch, not Stay", 11L, duringLunch.currentEvent?.id)
        assertEquals("Next event should be Fort Tour", 12L, duringLunch.nextEvent?.id)

        // 4. After all intermediate activities are completed on Day 22 morning (10:45): Stay becomes current for Check-out!
        val completedLunch = lunch.copy(status = EventStatus.COMPLETED)
        val completedFort = fortVisit.copy(status = EventStatus.COMPLETED)
        val atCheckOutTime = engine.computeState(
            baseTrip,
            listOf(checkedInStay, completedLunch, completedFort, trainHome),
            LocalDateTime.of(2026, 8, 22, 10, 45)
        )
        assertEquals("Stay should be currentEvent for Check-out when intermediate events are done", 10L, atCheckOutTime.currentEvent?.id)
        assertEquals("Next event should be Train Home", 13L, atCheckOutTime.nextEvent?.id)

        // 5. After user checks out (actualEndTime set, status = COMPLETED): Next Up is Train Home!
        val checkedOutStay = checkedInStay.copy(
            actualEndTime = LocalDateTime.of(2026, 8, 22, 10, 50),
            status = EventStatus.COMPLETED
        )
        val afterCheckOut = engine.computeState(
            baseTrip,
            listOf(checkedOutStay, completedLunch, completedFort, trainHome),
            LocalDateTime.of(2026, 8, 22, 11, 0)
        )
        assertNull(afterCheckOut.currentEvent)
        assertEquals("Next event should be Train Home after checkout", 13L, afterCheckOut.nextEvent?.id)
    }
}
