package com.tripcompanion.app.data.network

import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.engine.TrainProgress
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.model.TrainStopStatus
import com.tripcompanion.app.domain.service.TrainStatusError
import com.tripcompanion.app.domain.service.TrainStatusException
import com.tripcompanion.app.domain.service.TrainStatusProvider
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the train should be, worked out from its timetable and the clock.
 *
 * This is what runs when there is no API key, and what would run if the vendor were swapped
 * out tomorrow. It is not live tracking and never claims to be: every snapshot it produces is
 * stamped [TrainRunSource.PROJECTED], and the screens say so in words.
 *
 * What it can honestly tell you, offline, on a train with no signal:
 *
 * - which station the train has most recently left, and which one is next
 * - how far along the route that is, by kilometres
 * - when the next station is due, pushed back by whatever delay the user has entered
 *
 * What it deliberately refuses to produce: an actual arrival or departure time at any station,
 * and therefore any speed at all. Those come from observation, and this class observes nothing.
 * [TrainProgress.averageSpeedKmph] needs a reported time and finds none, so speed comes out
 * null and the screen shows an em dash rather than a plausible number.
 *
 * The timetable wins over the booking where they disagree. If the stored schedule says the
 * train leaves at 22:40 and the user typed 22:30, the projection follows the schedule — a
 * timetable is a published fact and the field on the booking is a typed one.
 */
@Singleton
class ScheduleProjectionTrainStatusProvider @Inject constructor(
    private val timeProvider: TimeProvider
) : TrainStatusProvider {

    override val providerName: String = PROVIDER_NAME

    override val isLive: Boolean = false

    override suspend fun fetchStatus(train: Train, schedule: List<TrainStop>): TrainRunStatus {
        if (schedule.isEmpty()) {
            throw TrainStatusException(
                TrainStatusError.NO_SCHEDULE,
                "No stored timetable to project from"
            )
        }

        val now = timeProvider.now()
        val runDate = train.departureTime.toLocalDate()
        val delay = train.knownDelayMinutes
        val ordered = schedule.sortedBy { it.serialNo }

        val stops = ordered.map { stop ->
            val arrival = stop.scheduledArrival?.let { at(runDate, stop.dayOffset, it, delay) }
            val departure = stop.scheduledDeparture?.let { at(runDate, stop.dayOffset, it, delay) }

            // Departed once the train is due to have left — or, at the terminus where there is
            // no departure, once it is due to have arrived. A run that has reached its last
            // station is over, and the progress bar should read full rather than one stop short.
            val reference = departure ?: arrival
            val isDeparted = reference != null && !now.isBefore(reference)

            // Standing at the platform: arrived and not yet due out. Only a halt counts, so
            // between stations no stop is current and progress rests on the last one left.
            val isCurrent = arrival != null &&
                departure != null &&
                !now.isBefore(arrival) &&
                now.isBefore(departure)

            TrainStopStatus(
                serialNo = stop.serialNo,
                stationCode = stop.stationCode,
                stationName = stop.stationName,
                scheduledArrival = stop.scheduledArrival,
                // No actual times. A projection has not seen the train.
                actualArrival = null,
                scheduledDeparture = stop.scheduledDeparture,
                actualDeparture = null,
                arrivalDelayMinutes = delay.takeIf { stop.scheduledArrival != null },
                departureDelayMinutes = delay.takeIf { stop.scheduledDeparture != null },
                distanceKm = stop.distanceKm,
                dayOffset = stop.dayOffset,
                isDeparted = isDeparted,
                isCurrent = isCurrent
            )
        }

        val current = stops.firstOrNull { it.isCurrent }
        val lastDeparted = stops.filter { it.isDeparted }.maxByOrNull { it.serialNo }
        val next = TrainProgress.nextStop(stops)

        return TrainRunStatus(
            trainId = train.id,
            fetchedAt = now,
            runDate = runDate,
            source = TrainRunSource.PROJECTED,
            currentStationCode = current?.stationCode ?: lastDeparted?.stationCode ?: "",
            currentStationName = current?.stationName ?: lastDeparted?.stationName ?: "",
            delayMinutes = delay,
            lastDepartedSerial = TrainProgress.lastDepartedSerial(stops),
            progressFraction = TrainProgress.progressFraction(stops),
            nextStopCode = next?.stationCode ?: "",
            nextStopName = next?.stationName ?: "",
            nextStopEta = next?.let { TrainProgress.etaFor(it, delay) },
            // Left null rather than derived: see the class comment.
            averageSpeedKmph = null,
            message = "",
            stops = stops
        )
    }

    /**
     * Null, always: a timetable is this provider's input, not its output.
     *
     * Reported as [com.tripcompanion.app.domain.service.TrainScheduleOutcome.Unsupported] rather
     * than an error, so the screen can offer to enter the route by hand instead of showing a
     * retry button for a request that will never be made.
     */
    override suspend fun fetchSchedule(trainNumber: String): List<TrainStop>? = null

    /**
     * A timetable time placed on the calendar.
     *
     * [dayOffset] is what makes an overnight run work: a 05:15 arrival on day two is nineteen
     * hours after a 22:40 departure on day one, not five hours before it.
     */
    private fun at(
        runDate: LocalDate,
        dayOffset: Int,
        time: LocalTime,
        delayMinutes: Int
    ): LocalDateTime =
        LocalDateTime.of(runDate.plusDays(dayOffset.toLong()), time)
            .plusMinutes(delayMinutes.toLong())

    companion object {
        const val PROVIDER_NAME = "Timetable projection"
    }
}
