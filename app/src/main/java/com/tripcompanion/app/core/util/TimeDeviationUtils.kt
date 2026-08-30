package com.tripcompanion.app.core.util

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Calculates and formats timing deviations between scheduled and actual itinerary events.
 *
 * Semantic rules:
 * - A difference of > 2 minutes is "late" (e.g. "6 min late", "17 min late").
 * - A difference of < -2 minutes is "early" (e.g. "7 min early").
 * - A difference within [-2, +2] minutes is "On time".
 */
object TimeDeviationUtils {

    /**
     * Positive if actual is after scheduled (late), negative if before (early).
     */
    fun calculateDeviationMinutes(scheduled: LocalDateTime, actual: LocalDateTime): Long =
        ChronoUnit.MINUTES.between(scheduled, actual)

    /**
     * Formats deviation as "6 min late", "7 min early", "2h early", "6d early", or "On time".
     */
    fun formatDeviation(scheduled: LocalDateTime, actual: LocalDateTime): String {
        val diff = calculateDeviationMinutes(scheduled, actual)
        val absDiff = abs(diff)
        return when {
            diff > 2L -> {
                if (absDiff >= 1440L) "${absDiff / 1440L}d late"
                else if (absDiff >= 60L) "${absDiff / 60L}h late"
                else "$diff min late"
            }
            diff < -2L -> {
                if (absDiff >= 1440L) "${absDiff / 1440L}d early"
                else if (absDiff >= 60L) "${absDiff / 60L}h early"
                else "$absDiff min early"
            }
            else -> "On time"
        }
    }

    /**
     * Formats a complete lifecycle badge:
     * e.g. "✓ Boarded · 15:31 · 6 min late"
     * e.g. "✓ Arrived · 04:07 · 17 min late"
     */
    fun formatLifecycleBadge(
        actionLabel: String,
        actual: LocalDateTime,
        scheduled: LocalDateTime
    ): String {
        val timeStr = DateTimeUtils.formatTime(actual)
        val devStr = formatDeviation(scheduled, actual)
        return "✓ $actionLabel · $timeStr · $devStr"
    }
}
