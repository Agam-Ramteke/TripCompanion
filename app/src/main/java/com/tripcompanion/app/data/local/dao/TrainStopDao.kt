package com.tripcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tripcompanion.app.data.local.entity.TrainStopEntity
import kotlinx.coroutines.flow.Flow

/**
 * A train's stored timetable.
 *
 * `serialNo` is the route order and the only sort key needed — the railway numbers stops
 * along the line, so it already encodes direction and survives overnight rollover where a
 * sort on time would not.
 */
@Dao
interface TrainStopDao {

    @Query("SELECT * FROM train_stops WHERE trainId = :trainId ORDER BY serialNo ASC")
    fun getStopsForTrain(trainId: Long): Flow<List<TrainStopEntity>>

    @Query("SELECT * FROM train_stops WHERE trainId = :trainId ORDER BY serialNo ASC")
    suspend fun getStopsForTrainOnce(trainId: Long): List<TrainStopEntity>

    @Query("SELECT COUNT(*) FROM train_stops WHERE trainId = :trainId")
    suspend fun countStopsForTrain(trainId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStops(stops: List<TrainStopEntity>)

    @Query("DELETE FROM train_stops WHERE trainId = :trainId")
    suspend fun deleteStopsForTrain(trainId: Long)

    /**
     * Swap in a whole schedule.
     *
     * One transaction, because a route half-deleted is a route that renders as a broken line.
     * A fetched schedule replaces the old one entirely rather than merging: stops get added
     * and dropped between timetable revisions, and a merge would leave phantom stations.
     */
    @Transaction
    suspend fun replaceStops(trainId: Long, stops: List<TrainStopEntity>) {
        deleteStopsForTrain(trainId)
        insertStops(stops)
    }
}
