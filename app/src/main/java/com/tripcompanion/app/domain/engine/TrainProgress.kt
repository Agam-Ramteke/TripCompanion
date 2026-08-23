package com.tripcompanion.app.domain.engine

import com.tripcompanion.app.domain.model.TrainStopStatus
import java.time.LocalTime

/**
 * The arithmetic behind the live-tracking screen.
 *
 * Pure functions over a route, for the same reason [TripStateEngine] is pure: these are the
 * numbers most likely to be quietly wrong, and a wrong progress bar or a fabricated speed is
 * read as fact by someone standing on a platform. Keeping them out of the provider means they
 * can be tested against real route shapes — before departure, mid-run, overnight, terminated —
 * without a network.
 *
 * Two rules run through all of it:
 *
 * - **Distance is the only measure of progress.** Counting stations makes the bar jump,
 *   because stops are not evenly spaced; counting time makes it lie, because a train standing
 *   still still burns clock. The API reports cumulative kilometres, so kilometres it is.
 * - **Nothing is invented.** Every function here returns null or zero rather than an
 *   estimate when the inputs do not support an answer, and the screens render an em dash.
 */
object TrainProgress {

    private const val MINUTES_PER_DAY = 24 * 60

    /**
     * How far along the route the train is, 0f to 1f.
     *
     * Uses the current station's cumulative distance over the terminus'. Returns 0f when the
     * route carries no distances at all — some schedules do not — because a bar stuck at zero
     * reads as "not started", which is at least a defensible thing to say, whereas a bar
     * derived from station counts reads as precision that isn't there.
     */
    fun progressFraction(stops: List<TrainStopStatus>): Float {
        if (stops.isEmpty()) return 0f
        val total = stops.maxOf { it.distanceKm }
        if (total <= 0) return 0f
        if (stops.all { it.isDeparted }) return 1f

        // Prefer the station the provider itself calls current. Falling back to the furthest
        // departed station matters for projections, which have no notion of "current".
        val covered = stops.firstOrNull { it.isCurrent }?.distanceKm
            ?: stops.filter { it.isDeparted }.maxOfOrNull { it.distanceKm }
            ?: 0

        return (covered.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    /** [TrainStopStatus.serialNo] of the last station left behind, or 0 before departure. */
    fun lastDepartedSerial(stops: List<TrainStopStatus>): Int =
        stops.filter { it.isDeparted }.maxOfOrNull { it.serialNo } ?: 0

    /**
     * The station the train is heading for.
     *
     * The first stop it has not departed — which is the station it is standing at when it is
     * standing at one, and that is the right answer: "next stop" on a platform display means
     * the next place the doors open, not the one after this.
     */
    fun nextStop(stops: List<TrainStopStatus>): TrainStopStatus? =
        stops.sortedBy { it.serialNo }.firstOrNull { !it.isDeparted }

    /**
     * When the train is expected at [stop].
     *
     * A reported actual time wins outright. Otherwise the scheduled arrival is pushed back by
     * the delay the train is already carrying, which is the assumption every station display
     * makes: a train 20 minutes down stays 20 minutes down until something changes.
     *
     * Null when the stop has no scheduled arrival — the origin, where there is nothing to be
     * early or late for.
     */
    fun etaFor(stop: TrainStopStatus, delayMinutes: Int): LocalTime? {
        stop.actualArrival?.let { return it }
        val scheduled = stop.scheduledArrival ?: return null
        return scheduled.plusMinutes(delayMinutes.toLong())
    }

    /**
     * How late the train is, in minutes, right now.
     *
     * Taken from the current station if one is flagged, otherwise from the last station it
     * left. Zero when nothing has been reported — a train with no delay information is shown
     * as on time, because that is the railway's own default and it is what the timetable says.
     */
    fun currentDelayMinutes(stops: List<TrainStopStatus>): Int {
        val current = stops.firstOrNull { it.isCurrent }?.delayMinutes
        if (current != null) return current
        return stops.filter { it.isDeparted }
            .maxByOrNull { it.serialNo }
            ?.delayMinutes
            ?: 0
    }

    /**
     * Average speed over the ground already covered, or null when it cannot be worked out.
     *
     * Needs two reported times and a distance between them, so it is null before the second
     * station and stays null on routes with no distance data. Deliberately *average* and
     * labelled as such: the API reports no instantaneous speed, and presenting a two-station
     * average as "speed" would be a guess dressed as a reading.
     *
     * Times are folded through [TrainStopStatus.dayOffset] before subtracting, so an
     * overnight run does not come out as a negative elapsed time and a 900 km/h train.
     */
    fun averageSpeedKmph(stops: List<TrainStopStatus>): Double? {
        val points = stops
            .sortedBy { it.serialNo }
            .mapNotNull { stop ->
                val reported = stop.actualDeparture ?: stop.actualArrival ?: return@mapNotNull null
                stop.distanceKm to (stop.dayOffset * MINUTES_PER_DAY + reported.hour * 60 + reported.minute)
            }
        if (points.size < 2) return null

        val (firstKm, firstMinute) = points.first()
        val (lastKm, lastMinute) = points.last()
        val km = lastKm - firstKm
        val minutes = lastMinute - firstMinute
        if (km <= 0 || minutes <= 0) return null

        return km * 60.0 / minutes
    }
}
