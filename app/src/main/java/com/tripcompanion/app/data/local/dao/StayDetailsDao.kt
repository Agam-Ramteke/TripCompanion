package com.tripcompanion.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tripcompanion.app.data.local.entity.StayDetailsEntity
import kotlinx.coroutines.flow.Flow

/** Hotel paperwork, one row per STAY event. */
@Dao
interface StayDetailsDao {

    @Query("SELECT * FROM stay_details WHERE eventId = :eventId")
    fun getForEvent(eventId: Long): Flow<StayDetailsEntity?>

    @Query("SELECT * FROM stay_details WHERE eventId = :eventId")
    suspend fun getForEventOnce(eventId: Long): StayDetailsEntity?

    /**
     * Every stay's paperwork at once.
     *
     * The itinerary draws a stay card for each STAY event on the day, so it needs several of
     * these rows together rather than one at a time. There is one row per stay event, so the
     * whole table is a handful of rows even for a long trip — cheaper to read than to join.
     */
    @Query("SELECT * FROM stay_details")
    fun getAll(): Flow<List<StayDetailsEntity>>

    /** REPLACE rather than a separate insert/update: the event id is the identity. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(details: StayDetailsEntity)

    @Query("DELETE FROM stay_details WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: Long)
}
