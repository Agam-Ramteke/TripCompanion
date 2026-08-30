package com.tripcompanion.app.domain.engine

import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.service.TrainStatusOutcome
import com.tripcompanion.app.domain.service.TrainStatusService
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Event-driven train arrival automation.
 *
 * Implements single-shot arrival checkpoints without continuous/periodic polling:
 * - When a train is scheduled to arrive, ONE status check is executed at `scheduledArrival`.
 * - If the API reports arrival: marks arrival, records actual arrival timestamp, and schedules no further checks.
 * - If not arrived: reads latest estimated arrival from API (e.g. 04:47) and schedules ONE check for that instant.
 * - If app opened late (e.g. at 05:15 for a 04:30 arrival): immediately performs ONE check to resolve the event.
 */
@Singleton
class TrainArrivalAutomator @Inject constructor(
    private val trainStatusService: TrainStatusService,
    private val trainRepository: TrainRepository,
    private val timeProvider: TimeProvider
) {
    /** Next checkpoint per trainId, indicating the exact instant when the next API check may run. */
    private val nextCheckpoints = ConcurrentHashMap<Long, LocalDateTime>()

    /**
     * Checks if any active train with unconfirmed arrival has reached its checkpoint time.
     * Guaranteed to make at most ONE request per checkpoint without continuous polling.
     */
    suspend fun checkArrivals(trains: List<Train>) {
        if (!trainStatusService.isLive) return
        val now = timeProvider.now()

        for (train in trains) {
            // Already arrived or manually confirmed
            if (train.actualArrivalTime != null) {
                nextCheckpoints.remove(train.id)
                continue
            }

            // Outside plausible travel window (only check between departure - 30m and arrival + 12h)
            if (now.isBefore(train.departureTime.minusMinutes(30)) ||
                now.isAfter(train.arrivalTime.plusHours(12))
            ) {
                continue
            }

            val checkpoint = nextCheckpoints.getOrPut(train.id) { train.arrivalTime }
            if (!now.isBefore(checkpoint)) {
                // Execute ONE single-shot API request
                val outcome = trainStatusService.refresh(train.id, force = true)
                when (outcome) {
                    is TrainStatusOutcome.Updated -> {
                        val status = outcome.status
                        val isLive = status.source == TrainRunSource.LIVE
                        val destStop = status.stops.firstOrNull {
                            it.stationCode.equals(train.destinationCode, ignoreCase = true)
                        }
                        val hasArrived = status.hasArrived ||
                            (destStop != null && (destStop.isDeparted || destStop.actualArrival != null))

                        if (hasArrived) {
                            // Arrived! Remove checkpoint, no further checks needed.
                            nextCheckpoints.remove(train.id)
                        } else if (isLive) {
                            // Schedule ONE next check based on expected arrival time
                            val delayMinutes = destStop?.arrivalDelayMinutes ?: status.delayMinutes
                            val expectedArrival = train.arrivalTime.plusMinutes(delayMinutes.toLong())
                            // Ensure next check is at expected arrival (or at least 3 min in future to prevent hammering)
                            val nextCheck = if (expectedArrival.isAfter(now)) expectedArrival else now.plusMinutes(3)
                            nextCheckpoints[train.id] = nextCheck
                        } else {
                            // Offline projection fallback: retry in 5 minutes
                            nextCheckpoints[train.id] = now.plusMinutes(5)
                        }
                    }
                    is TrainStatusOutcome.Cached -> {
                        // Cached snapshot, bump checkpoint slightly
                        nextCheckpoints[train.id] = now.plusMinutes(3)
                    }
                    is TrainStatusOutcome.Failed -> {
                        // Network error / timeout: retry at next sensible interval
                        nextCheckpoints[train.id] = now.plusMinutes(5)
                    }
                }
            }
        }
    }
}
