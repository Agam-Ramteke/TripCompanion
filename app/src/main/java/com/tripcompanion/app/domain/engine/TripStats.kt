package com.tripcompanion.app.domain.engine

import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.Trip
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The numbers on Home's four stat cards, and on New Trip's "5 Days / 4 Nights" line.
 *
 * Every field is counted from stored rows. That is the whole point of the class: a stat card
 * showing a constant is a lie that survives every refactor, so there is one place that does
 * the counting and no screen is allowed to do its own.
 *
 * Pure and clock-injected, like [TripStateEngine] — [compute] takes `today` rather than
 * calling [LocalDate.now], so "day 3 of 5" is testable at any point in a trip.
 */
data class TripStats(
    /** Calendar days the trip spans, inclusive of both ends. Zero when there is no trip. */
    val totalDays: Int = 0,
    /**
     * Which day of the trip [today] is, 1-based.
     *
     * Zero before the trip starts and clamped to [totalDays] after it ends, so a screen can
     * render "Day 3 of 5" without bounds-checking and never shows "Day 9 of 5".
     */
    val dayNumber: Int = 0,
    val activityCount: Int = 0,
    val completedCount: Int = 0,
    /** Distinct locations the itinerary visits — not every location in the database. */
    val placeCount: Int = 0,
    val photoPlanCount: Int = 0
) {
    /** Nights between the days. A one-day trip has none, so this floors at zero. */
    val nightCount: Int get() = (totalDays - 1).coerceAtLeast(0)

    /** Completed over total, 0f when there is nothing planned. Never divides by zero. */
    val completionFraction: Float
        get() = if (activityCount > 0) completedCount.toFloat() / activityCount else 0f

    val remainingCount: Int get() = (activityCount - completedCount).coerceAtLeast(0)

    /** `"5 Days · 4 Nights"`, the phrase the brief puts under a trip's date range. */
    val durationLabel: String
        get() = when {
            totalDays <= 0 -> ""
            totalDays == 1 -> "1 Day"
            else -> "$totalDays Days · $nightCount ${if (nightCount == 1) "Night" else "Nights"}"
        }

    companion object {

        /**
         * Counts a trip.
         *
         * [events] should be the engine's `eventsWithComputedStatus` where a caller has it, so
         * that a completed count matches the badges on screen. Raw rows also work — the only
         * status this reads is [EventStatus.COMPLETED], which is stored rather than derived.
         *
         * [photoCount] comes from the photo repository, which is keyed by event rather than by
         * trip, so it is passed in instead of counted here.
         */
        fun compute(
            trip: Trip?,
            events: List<Event>,
            photoCount: Int,
            today: LocalDate
        ): TripStats {
            if (trip == null) return TripStats(photoPlanCount = photoCount)

            val totalDays = dayCount(trip.startDate, trip.endDate)
            val elapsed = ChronoUnit.DAYS.between(trip.startDate, today).toInt() + 1

            return TripStats(
                totalDays = totalDays,
                dayNumber = elapsed.coerceIn(0, totalDays),
                activityCount = events.size,
                completedCount = events.count { it.status == EventStatus.COMPLETED },
                placeCount = events.mapNotNull { it.locationId }.distinct().size,
                photoPlanCount = photoCount
            )
        }

        /** Inclusive day span. A trip that starts and ends on the same date lasts one day. */
        private fun dayCount(start: LocalDate, end: LocalDate): Int =
            (ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceAtLeast(1)
    }
}
