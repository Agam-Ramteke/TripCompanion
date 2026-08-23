package com.tripcompanion.app.domain.service

import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop

/**
 * The port a source of train information plugs into.
 *
 * The mirror of [LocationSearchProvider]: one interface, one method per question, no HTTP
 * vocabulary. Two implementations ship — a real railway API and an offline projection built
 * from the stored timetable — and neither is nameable from outside `data/`.
 *
 * Implementations report failure by throwing [TrainStatusException] with a classified
 * [TrainStatusError]. Turning that back into a value is the service's job, not each
 * provider's.
 */
interface TrainStatusProvider {

    /** Shown on screen next to a fetched number, so the user knows who said it. */
    val providerName: String

    /**
     * Whether this provider reports observed running status.
     *
     * False for the projection, which knows only the timetable and the clock. The distinction
     * reaches the screen unchanged: a projected position is labelled as one.
     */
    val isLive: Boolean

    /**
     * Where the train is now.
     *
     * @param train the booking, which carries the number, the date and — for the projection —
     *   the user's own delay estimate.
     * @param schedule the stored timetable, ordered by `serialNo`. The live provider mostly
     *   ignores it because the response carries its own route; the projection cannot work
     *   without it and throws [TrainStatusError.NO_SCHEDULE] when it is empty.
     */
    suspend fun fetchStatus(train: Train, schedule: List<TrainStop>): TrainRunStatus

    /**
     * The train's timetable.
     *
     * Returns null when this provider cannot supply one — the projection's honest answer,
     * since a timetable is exactly the thing it needs given rather than the thing it can
     * produce. Null is not a failure and is not an error, so it is not an exception.
     */
    suspend fun fetchSchedule(trainNumber: String): List<TrainStop>?
}
