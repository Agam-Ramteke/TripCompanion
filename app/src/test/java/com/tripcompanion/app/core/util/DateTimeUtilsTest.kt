package com.tripcompanion.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * §9 is a formatting contract, so these tests read it literally: the exact strings
 * the spec names, the en dash it asks for, and the boundaries where a naive
 * implementation flips sign — midnight crossings, zero-length spans, and a `now`
 * that has already passed the value it is being compared against.
 */
class DateTimeUtilsTest {

    /** U+2013. Written as an escape so a stray copy-paste can't swap it for a hyphen. */
    private val enDash = "–"

    // ---- Dates -------------------------------------------------------------

    @Test
    fun `formatFullDate spells the month out`() {
        assertEquals("21 August 2026", DateTimeUtils.formatFullDate(LocalDate.of(2026, 8, 21)))
    }

    @Test
    fun `formatShortDate abbreviates and drops the year`() {
        assertEquals("21 Aug", DateTimeUtils.formatShortDate(LocalDate.of(2026, 8, 21)))
    }

    @Test
    fun `formatDayAndDate leads with the weekday`() {
        assertEquals("Friday, 21 August", DateTimeUtils.formatDayAndDate(LocalDate.of(2026, 8, 21)))
    }

    @Test
    fun `formatWeekdayShort gives the three-letter day`() {
        assertEquals("Fri", DateTimeUtils.formatWeekdayShort(LocalDate.of(2026, 8, 21)))
        assertEquals("Mon", DateTimeUtils.formatWeekdayShort(LocalDate.of(2026, 8, 24)))
    }

    @Test
    fun `single-digit days are zero-padded so columns of dates line up`() {
        assertEquals("05 Aug", DateTimeUtils.formatShortDate(LocalDate.of(2026, 8, 5)))
    }

    // ---- Date ranges -------------------------------------------------------

    @Test
    fun `formatDateRange says the month once when the trip stays inside it`() {
        assertEquals(
            "03 $enDash 06 November 2026",
            DateTimeUtils.formatDateRange(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 6))
        )
    }

    @Test
    fun `formatDateRange repeats the month when the trip crosses one`() {
        assertEquals(
            "28 Nov $enDash 02 Dec 2026",
            DateTimeUtils.formatDateRange(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 2))
        )
    }

    @Test
    fun `formatDateRange repeats the year when the trip crosses one`() {
        assertEquals(
            "28 Dec 2026 $enDash 02 Jan 2027",
            DateTimeUtils.formatDateRange(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 2))
        )
    }

    @Test
    fun `a one-day trip is written as a single date, not a range of one`() {
        val day = LocalDate.of(2026, 11, 3)
        assertEquals("03 November 2026", DateTimeUtils.formatDateRange(day, day))
    }

    @Test
    fun `same month in different years is not collapsed`() {
        // The month matches but the year does not, so the short form would read
        // "03 – 06 November 2026" for a trip that actually spans a year.
        assertEquals(
            "03 Nov 2026 $enDash 06 Nov 2027",
            DateTimeUtils.formatDateRange(LocalDate.of(2026, 11, 3), LocalDate.of(2027, 11, 6))
        )
    }

    @Test
    fun `dayCount counts both ends`() {
        assertEquals(
            4,
            DateTimeUtils.dayCount(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 6))
        )
    }

    @Test
    fun `a trip that starts and ends on one day is one day long`() {
        val day = LocalDate.of(2026, 11, 3)
        assertEquals(1, DateTimeUtils.dayCount(day, day))
    }

    @Test
    fun `dayCount never returns zero or less for a backwards range`() {
        // The UI blocks saving these, but a row already in the database could hold
        // one, and a zero or negative day count would size the timeline to nothing.
        assertEquals(
            1,
            DateTimeUtils.dayCount(LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 3))
        )
    }

    @Test
    fun `dayCount counts a leap day`() {
        assertEquals(
            3,
            DateTimeUtils.dayCount(LocalDate.of(2028, 2, 28), LocalDate.of(2028, 3, 1))
        )
    }

    // ---- Times -------------------------------------------------------------

    @Test
    fun `formatTime is 24-hour and never shows seconds`() {
        assertEquals("08:30", DateTimeUtils.formatTime(LocalTime.of(8, 30)))
        assertEquals("00:00", DateTimeUtils.formatTime(LocalTime.of(0, 0)))
        assertEquals("23:59", DateTimeUtils.formatTime(LocalTime.of(23, 59)))
    }

    @Test
    fun `formatTime discards the seconds it is handed`() {
        val messy = LocalTime.of(8, 30, 45, 517_484_000)
        assertEquals("08:30", DateTimeUtils.formatTime(messy))
    }

    @Test
    fun `the LocalDateTime overload prints the time only`() {
        val moment = LocalDateTime.of(2026, 8, 21, 14, 5, 33)
        assertEquals("14:05", DateTimeUtils.formatTime(moment))
    }

    @Test
    fun `formatDateTime joins date and time with a bullet`() {
        val moment = LocalDateTime.of(2026, 8, 21, 8, 30)
        assertEquals("21 Aug • 08:30", DateTimeUtils.formatDateTime(moment))
    }

    // ---- Ranges ------------------------------------------------------------

    @Test
    fun `formatTimeRange separates with an en dash`() {
        val range = DateTimeUtils.formatTimeRange(LocalTime.of(8, 30), LocalTime.of(10, 15))
        assertEquals("08:30 $enDash 10:15", range)
    }

    @Test
    fun `the range separator is not a hyphen or an em dash`() {
        val range = DateTimeUtils.formatTimeRange(LocalTime.of(8, 30), LocalTime.of(10, 15))
        assertFalse("hyphen leaked into a time range", range.contains("-"))
        assertFalse("em dash leaked into a time range", range.contains("—"))
    }

    @Test
    fun `a same-day range carries no day suffix`() {
        val start = LocalDateTime.of(2026, 8, 21, 9, 0)
        val end = LocalDateTime.of(2026, 8, 21, 17, 30)
        assertEquals("09:00 $enDash 17:30", DateTimeUtils.formatTimeRange(start, end))
    }

    @Test
    fun `an overnight range says so, because otherwise it reads backwards`() {
        val start = LocalDateTime.of(2026, 8, 21, 23, 0)
        val end = LocalDateTime.of(2026, 8, 22, 1, 30)
        assertEquals("23:00 $enDash 01:30 (+1 day)", DateTimeUtils.formatTimeRange(start, end))
    }

    @Test
    fun `a multi-night stay pluralises the day suffix`() {
        val start = LocalDateTime.of(2026, 8, 21, 15, 0)
        val end = LocalDateTime.of(2026, 8, 24, 11, 0)
        assertEquals("15:00 $enDash 11:00 (+3 days)", DateTimeUtils.formatTimeRange(start, end))
    }

    @Test
    fun `crossing midnight exactly still counts as the next day`() {
        val start = LocalDateTime.of(2026, 8, 21, 22, 0)
        val end = LocalDateTime.of(2026, 8, 22, 0, 0)
        assertEquals("22:00 $enDash 00:00 (+1 day)", DateTimeUtils.formatTimeRange(start, end))
    }

    // ---- Durations ---------------------------------------------------------

    @Test
    fun `formatMinutes picks units by magnitude`() {
        assertEquals("45 min", DateTimeUtils.formatMinutes(45))
        assertEquals("1 h", DateTimeUtils.formatMinutes(60))
        assertEquals("1 h 20 m", DateTimeUtils.formatMinutes(80))
        assertEquals("3 h", DateTimeUtils.formatMinutes(180))
        assertEquals("1 d", DateTimeUtils.formatMinutes(24 * 60))
        assertEquals("1 d 1 h", DateTimeUtils.formatMinutes(25 * 60))
        assertEquals("2 d 4 h", DateTimeUtils.formatMinutes(52 * 60))
    }

    @Test
    fun `a day-and-a-bit drops the minutes rather than reading like a stopwatch`() {
        // 1 d 4 h 37 m. The minutes are noise at this scale and would push the
        // label past the width of the places it lands in.
        assertEquals("1 d 4 h", DateTimeUtils.formatMinutes(24 * 60 + 4 * 60 + 37))
    }

    @Test
    fun `zero and negative spans clamp instead of printing a minus sign`() {
        assertEquals("0 min", DateTimeUtils.formatMinutes(0))
        assertEquals("0 min", DateTimeUtils.formatMinutes(-30))
    }

    @Test
    fun `formatDuration measures the gap between two moments`() {
        val start = LocalDateTime.of(2026, 8, 21, 9, 0)
        val end = LocalDateTime.of(2026, 8, 21, 11, 45)
        assertEquals("2 h 45 m", DateTimeUtils.formatDuration(start, end))
    }

    @Test
    fun `formatDuration handles an overnight event`() {
        val start = LocalDateTime.of(2026, 8, 21, 23, 0)
        val end = LocalDateTime.of(2026, 8, 22, 2, 0)
        assertEquals("3 h", DateTimeUtils.formatDuration(start, end))
    }

    @Test
    fun `formatDuration ignores sub-minute noise`() {
        val start = LocalDateTime.of(2026, 8, 21, 9, 0, 59)
        val end = LocalDateTime.of(2026, 8, 21, 9, 30, 0)
        assertEquals("29 min", DateTimeUtils.formatDuration(start, end))
    }

    // ---- Countdowns --------------------------------------------------------

    @Test
    fun `formatTimeUntil counts forwards`() {
        val now = LocalDateTime.of(2026, 8, 21, 14, 20)
        assertEquals("in 40 min", DateTimeUtils.formatTimeUntil(now, now.plusMinutes(40)))
        assertEquals("in 1 h 20 m", DateTimeUtils.formatTimeUntil(now, now.plusMinutes(80)))
        assertEquals("in 2 d 4 h", DateTimeUtils.formatTimeUntil(now, now.plusMinutes(52 * 60)))
    }

    @Test
    fun `something already started reads as now, not as a negative countdown`() {
        val now = LocalDateTime.of(2026, 8, 21, 14, 20)
        assertEquals("now", DateTimeUtils.formatTimeUntil(now, now))
        assertEquals("now", DateTimeUtils.formatTimeUntil(now, now.minusMinutes(90)))
    }

    @Test
    fun `formatTimeRemaining counts down to the end`() {
        val now = LocalDateTime.of(2026, 8, 21, 14, 20)
        assertEquals("1 h 20 m left", DateTimeUtils.formatTimeRemaining(now, now.plusMinutes(80)))
        assertEquals("5 min left", DateTimeUtils.formatTimeRemaining(now, now.plusMinutes(5)))
    }

    @Test
    fun `an event at or past its end is ending now`() {
        val now = LocalDateTime.of(2026, 8, 21, 14, 20)
        assertEquals("ending now", DateTimeUtils.formatTimeRemaining(now, now))
        assertEquals("ending now", DateTimeUtils.formatTimeRemaining(now, now.minusMinutes(10)))
    }

    // ---- Progress ----------------------------------------------------------

    @Test
    fun `progressFraction spans zero to one across the event`() {
        val start = LocalDateTime.of(2026, 8, 21, 10, 0)
        val end = LocalDateTime.of(2026, 8, 21, 12, 0)

        assertEquals(0f, DateTimeUtils.progressFraction(start, end, start), 0.0001f)
        assertEquals(0.5f, DateTimeUtils.progressFraction(start, end, start.plusHours(1)), 0.0001f)
        assertEquals(1f, DateTimeUtils.progressFraction(start, end, end), 0.0001f)
    }

    @Test
    fun `progressFraction clamps outside the event`() {
        val start = LocalDateTime.of(2026, 8, 21, 10, 0)
        val end = LocalDateTime.of(2026, 8, 21, 12, 0)

        assertEquals(0f, DateTimeUtils.progressFraction(start, end, start.minusHours(3)), 0.0001f)
        assertEquals(1f, DateTimeUtils.progressFraction(start, end, end.plusHours(3)), 0.0001f)
    }

    @Test
    fun `a zero-length or inverted span reports no progress rather than dividing by zero`() {
        val moment = LocalDateTime.of(2026, 8, 21, 10, 0)

        assertEquals(0f, DateTimeUtils.progressFraction(moment, moment, moment), 0.0001f)
        assertEquals(
            0f,
            DateTimeUtils.progressFraction(moment, moment.minusHours(2), moment),
            0.0001f
        )
    }

    // ---- Normalisation and persistence boundaries --------------------------

    @Test
    fun `normalizeToMinutes strips seconds and nanoseconds`() {
        val raw = LocalDateTime.of(2026, 8, 21, 8, 30, 45, 517_484_000)
        val normalized = DateTimeUtils.normalizeToMinutes(raw)

        assertEquals(0, normalized.second)
        assertEquals(0, normalized.nano)
        assertEquals(8, normalized.hour)
        assertEquals(30, normalized.minute)
        assertEquals(LocalDate.of(2026, 8, 21), normalized.toLocalDate())
    }

    @Test
    fun `normalizeToMinutes truncates rather than rounding`() {
        // 08:30:59 is still 08:30. Rounding up would let a saved event start a
        // minute after the time the user typed.
        val raw = LocalDateTime.of(2026, 8, 21, 8, 30, 59, 999_999_999)
        assertEquals(LocalDateTime.of(2026, 8, 21, 8, 30), DateTimeUtils.normalizeToMinutes(raw))
    }

    @Test
    fun `normalizeToMinutes is idempotent`() {
        val once = DateTimeUtils.normalizeToMinutes(LocalDateTime.of(2026, 8, 21, 8, 30, 45))
        assertEquals(once, DateTimeUtils.normalizeToMinutes(once))
    }

    @Test
    fun `epoch millis round-trip without shifting the day`() {
        val date = LocalDate.of(2026, 8, 21)
        val millis = DateTimeUtils.localDateToEpochMillis(date)
        assertEquals(date, DateTimeUtils.epochMillisToLocalDate(millis))
    }

    @Test
    fun `the round trip survives month and year boundaries`() {
        listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2026, 12, 31)
        ).forEach { date ->
            val millis = DateTimeUtils.localDateToEpochMillis(date)
            assertEquals(date, DateTimeUtils.epochMillisToLocalDate(millis))
        }
    }

    // ---- Live Countdown ----------------------------------------------------

    @Test
    fun `formatLiveCountdown matches precision tiers`() {
        val target = LocalDateTime.of(2026, 9, 5, 15, 25, 0)

        // > 48 hours: "11 days to go"
        val elevenDaysBefore = target.minusDays(11)
        assertEquals("11 days to go", DateTimeUtils.formatLiveCountdown(target, elevenDaysBefore))

        val twoDaysBefore = target.minusHours(48)
        assertEquals("2 days to go", DateTimeUtils.formatLiveCountdown(target, twoDaysBefore))

        // 24–48 hours: "1d 7h 23m"
        val dayAndHalf = target.minusHours(31).minusMinutes(23)
        assertEquals("1d 7h 23m", DateTimeUtils.formatLiveCountdown(target, dayAndHalf))

        // < 24 hours: "18h 42m"
        val eighteenHours = target.minusHours(18).minusMinutes(42)
        assertEquals("18h 42m", DateTimeUtils.formatLiveCountdown(target, eighteenHours))

        // < 1 hour: "42m 18s"
        val fortyTwoMins = target.minusMinutes(42).minusSeconds(18)
        assertEquals("42m 18s", DateTimeUtils.formatLiveCountdown(target, fortyTwoMins))

        // At/after zero: "Trip starts now"
        assertEquals("Trip starts now", DateTimeUtils.formatLiveCountdown(target, target))
        assertEquals("Trip starts now", DateTimeUtils.formatLiveCountdown(target, target.plusSeconds(30)))
    }
}
