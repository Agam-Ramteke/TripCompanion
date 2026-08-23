package com.tripcompanion.app.core.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Every date and time the user reads passes through here (§9).
 *
 * Two rules drive the whole file. Trip planning happens at minute resolution, so
 * seconds and nanoseconds are not merely hidden but stripped — a raw
 * `2026-08-21T00:00:00.517484` must never reach a screen. And the separator in a
 * time range is an en dash, because that is what a range is set with.
 */
object DateTimeUtils {

    /** En dash, per §9. Not a hyphen and not an em dash. */
    private const val RANGE_DASH = "–"

    private val FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy")
    private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM")
    private val SHORT_DATE_YEAR_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy")
    private val DAY_AND_DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, dd MMMM")
    private val WEEKDAY_SHORT_FORMATTER = DateTimeFormatter.ofPattern("EEE")
    private val DAY_OF_MONTH_FORMATTER = DateTimeFormatter.ofPattern("dd")
    private val MONTH_AND_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy")
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

    /** "21 August 2026" */
    fun formatFullDate(date: LocalDate): String = date.format(FULL_DATE_FORMATTER)

    /** "21 Aug" */
    fun formatShortDate(date: LocalDate): String = date.format(SHORT_DATE_FORMATTER)

    /** "Friday, 21 August" */
    fun formatDayAndDate(date: LocalDate): String = date.format(DAY_AND_DATE_FORMATTER)

    /** "Fri" */
    fun formatWeekdayShort(date: LocalDate): String = date.format(WEEKDAY_SHORT_FORMATTER)

    /**
     * A trip's dates on one line: "03 – 06 November 2026".
     *
     * The repeated part is said once. Writing "03 November 2026 – 06 November 2026" on a
     * card forces the reader to diff two nearly identical strings to find the two digits
     * that actually differ, and most trips sit inside a single month. The month is only
     * repeated when it changes ("28 Nov – 02 Dec 2026") and the year only when it does
     * ("28 Dec 2026 – 02 Jan 2027"), so the longer forms are a signal in themselves.
     */
    fun formatDateRange(start: LocalDate, end: LocalDate): String = when {
        start == end -> formatFullDate(start)

        start.year == end.year && start.month == end.month ->
            start.format(DAY_OF_MONTH_FORMATTER) + " $RANGE_DASH " +
                end.format(DAY_OF_MONTH_FORMATTER) + " " +
                start.format(MONTH_AND_YEAR_FORMATTER)

        start.year == end.year ->
            "${formatShortDate(start)} $RANGE_DASH ${formatShortDate(end)} ${start.year}"

        else ->
            start.format(SHORT_DATE_YEAR_FORMATTER) + " $RANGE_DASH " +
                end.format(SHORT_DATE_YEAR_FORMATTER)
    }

    /**
     * How many days a trip covers, counting both ends: 3 Nov to 6 Nov is 4 days.
     *
     * Inclusive because that is what a traveller means by "a four day trip" — the
     * exclusive count would call it three and disagree with the number of days the
     * timeline actually draws.
     */
    fun dayCount(start: LocalDate, end: LocalDate): Int =
        (ChronoUnit.DAYS.between(start, end) + 1L).coerceAtLeast(1L).toInt()

    /** "08:30" — 24-hour, no seconds, ever. */
    fun formatTime(time: LocalTime): String = time.format(TIME_FORMATTER)

    /** "08:30" */
    fun formatTime(dateTime: LocalDateTime): String = dateTime.toLocalTime().format(TIME_FORMATTER)

    /** "08:30 – 10:15" */
    fun formatTimeRange(startTime: LocalTime, endTime: LocalTime): String =
        "${formatTime(startTime)} $RANGE_DASH ${formatTime(endTime)}"

    /**
     * "08:30 – 10:15", or "23:00 – 01:30 (+1 day)" when the event runs past
     * midnight. The suffix is the only honest way to render an overnight range on
     * one line: without it "23:00 – 01:30" reads as a negative duration.
     */
    fun formatTimeRange(start: LocalDateTime, end: LocalDateTime): String {
        val baseRange = "${formatTime(start)} $RANGE_DASH ${formatTime(end)}"
        val dayDelta = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate())
        return when {
            dayDelta <= 0L -> baseRange
            dayDelta == 1L -> "$baseRange (+1 day)"
            else -> "$baseRange (+$dayDelta days)"
        }
    }

    /** "21 Aug • 08:30" */
    fun formatDateTime(dateTime: LocalDateTime): String =
        "${formatShortDate(dateTime.toLocalDate())} • ${formatTime(dateTime.toLocalTime())}"

    /**
     * A span of time as "45 min", "1 h 20 m", "3 h", "2 d 4 h".
     *
     * Units are shortened because these land in tight places — a spine label, a
     * chip beside a title — and a duration that wraps to two lines is worse than
     * one that reads slightly terse.
     */
    fun formatDuration(start: LocalDateTime, end: LocalDateTime): String =
        formatMinutes(ChronoUnit.MINUTES.between(start, end))

    /** [formatDuration] from a raw minute count. Negative spans clamp to "0 min". */
    fun formatMinutes(totalMinutes: Long): String {
        if (totalMinutes <= 0L) return "0 min"
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0L && hours > 0L -> "$days d $hours h"
            days > 0L -> "$days d"
            hours > 0L && minutes > 0L -> "$hours h $minutes m"
            hours > 0L -> "$hours h"
            else -> "$minutes min"
        }
    }

    /**
     * How long until something starts, phrased for a countdown: "now",
     * "in 18 min", "in 1 h 20 m", "in 2 d 4 h".
     *
     * Anything already begun returns "now" rather than a negative number — the
     * caller asking this question is showing a *next up* label, and the state
     * engine is what decides whether the thing is actually running.
     */
    fun formatTimeUntil(now: LocalDateTime, target: LocalDateTime): String {
        val minutes = ChronoUnit.MINUTES.between(now, target)
        return if (minutes <= 0L) "now" else "in ${formatMinutes(minutes)}"
    }

    /** How much of a running event is left: "1 h 20 m left", or "ending now". */
    fun formatTimeRemaining(now: LocalDateTime, end: LocalDateTime): String {
        val minutes = ChronoUnit.MINUTES.between(now, end)
        return if (minutes <= 0L) "ending now" else "${formatMinutes(minutes)} left"
    }

    /**
     * How far through a span we are, as 0f..1f. Returns 0f for a zero or
     * inverted span so callers never divide by zero or draw a backwards bar.
     */
    fun progressFraction(start: LocalDateTime, end: LocalDateTime, now: LocalDateTime): Float {
        val total = Duration.between(start, end).toMinutes()
        if (total <= 0L) return 0f
        val elapsed = Duration.between(start, now).toMinutes()
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Drops seconds and nanoseconds. Called on every value entering persistence
     * so that equality, sorting and duration maths can never be thrown off by
     * sub-minute jitter the user never asked for and cannot see.
     */
    fun normalizeToMinutes(dateTime: LocalDateTime): LocalDateTime =
        dateTime.truncatedTo(ChronoUnit.MINUTES)

    /** Material 3's DatePicker hands back UTC millis; convert without shifting the day. */
    fun epochMillisToLocalDate(utcMillis: Long): LocalDate =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    /** The inverse, for seeding a DatePicker. */
    fun localDateToEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
