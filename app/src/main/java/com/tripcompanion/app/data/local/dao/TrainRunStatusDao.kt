package com.tripcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tripcompanion.app.data.local.entity.TrainRunStatusEntity
import com.tripcompanion.app.data.local.entity.TrainRunStopEntity
import kotlinx.coroutines.flow.Flow

/**
 * The cached running-status snapshot.
 *
 * Every write goes through [replaceSnapshot]. There is no update path and no merge path on
 * purpose: a snapshot is a single observation of where a train was at one instant, and
 * stitching a new header onto old stop rows would produce times that never coexisted.
 */
@Dao
interface TrainRunStatusDao {

    @Query("SELECT * FROM train_run_status WHERE trainId = :trainId")
    fun getStatus(trainId: Long): Flow<TrainRunStatusEntity?>

    @Query("SELECT * FROM train_run_status WHERE trainId = :trainId")
    suspend fun getStatusOnce(trainId: Long): TrainRunStatusEntity?

    @Query("SELECT * FROM train_run_stops WHERE trainId = :trainId ORDER BY serialNo ASC")
    fun getRunStops(trainId: Long): Flow<List<TrainRunStopEntity>>

    @Query("SELECT * FROM train_run_stops WHERE trainId = :trainId ORDER BY serialNo ASC")
    suspend fun getRunStopsOnce(trainId: Long): List<TrainRunStopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStatus(status: TrainRunStatusEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRunStops(stops: List<TrainRunStopEntity>)

    @Query("DELETE FROM train_run_stops WHERE trainId = :trainId")
    suspend fun deleteRunStops(trainId: Long)

    @Query("DELETE FROM train_run_status WHERE trainId = :trainId")
    suspend fun deleteStatus(trainId: Long)

    /** Replace header and stops together, so no reader ever sees one without the other. */
    @Transaction
    suspend fun replaceSnapshot(status: TrainRunStatusEntity, stops: List<TrainRunStopEntity>) {
        upsertStatus(status)
        deleteRunStops(status.trainId)
        insertRunStops(stops)
    }

    /** Drop the cache for one train. Costs a refresh and nothing else. */
    @Transaction
    suspend fun clearSnapshot(trainId: Long) {
        deleteRunStops(trainId)
        deleteStatus(trainId)
    }
}
