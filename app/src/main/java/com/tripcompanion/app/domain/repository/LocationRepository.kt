package com.tripcompanion.app.domain.repository

import com.tripcompanion.app.domain.model.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getAllLocations(): Flow<List<Location>>
    fun getLocationById(id: Long): Flow<Location?>
    suspend fun getLocationByIdOnce(id: Long): Location?
    suspend fun insertLocation(location: Location): Long
    suspend fun updateLocation(location: Location)
    suspend fun deleteLocation(id: Long)

    /** Places not yet ticked off as visited. */
    fun getPlacesToVisit(): Flow<List<Location>>
    fun getVisitedPlaces(): Flow<List<Location>>
    fun getSavedPlaces(): Flow<List<Location>>

    /**
     * Toggle one flag without reading, copying and writing the whole row.
     *
     * A place card's bookmark taps faster than a round trip, and a read-modify-write would
     * drop the other flag if two taps overlapped.
     */
    suspend fun setSaved(id: Long, saved: Boolean)
    suspend fun setVisited(id: Long, visited: Boolean)
}
