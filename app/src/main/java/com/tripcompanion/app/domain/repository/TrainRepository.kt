package com.tripcompanion.app.domain.repository

import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import kotlinx.coroutines.flow.Flow

/**
 * Trains, their passengers, their timetables, and the last known running status of each.
 *
 * One repository for all four because they are one aggregate: a train without its stop list
 * cannot draw a route, a running status without a train has nothing to be the status of, and a
 * booking without its party is a ticket with nobody on it. Splitting them would push the job of
 * keeping them consistent up into the ViewModels.
 *
 * Every read here returns a [Train] with its [Train.passengers] already attached, so no screen
 * has to make a second call to find out who is travelling. Every write takes them the same way:
 * [insertTrain] and [updateTrain] save the party along with the booking, in that order, because
 * a passenger needs a train id to point at.
 */
interface TrainRepository {

    // ── The booking ──

    fun getTrainsForTrip(tripId: Long): Flow<List<Train>>
    fun getAllTrains(): Flow<List<Train>>
    fun getTrainById(id: Long): Flow<Train?>
    suspend fun getTrainByIdOnce(id: Long): Train?

    /** The train linked to a JOURNEY event, so Home can show the real coach and berths. */
    fun getTrainForEvent(eventId: Long): Flow<Train?>

    /** Saves the booking and its party together, and returns the new train's id. */
    suspend fun insertTrain(train: Train): Long

    /**
     * Saves the booking and replaces its party wholesale.
     *
     * A [Train] whose [Train.passengers] is empty therefore *removes* every passenger. That is
     * deliberate: emptying the list is how the editor deletes the last one, and treating empty
     * as "leave them alone" would make that impossible.
     */
    suspend fun updateTrain(train: Train)

    suspend fun deleteTrain(id: Long)

    // ── The timetable ──

    fun getStops(trainId: Long): Flow<List<TrainStop>>
    suspend fun getStopsOnce(trainId: Long): List<TrainStop>

    /** Whether a route is stored at all — what the Route tab checks before offering a fetch. */
    suspend fun hasSchedule(trainId: Long): Boolean

    /** Swap in a whole timetable, replacing any previous one. */
    suspend fun replaceSchedule(trainId: Long, stops: List<TrainStop>)

    // ── The running status ──

    /**
     * The cached snapshot, header and stops together, emitting again whenever either changes.
     *
     * Null means nothing has ever been fetched or projected for this train. Screens read
     * [TrainRunStatus.fetchedAt] to decide how to describe what they are showing; this
     * repository never hides a stale snapshot, because a stale snapshot honestly labelled is
     * more use on a train than a spinner.
     */
    fun observeRunStatus(trainId: Long): Flow<TrainRunStatus?>

    suspend fun getRunStatusOnce(trainId: Long): TrainRunStatus?

    suspend fun saveRunStatus(status: TrainRunStatus)

    suspend fun clearRunStatus(trainId: Long)
}
