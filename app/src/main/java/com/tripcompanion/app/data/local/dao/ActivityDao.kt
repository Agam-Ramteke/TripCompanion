package com.tripcompanion.app.data.local.dao

import androidx.room.*
import com.tripcompanion.app.data.local.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE eventId = :eventId ORDER BY `order` ASC")
    fun getActivitiesForEvent(eventId: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    fun getActivityById(id: Long): Flow<ActivityEntity?>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getActivityByIdOnce(id: Long): ActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: ActivityEntity): Long

    @Update
    suspend fun updateActivity(activity: ActivityEntity)

    @Delete
    suspend fun deleteActivity(activity: ActivityEntity)

    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteActivityById(id: Long)

    @Query("SELECT COALESCE(MAX(`order`), -1) + 1 FROM activities WHERE eventId = :eventId")
    suspend fun getNextOrder(eventId: Long): Int

    @Query("UPDATE activities SET completionStatus = :status WHERE id = :id")
    suspend fun updateCompletionStatus(id: Long, status: String)
}
