package com.tripcompanion.app.data.repository

import com.tripcompanion.app.data.local.dao.LocationDao
import com.tripcompanion.app.data.local.toDomain
import com.tripcompanion.app.data.local.toEntity
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao
) : LocationRepository {

    override fun getAllLocations(): Flow<List<Location>> =
        locationDao.getAllLocations().map { entities -> entities.map { it.toDomain() } }

    override fun getLocationById(id: Long): Flow<Location?> =
        locationDao.getLocationById(id).map { it?.toDomain() }

    override suspend fun getLocationByIdOnce(id: Long): Location? =
        locationDao.getLocationByIdOnce(id)?.toDomain()

    override suspend fun insertLocation(location: Location): Long {
        val existing = locationDao.findLocationByName(location.name.trim())
        return if (existing != null) {
            existing.id
        } else {
            locationDao.insertLocation(location.toEntity())
        }
    }

    override suspend fun updateLocation(location: Location) =
        locationDao.updateLocation(location.copy(updatedAt = LocalDateTime.now()).toEntity())

    override suspend fun deleteLocation(id: Long) =
        locationDao.deleteLocationById(id)

    override fun getPlacesToVisit(): Flow<List<Location>> =
        locationDao.getPlacesToVisit().map { entities -> entities.map { it.toDomain() } }

    override fun getVisitedPlaces(): Flow<List<Location>> =
        locationDao.getVisitedPlaces().map { entities -> entities.map { it.toDomain() } }

    override fun getSavedPlaces(): Flow<List<Location>> =
        locationDao.getSavedPlaces().map { entities -> entities.map { it.toDomain() } }

    override suspend fun setSaved(id: Long, saved: Boolean) =
        locationDao.setSaved(id, saved, LocalDateTime.now())

    override suspend fun setVisited(id: Long, visited: Boolean) =
        locationDao.setVisited(id, visited, LocalDateTime.now())
}
