package com.tripcompanion.app.domain.service

import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop

/**
 * What can go wrong when asking where a train is.
 *
 * The user's categories, not HTTP's — same reasoning as [LocationSearchError]. "This train
 * isn't running today" and "we couldn't reach the railway" are both failures to a network
 * library and completely different sentences on screen.
 */
enum class TrainStatusError {
    /** No usable connection, DNS failure, socket refused. */
    NETWORK_UNAVAILABLE,

    /** The provider accepted the request but did not answer in time. */
    TIMEOUT,

    /** Too many requests. The query is fine; the caller should wait. */
    RATE_LIMITED,

    /** The provider answered with an error status or an error code in the body. */
    PROVIDER_ERROR,

    /** The provider answered successfully with something unparseable. */
    MALFORMED_RESPONSE,

    /**
     * The train number is not running on the requested date.
     *
     * Not an error the user can fix by retrying, and not the app's fault either — most trains
     * do not run every day, and a number typed a digit wrong looks exactly like this.
     */
    TRAIN_NOT_FOUND,

    /**
     * There is no stored timetable, so there is nothing to project a position from.
     *
     * The offline path's version of "no data": without a route, an offline projection has no
     * stations to place the train between.
     */
    NO_SCHEDULE,

    /** Anything unclassified. Kept so the taxonomy is total and nothing is swallowed. */
    UNKNOWN
}

/**
 * The result of asking for a train's status.
 *
 * [Cached] is deliberately separate from [Updated]: both carry a usable snapshot, but only
 * one of them was just observed. And [Failed] carries the cached snapshot too, because the
 * right thing to show when a refresh fails is the last known position with its age, not an
 * empty screen.
 */
sealed interface TrainStatusOutcome {
    /** Freshly fetched or freshly projected, and already persisted. */
    data class Updated(val status: TrainRunStatus) : TrainStatusOutcome

    /** The stored snapshot is recent enough that no request was made. */
    data class Cached(val status: TrainRunStatus) : TrainStatusOutcome

    /** The refresh failed. [cached] is whatever was stored, which may be null. */
    data class Failed(
        val error: TrainStatusError,
        val cached: TrainRunStatus? = null
    ) : TrainStatusOutcome
}

/** The result of asking for a train's timetable. */
sealed interface TrainScheduleOutcome {
    data class Loaded(val stops: List<TrainStop>) : TrainScheduleOutcome

    /**
     * This provider cannot fetch timetables at all.
     *
     * The honest answer from the offline projection: it can only work from a schedule that is
     * already stored, so the screen offers to enter stops by hand instead of retrying.
     */
    data object Unsupported : TrainScheduleOutcome

    data class Failed(val error: TrainStatusError) : TrainScheduleOutcome
}

/** Thrown by a [TrainStatusProvider] to report a classified failure. */
class TrainStatusException(
    val error: TrainStatusError,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message ?: error.name, cause)

/**
 * What the app actually calls to find out where a train is.
 *
 * §11's boundary again: nothing about HTTP, JSON, API keys or which railway's API is in use
 * is visible through this interface. Screens call [refresh] and observe the repository; the
 * decision about *whether* to make a request — and it is a decision, on a metered free tier —
 * lives behind here.
 */
interface TrainStatusService {

    /**
     * Whether the bound provider observes real running status, as opposed to projecting a
     * position from the timetable.
     *
     * Screens show this. A projected position presented as live tracking would be the app
     * lying about how much it knows.
     */
    val isLive: Boolean

    /** Named on screen so the user knows where a number came from. */
    val providerName: String

    /**
     * Bring the stored snapshot up to date if it is stale, and return what is now known.
     *
     * Never throws. A failed refresh is a value, because being out of signal on a train is the
     * expected case rather than an exceptional one.
     *
     * @param force skip the freshness check — for an explicit pull-to-refresh, where the user
     *   has asked for a request and should get one.
     */
    suspend fun refresh(trainId: Long, force: Boolean = false): TrainStatusOutcome

    /**
     * Fetch and store the train's timetable.
     *
     * Separate from [refresh] because a schedule changes with the timetable, not with the
     * minute: it is fetched once and then read offline for the rest of the trip.
     */
    suspend fun refreshSchedule(trainId: Long): TrainScheduleOutcome

    companion object {
        /**
         * How old a snapshot may be before a refresh is worth a request.
         *
         * Two minutes. Railway feeds update at roughly station granularity, so polling faster
         * returns the same numbers while spending a metered quota, and every screen shows the
         * snapshot's age anyway.
         */
        const val CACHE_TTL_MINUTES = 2L

        /** Hard ceiling on one request, whatever the provider's own timeouts do. */
        const val REQUEST_TIMEOUT_MS = 12_000L
    }
}
