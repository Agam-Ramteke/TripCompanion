package com.tripcompanion.app.data.repository

import com.tripcompanion.app.data.local.dao.PlannedPhotoDao
import com.tripcompanion.app.data.local.toDomain
import com.tripcompanion.app.data.local.toEntity
import com.tripcompanion.app.domain.model.PlannedPhoto
import com.tripcompanion.app.domain.repository.PlannedPhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlannedPhotoRepositoryImpl @Inject constructor(
    private val plannedPhotoDao: PlannedPhotoDao
) : PlannedPhotoRepository {

    override fun getPhotosForEvent(eventId: Long): Flow<List<PlannedPhoto>> =
        plannedPhotoDao.getPhotosForEvent(eventId).map { entities -> entities.map { it.toDomain() } }

    override fun getPhotoById(id: Long): Flow<PlannedPhoto?> =
        plannedPhotoDao.getPhotoById(id).map { it?.toDomain() }

    override suspend fun insertPhoto(photo: PlannedPhoto): Long =
        plannedPhotoDao.insertPhoto(photo.toEntity())

    override suspend fun updatePhoto(photo: PlannedPhoto) =
        plannedPhotoDao.updatePhoto(photo.toEntity())

    override suspend fun deletePhoto(id: Long) =
        plannedPhotoDao.deletePhotoById(id)

    override suspend fun getNextOrder(eventId: Long): Int =
        plannedPhotoDao.getNextOrder(eventId)

    override fun countForTrip(tripId: Long): Flow<Int> =
        plannedPhotoDao.countForTrip(tripId)
}
