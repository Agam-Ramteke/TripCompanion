package com.tripcompanion.app.data.local.dao

import androidx.room.*
import com.tripcompanion.app.data.local.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE tripId = :tripId ORDER BY startTime ASC, `order` ASC, id ASC")
    fun getEventsForTrip(tripId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    fun getEventById(id: Long): Flow<EventEntity?>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventByIdOnce(id: Long): EventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Delete
    suspend fun deleteEvent(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEventById(id: Long)

    /**
     * Every itinerary entry pointing at one place, across all trips.
     *
     * Place Detail asks "when am I going here", and the answer is not scoped to a trip: the
     * same lake can appear on two visits. Ordered by the §10 comparator so the screen lists
     * them in the order the rest of the app would.
     */
    @Query("SELECT * FROM events WHERE locationId = :locationId ORDER BY startTime ASC, `order` ASC, id ASC")
    fun getEventsForLocation(locationId: Long): Flow<List<EventEntity>>

    @Query("SELECT COALESCE(MAX(`order`), -1) + 1 FROM events WHERE tripId = :tripId")
    suspend fun getNextOrder(tripId: Long): Int

    @Query("UPDATE events SET `order` = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)
}
