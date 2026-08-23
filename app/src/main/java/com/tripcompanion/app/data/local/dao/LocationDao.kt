package com.tripcompanion.app.data.local.dao

import androidx.room.*
import com.tripcompanion.app.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name ASC")
    fun getAllLocations(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE id = :id")
    fun getLocationById(id: Long): Flow<LocationEntity?>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getLocationByIdOnce(id: Long): LocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity): Long

    @Update
    suspend fun updateLocation(location: LocationEntity)

    @Delete
    suspend fun deleteLocation(location: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteLocationById(id: Long)

    // ── Places tabs ──
    //
    // Three separate queries rather than one filtered in memory, so a long list of places
    // is filtered by SQLite instead of being loaded whole on every tab switch.

    /** Places still to see: saved or not, but not yet ticked off. */
    @Query("SELECT * FROM locations WHERE isVisited = 0 ORDER BY name ASC")
    fun getPlacesToVisit(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE isVisited = 1 ORDER BY updatedAt DESC, name ASC")
    fun getVisitedPlaces(): Flow<List<LocationEntity>>

    /** Explicitly saved, whether or not they have been visited. */
    @Query("SELECT * FROM locations WHERE isSaved = 1 ORDER BY name ASC")
    fun getSavedPlaces(): Flow<List<LocationEntity>>

    @Query("UPDATE locations SET isSaved = :saved, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSaved(id: Long, saved: Boolean, updatedAt: LocalDateTime)

    @Query("UPDATE locations SET isVisited = :visited, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setVisited(id: Long, visited: Boolean, updatedAt: LocalDateTime)
}
