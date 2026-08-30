package com.tripcompanion.app.data.train

import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.data.network.ScheduleProjectionTrainStatusProvider
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainRunSource
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.repository.EventRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.service.TrainScheduleOutcome
import com.tripcompanion.app.domain.service.TrainStatusError
import com.tripcompanion.app.domain.service.TrainStatusException
import com.tripcompanion.app.domain.service.TrainStatusOutcome
import com.tripcompanion.app.domain.service.TrainStatusProvider
import com.tripcompanion.app.domain.service.TrainStatusService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The policy around asking a railway where a train is.
 *
 * The provider knows how to make one request. This decides whether a request is worth making
 * at all, what to do when it fails, and what the screen is handed either way — the same split
 * as [com.tripcompanion.app.data.search.DefaultLocationSearchService].
 *
 * Four rules, in the order they bite:
 *
 * 1. **A fresh snapshot is not refetched.** Railway feeds move at station granularity, so a
 *    request two minutes after the last one returns the same numbers and spends a metered
 *    quota to do it. An explicit pull-to-refresh passes `force` and skips this.
 * 2. **A train that is not running is not queried.** Outside the window around its own
 *    timetable there is nothing live to report, and the projection answers "hasn't started" or
 *    "arrived" for free. This holds even under `force`, because a request cannot tell the user
 *    anything about tomorrow's train.
 * 3. **A recent real observation beats a guess.** When a live request fails, a live snapshot
 *    from the last half hour is returned as-is with its age; the screens show "updated N min
 *    ago" and the user judges it. Past that the position describes somewhere the train has
 *    left, and the timetable projection becomes the better answer.
 * 4. **Nothing throws.** Losing signal on a train is the expected case. Every failure comes
 *    back as [TrainStatusOutcome.Failed] carrying whatever is cached.
 */
@Singleton
class DefaultTrainStatusService @Inject constructor(
    private val provider: TrainStatusProvider,
    /**
     * The offline projection, injected concretely and on purpose.
     *
     * It is not the bound [TrainStatusProvider] — that may be the live one — but it is always
     * available as the fallback. Naming the class here is a data-layer reference to a
     * data-layer class, so §11's boundary is untouched: nothing above this file learns that
     * either provider exists.
     */
    private val projection: ScheduleProjectionTrainStatusProvider,
    private val repository: TrainRepository,
    private val eventRepository: EventRepository,
    private val timeProvider: TimeProvider
) : TrainStatusService {

    override val isLive: Boolean get() = provider.isLive

    override val providerName: String get() = provider.providerName

    override suspend fun refresh(trainId: Long, force: Boolean): TrainStatusOutcome {
        val train = repository.getTrainByIdOnce(trainId)
            ?: return TrainStatusOutcome.Failed(TrainStatusError.TRAIN_NOT_FOUND)

        val cached = repository.getRunStatusOnce(trainId)
        val now = timeProvider.now()

        if (!force && cached != null && isFresh(cached, now)) {
            return TrainStatusOutcome.Cached(cached)
        }

        val schedule = repository.getStopsOnce(trainId)

        // Rule 2. No live request outside the run's own window; project instead.
        if (!provider.isLive || !isRunning(train, now)) {
            return project(train, schedule, reason = null, cached = cached)
        }

        return try {
            val status = withTimeout(TrainStatusService.REQUEST_TIMEOUT_MS) {
                provider.fetchStatus(train, schedule)
            }
            repository.saveRunStatus(status)
            if (status.source == TrainRunSource.LIVE) {
                checkAndApplyArrival(train, status)
            }
            TrainStatusOutcome.Updated(status)
        } catch (e: TimeoutCancellationException) {
            // Must be caught before CancellationException: withTimeout signals a timeout by
            // cancelling, and a timeout is a failure the user should see.
            recover(train, schedule, TrainStatusError.TIMEOUT, cached, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TrainStatusException) {
            recover(train, schedule, e.error, cached, now)
        } catch (e: Exception) {
            recover(train, schedule, TrainStatusError.UNKNOWN, cached, now)
        }
    }

    override suspend fun refreshSchedule(trainId: Long): TrainScheduleOutcome {
        val train = repository.getTrainByIdOnce(trainId)
            ?: return TrainScheduleOutcome.Failed(TrainStatusError.TRAIN_NOT_FOUND)

        val stops = try {
            withTimeout(TrainStatusService.REQUEST_TIMEOUT_MS) {
                provider.fetchSchedule(train.number)
            }
        } catch (e: TimeoutCancellationException) {
            return TrainScheduleOutcome.Failed(TrainStatusError.TIMEOUT)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TrainStatusException) {
            return TrainScheduleOutcome.Failed(e.error)
        } catch (e: Exception) {
            return TrainScheduleOutcome.Failed(TrainStatusError.UNKNOWN)
        }

        // Null means this provider does not do timetables at all, which is not a failure.
        if (stops == null) return TrainScheduleOutcome.Unsupported
        if (stops.isEmpty()) {
            return TrainScheduleOutcome.Failed(TrainStatusError.MALFORMED_RESPONSE)
        }

        repository.replaceSchedule(trainId, stops)
        return TrainScheduleOutcome.Loaded(stops)
    }

    /**
     * What to show when a live request fails.
     *
     * Rule 3: a real observation from the last [LIVE_SNAPSHOT_USEFUL_MINUTES] is handed back
     * untouched, because it happened and the projection did not. Anything older is superseded
     * by the timetable.
     */
    private suspend fun recover(
        train: Train,
        schedule: List<TrainStop>,
        error: TrainStatusError,
        cached: TrainRunStatus?,
        now: LocalDateTime
    ): TrainStatusOutcome {
        if (cached != null && cached.source == TrainRunSource.LIVE && isUsable(cached, now)) {
            return TrainStatusOutcome.Failed(error, cached)
        }
        return project(train, schedule, reason = error, cached = cached)
    }

    /**
     * Projects from the stored timetable and saves the result.
     *
     * [reason] is the live failure that led here, or null when projecting was the plan all
     * along. If the projection cannot run either, [reason] wins where it exists: the user is
     * better served by "we couldn't reach the railway" than by "no timetable stored", when both
     * are true and only the first is why they tapped.
     */
    private suspend fun project(
        train: Train,
        schedule: List<TrainStop>,
        reason: TrainStatusError?,
        cached: TrainRunStatus?
    ): TrainStatusOutcome {
        val projected = try {
            projection.fetchStatus(train, schedule)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TrainStatusException) {
            return TrainStatusOutcome.Failed(reason ?: e.error, cached)
        } catch (e: Exception) {
            return TrainStatusOutcome.Failed(reason ?: TrainStatusError.UNKNOWN, cached)
        }

        repository.saveRunStatus(projected)
        return TrainStatusOutcome.Updated(projected)
    }

    private fun isFresh(status: TrainRunStatus, now: LocalDateTime): Boolean =
        status.fetchedAt.plusMinutes(TrainStatusService.CACHE_TTL_MINUTES).isAfter(now)

    private fun isUsable(status: TrainRunStatus, now: LocalDateTime): Boolean =
        status.fetchedAt.plusMinutes(LIVE_SNAPSHOT_USEFUL_MINUTES).isAfter(now)

    /**
     * Whether the train could plausibly be on the move.
     *
     * Bounded by the booking's own times with an hour's grace before and six hours after, which
     * covers a late start and a badly delayed arrival without querying next week's train every
     * time someone opens the screen.
     */
    private fun isRunning(train: Train, now: LocalDateTime): Boolean {
        val from = train.departureTime.minusMinutes(LIVE_LEAD_MINUTES)
        val until = train.arrivalTime.plusHours(LIVE_TRAIL_HOURS)
        return !now.isBefore(from) && !now.isAfter(until)
    }

    private suspend fun checkAndApplyArrival(train: Train, status: TrainRunStatus) {
        val destStop = status.stops.firstOrNull { it.stationCode.equals(train.destinationCode, ignoreCase = true) }
        val isArrived = status.hasArrived || (destStop != null && (destStop.isDeparted || destStop.actualArrival != null))

        if (isArrived && train.actualArrivalTime == null) {
            val arrivalTime = if (destStop?.actualArrival != null) {
                val stopDate = status.runDate.plusDays(destStop.dayOffset.toLong())
                LocalDateTime.of(stopDate, destStop.actualArrival)
            } else {
                status.fetchedAt
            }

            val originStop = status.stops.firstOrNull { it.stationCode.equals(train.originCode, ignoreCase = true) }
            val actualDep = if (train.actualDepartureTime == null && originStop?.actualDeparture != null) {
                val originDate = status.runDate.plusDays(originStop.dayOffset.toLong())
                LocalDateTime.of(originDate, originStop.actualDeparture)
            } else {
                train.actualDepartureTime
            }

            val updatedTrain = train.copy(
                actualArrivalTime = arrivalTime,
                actualDepartureTime = actualDep,
                arrivalSource = "API"
            )
            repository.updateTrain(updatedTrain)

            if (train.eventId != null) {
                val event = eventRepository.getEventByIdOnce(train.eventId)
                if (event != null && event.status != EventStatus.COMPLETED) {
                    eventRepository.updateEvent(
                        event.copy(
                            status = EventStatus.COMPLETED,
                            actualEndTime = arrivalTime
                        )
                    )
                }
            }
        }
    }

    companion object {
        /** How long a real observation stays preferable to a projection. */
        const val LIVE_SNAPSHOT_USEFUL_MINUTES = 30L

        /** How early before departure live status starts being worth a request. */
        const val LIVE_LEAD_MINUTES = 60L

        /** How long after scheduled arrival a run is still considered possibly in progress. */
        const val LIVE_TRAIL_HOURS = 6L
    }
}
