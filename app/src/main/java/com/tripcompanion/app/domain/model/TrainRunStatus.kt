package com.tripcompanion.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Where a train is right now, as of [fetchedAt].
 *
 * A *snapshot*, deliberately. Every screen that shows running status also shows how old the
 * status is, because "20 minutes late" and "20 minutes late, as of an hour ago" are
 * different facts and only one of them is worth acting on. Nothing in the app treats this
 * as current without checking [fetchedAt].
 *
 * [source] is not decoration either. A projection is arithmetic on the timetable, not
 * observation, and the screen says so — showing a derived position as though a railway had
 * reported it is the kind of confident wrongness that makes someone miss a train.
 */
data class TrainRunStatus(
    /** Zero when a provider has built a snapshot that is not yet attached to a train. */
    val trainId: Long = 0,
    val fetchedAt: LocalDateTime,
    /** The day of the run this describes. A daily train has a different run every day. */
    val runDate: LocalDate,
    val source: TrainRunSource,
    val currentStationCode: String = "",
    val currentStationName: String = "",
    /** Positive is late, negative is early, zero is on time. */
    val delayMinutes: Int = 0,
    /** [TrainStop.serialNo] of the last station the train has left. Zero before departure. */
    val lastDepartedSerial: Int = 0,
    /** 0f at the origin, 1f at the terminus. Derived from distance covered, never invented. */
    val progressFraction: Float = 0f,
    val nextStopCode: String = "",
    val nextStopName: String = "",
    val nextStopEta: LocalTime? = null,
    /**
     * Average speed over the distance run so far, or null when it cannot be worked out —
     * which is most of the time, because it needs two actual departure times. Screens render
     * an em dash for null rather than a zero.
     */
    val averageSpeedKmph: Double? = null,
    /** Whatever the provider said about the run in words. Often empty. */
    val message: String = "",
    val stops: List<TrainStopStatus> = emptyList()
) {
    val hasStarted: Boolean get() = lastDepartedSerial > 0
    val hasArrived: Boolean get() = progressFraction >= 1f

    /** Whether the delay is worth colouring red. Railways treat under 5 minutes as on time. */
    val isDelayed: Boolean get() = delayMinutes >= 5

    /** The station the train is heading for, if the route is known. */
    fun nextStop(): TrainStopStatus? = stops.firstOrNull { !it.isDeparted }
}

/** How a [TrainRunStatus] was arrived at. */
enum class TrainRunSource {
    /** Reported by a live-tracking API. */
    LIVE,

    /** Computed from the stored timetable, the clock and a user-entered delay. */
    PROJECTED
}

/**
 * One station's actuals within a [TrainRunStatus].
 *
 * Every scheduled/actual pair is nullable and independently so: a station the train has not
 * reached has no actual arrival, and a station it has not left has no actual departure. The
 * scheduled halves are copied in from [TrainStop] so a route can be rendered from this list
 * alone without joining back to the timetable.
 */
data class TrainStopStatus(
    val serialNo: Int,
    val stationCode: String,
    val stationName: String,
    val scheduledArrival: LocalTime? = null,
    val actualArrival: LocalTime? = null,
    val scheduledDeparture: LocalTime? = null,
    val actualDeparture: LocalTime? = null,
    val arrivalDelayMinutes: Int? = null,
    val departureDelayMinutes: Int? = null,
    val distanceKm: Int = 0,
    val dayOffset: Int = 0,
    /** True once the train has left. The first false stop is where the train is heading. */
    val isDeparted: Boolean = false,
    /** The single station the provider calls the train's current position. */
    val isCurrent: Boolean = false
) {
    /** The delay that matters at this stop: departure if it has one, else arrival. */
    val delayMinutes: Int?
        get() = departureDelayMinutes ?: arrivalDelayMinutes
}
