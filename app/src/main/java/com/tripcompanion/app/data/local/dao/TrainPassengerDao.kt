package com.tripcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.tripcompanion.app.data.local.entity.TrainPassengerEntity
import kotlinx.coroutines.flow.Flow

/**
 * The people on a booking.
 *
 * `serialNo ASC, id ASC` everywhere: the serial is the railway's own ordering, the one printed
 * on the chart the conductor reads, and the tiebreak on `id` keeps two hand-added passengers
 * who share a serial from swapping places between reads.
 */
@Dao
interface TrainPassengerDao {

    @Query("SELECT * FROM train_passengers WHERE trainId = :trainId ORDER BY serialNo ASC, id ASC")
    fun getPassengersForTrain(trainId: Long): Flow<List<TrainPassengerEntity>>

    @Query("SELECT * FROM train_passengers WHERE trainId = :trainId ORDER BY serialNo ASC, id ASC")
    suspend fun getPassengersForTrainOnce(trainId: Long): List<TrainPassengerEntity>

    /**
     * Every passenger in the database, for grouping onto a list of trains.
     *
     * A list screen needs the passengers of every train it shows, and a query parameterised by
     * a list of train ids would not re-run when that list changed — it would have to be
     * resubscribed, which means a screen flicker on every train added. One flow of everything,
     * grouped in memory, is both simpler and correct; a trip has a handful of trains, not
     * thousands.
     */
    @Query("SELECT * FROM train_passengers ORDER BY trainId ASC, serialNo ASC, id ASC")
    fun getAllPassengers(): Flow<List<TrainPassengerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassengers(passengers: List<TrainPassengerEntity>)

    @Query("DELETE FROM train_passengers WHERE trainId = :trainId")
    suspend fun deletePassengersForTrain(trainId: Long)

    /**
     * Swap in the whole party.
     *
     * One transaction and a full replace rather than a merge. Editing a booking is editing a
     * list — a passenger can be removed, and their berth reassigned to someone else on the same
     * PNR — so matching rows up by anything would be guesswork. Half a party is worse than
     * either state.
     */
    @Transaction
    suspend fun replacePassengers(trainId: Long, passengers: List<TrainPassengerEntity>) {
        deletePassengersForTrain(trainId)
        if (passengers.isNotEmpty()) insertPassengers(passengers)
    }
}
