package com.tripcompanion.app.data.local.dao

import androidx.room.*
import com.tripcompanion.app.data.local.entity.PlannedPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedPhotoDao {
    @Query("SELECT * FROM planned_photos WHERE eventId = :eventId ORDER BY `order` ASC")
    fun getPhotosForEvent(eventId: Long): Flow<List<PlannedPhotoEntity>>

    @Query("SELECT * FROM planned_photos WHERE id = :id")
    fun getPhotoById(id: Long): Flow<PlannedPhotoEntity?>

    @Query("SELECT * FROM planned_photos WHERE id = :id")
    suspend fun getPhotoByIdOnce(id: Long): PlannedPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PlannedPhotoEntity): Long

    @Update
    suspend fun updatePhoto(photo: PlannedPhotoEntity)

    @Delete
    suspend fun deletePhoto(photo: PlannedPhotoEntity)

    @Query("DELETE FROM planned_photos WHERE id = :id")
    suspend fun deletePhotoById(id: Long)

    @Query("SELECT COALESCE(MAX(`order`), -1) + 1 FROM planned_photos WHERE eventId = :eventId")
    suspend fun getNextOrder(eventId: Long): Int

    /**
     * How many photo plans a whole trip carries.
     *
     * Photos are keyed by event, so Home's Photos stat would otherwise mean one query per
     * event on every clock tick. The sub-select keeps it to one, and Room re-emits it when
     * either table changes because both are named in the query.
     */
    @Query(
        """
        SELECT COUNT(*) FROM planned_photos
        WHERE eventId IN (SELECT id FROM events WHERE tripId = :tripId)
        """
    )
    fun countForTrip(tripId: Long): Flow<Int>
}
