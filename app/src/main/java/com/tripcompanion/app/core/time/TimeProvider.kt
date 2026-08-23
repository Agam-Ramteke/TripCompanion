package com.tripcompanion.app.core.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The source of "now".
 *
 * The state engine is a pure function of time and events (§21), which only holds
 * if time is an *input*. Calling `LocalDateTime.now()` inside a ViewModel breaks
 * that twice over: the value is captured once and then never advances, so an
 * event silently never turns ACTIVE while the screen is open; and there is no way
 * to ask what the app looks like at 14:05 tomorrow, so five of the six statuses
 * are unreachable in a test.
 *
 * Injecting this fixes both. Production uses [SystemTimeProvider]; tests use
 * [FixedTimeProvider] and drive the clock by hand.
 */
interface TimeProvider {
    /** The current time, at minute resolution — seconds and nanos are never present (§9). */
    fun now(): LocalDateTime

    /**
     * Emits [now] immediately, then again on every minute boundary.
     *
     * Aligned to the boundary rather than every 60s from subscription, so 09:59 →
     * 10:00 flips as the minute turns instead of up to 59 seconds late.
     */
    fun ticker(): Flow<LocalDateTime>
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {

    override fun now(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)

    override fun ticker(): Flow<LocalDateTime> = flow {
        while (true) {
            emit(now())
            val wallClock = LocalDateTime.now()
            val nextMinute = wallClock.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(ChronoUnit.MILLIS.between(wallClock, nextMinute).coerceAtLeast(1L))
        }
    }
}

/**
 * A clock that only moves when told to. Lets a test place the app at any instant
 * — mid-event, thirty-one minutes before a start, a day after the trip ended —
 * and assert what the engine computes there.
 */
class FixedTimeProvider(initial: LocalDateTime) : TimeProvider {

    private val state = MutableStateFlow(initial.truncatedTo(ChronoUnit.MINUTES))

    override fun now(): LocalDateTime = state.value

    override fun ticker(): Flow<LocalDateTime> = state.asStateFlow()

    /** Jump to an instant. */
    fun setNow(value: LocalDateTime) {
        state.value = value.truncatedTo(ChronoUnit.MINUTES)
    }

    /** Move forward, for asserting a transition rather than a single frame. */
    fun advanceMinutes(minutes: Long) {
        state.value = state.value.plusMinutes(minutes)
    }
}
