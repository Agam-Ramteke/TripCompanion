package com.tripcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.tripcompanion.app.data.local.entity.TrainEntity
import kotlinx.coroutines.flow.Flow

/**
 * Trains, ordered the way a traveller reads them.
 *
 * `departureTime ASC, id ASC` on every list query. The tiebreak on `id` is not decoration:
 * two trains booked for the same minute would otherwise swap places between reads and the
 * list would flicker.
 */
@Dao
interface TrainDao {

    @Query("SELECT * FROM trains WHERE tripId = :tripId ORDER BY departureTime ASC, id ASC")
    fun getTrainsForTrip(tripId: Long): Flow<List<TrainEntity>>

    /** Every train across every trip, for the Trains tab when no trip is selected. */
    @Query("SELECT * FROM trains ORDER BY departureTime ASC, id ASC")
    fun getAllTrains(): Flow<List<TrainEntity>>

    @Query("SELECT * FROM trains WHERE id = :id")
    fun getTrainById(id: Long): Flow<TrainEntity?>

    @Query("SELECT * FROM trains WHERE id = :id")
    suspend fun getTrainByIdOnce(id: Long): TrainEntity?

    /**
     * The train attached to a JOURNEY event, if any.
     *
     * `LIMIT 1` because the link is one train per event by convention rather than by
     * constraint — `eventId` is advisory, not a foreign key.
     */
    @Query("SELECT * FROM trains WHERE eventId = :eventId ORDER BY id ASC LIMIT 1")
    fun getTrainForEvent(eventId: Long): Flow<TrainEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrain(train: TrainEntity): Long

    @Update
    suspend fun updateTrain(train: TrainEntity)

    @Query("SELECT * FROM trains WHERE tripId = :tripId AND LOWER(TRIM(number)) = LOWER(TRIM(:trainNumber)) AND departureTime = :departureTime LIMIT 1")
    suspend fun findTrainByNumberAndDeparture(tripId: Long, trainNumber: String, departureTime: java.time.LocalDateTime): TrainEntity?

    @Query("DELETE FROM trains WHERE id = :id")
    suspend fun deleteTrainById(id: Long)
}
