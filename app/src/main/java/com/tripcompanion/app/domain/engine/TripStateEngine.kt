package com.tripcompanion.app.domain.engine

import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.Trip
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Snapshot of the current trip state, computed deterministically from
 * the current time and the list of events with their statuses.
 * No GPS dependency — purely time + manual-override based.
 */
data class TripState(
    val trip: Trip?,
    val currentEvent: Event? = null,
    val nextEvent: Event? = null,
    val previousEvent: Event? = null,
    val currentTripDay: Int = 0,
    val totalTripDays: Int = 0,
    val currentTripState: TripPhase = TripPhase.NOT_STARTED,
    val eventsWithComputedStatus: List<Event> = emptyList(),
    val completedEventCount: Int = 0,
    val totalEventCount: Int = 0,
    val progressPercent: Float = 0f
)

enum class TripPhase {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

/**
 * Pure function engine — no Android dependencies.
 * Takes current time + events + trip and produces a deterministic TripState.
 *
 * Rules:
 * - Chronological sorting: startTime ASC, order ASC, id ASC.
 * - COMPLETED/SKIPPED events are terminal — never recalculated.
 * - If now is before event start: UPCOMING (or STARTING_SOON if within 30 min).
 * - If now is within [startTime, endTime]: ACTIVE.
 * - If now is after endTime and status is still UPCOMING: MISSED.
 * - currentEvent = first ACTIVE event chronologically.
 * - nextEvent = first UPCOMING/STARTING_SOON event chronologically after currentEvent.
 * - previousEvent = last COMPLETED/SKIPPED/MISSED event before currentEvent chronologically.
 */
class TripStateEngine {

    companion object {
        /** How many minutes before start time an event becomes STARTING_SOON */
        const val STARTING_SOON_MINUTES = 30L

        /** Single deterministic comparator for all event itinerary sorting */
        val EVENT_CHRONOLOGICAL_COMPARATOR = compareBy<Event> { it.startTime }
            .thenBy { it.order }
            .thenBy { it.id }
    }

    fun computeState(
        trip: Trip?,
        events: List<Event>,
        now: LocalDateTime
    ): TripState {
        if (trip == null) {
            return TripState(trip = null)
        }

        val sortedEvents = events.sortedWith(EVENT_CHRONOLOGICAL_COMPARATOR)

        // Compute effective status for each event
        val computedEvents = sortedEvents.map { event ->
            event.copy(status = computeEventStatus(event, now))
        }

        val currentEvent = computedEvents.firstOrNull { it.status == EventStatus.ACTIVE }
        val nextEvent = if (currentEvent != null) {
            val currentIndex = computedEvents.indexOfFirst { it.id == currentEvent.id }
            computedEvents.drop(currentIndex + 1).firstOrNull {
                it.status == EventStatus.UPCOMING || it.status == EventStatus.STARTING_SOON
            }
        } else {
            computedEvents.firstOrNull {
                it.status == EventStatus.UPCOMING || it.status == EventStatus.STARTING_SOON
            }
        }

        // Previous = last event before the current/next that is in a terminal or past state
        val referenceIndex = when {
            currentEvent != null -> computedEvents.indexOfFirst { it.id == currentEvent.id }
            nextEvent != null -> computedEvents.indexOfFirst { it.id == nextEvent.id }
            else -> computedEvents.size
        }

        val previousEvent = if (referenceIndex > 0) {
            computedEvents.take(referenceIndex).lastOrNull {
                it.status == EventStatus.COMPLETED ||
                    it.status == EventStatus.SKIPPED ||
                    it.status == EventStatus.MISSED
            }
        } else {
            null
        }

        val currentTripDay = computeTripDay(trip.startDate, now.toLocalDate())
        val totalTripDays = computeTotalDays(trip.startDate, trip.endDate)

        val completedCount = computedEvents.count { it.status == EventStatus.COMPLETED }
        val totalCount = computedEvents.size
        val progressPercent = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

        val tripPhase = when {
            trip.status == com.tripcompanion.app.domain.model.TripStatus.CANCELLED -> TripPhase.CANCELLED
            now.toLocalDate() < trip.startDate -> TripPhase.NOT_STARTED
            now.toLocalDate() > trip.endDate -> TripPhase.COMPLETED
            computedEvents.all {
                it.status == EventStatus.COMPLETED ||
                    it.status == EventStatus.SKIPPED ||
                    it.status == EventStatus.MISSED
            } && computedEvents.isNotEmpty() -> TripPhase.COMPLETED
            else -> TripPhase.IN_PROGRESS
        }

        return TripState(
            trip = trip,
            currentEvent = currentEvent,
            nextEvent = nextEvent,
            previousEvent = previousEvent,
            currentTripDay = currentTripDay,
            totalTripDays = totalTripDays,
            currentTripState = tripPhase,
            eventsWithComputedStatus = computedEvents,
            completedEventCount = completedCount,
            totalEventCount = totalCount,
            progressPercent = progressPercent
        )
    }

    /**
     * Compute the effective status of a single event.
     * Manual overrides (COMPLETED, SKIPPED) are preserved as-is.
     */
    fun computeEventStatus(event: Event, now: LocalDateTime): EventStatus {
        // Terminal states are never recalculated
        if (event.status == EventStatus.COMPLETED ||
            event.status == EventStatus.SKIPPED
        ) {
            return event.status
        }

        return when {
            now.isAfter(event.endTime) -> EventStatus.MISSED
            now.isAfter(event.startTime) || now.isEqual(event.startTime) -> EventStatus.ACTIVE
            now.isAfter(event.startTime.minusMinutes(STARTING_SOON_MINUTES)) -> EventStatus.STARTING_SOON
            else -> EventStatus.UPCOMING
        }
    }

    private fun computeTripDay(startDate: LocalDate, today: LocalDate): Int {
        val daysBetween = ChronoUnit.DAYS.between(startDate, today).toInt()
        return if (daysBetween < 0) 0 else daysBetween + 1
    }

    private fun computeTotalDays(startDate: LocalDate, endDate: LocalDate): Int {
        return ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
    }
}
