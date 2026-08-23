package com.tripcompanion.app.domain.repository

import com.tripcompanion.app.domain.model.PlannedPhoto
import kotlinx.coroutines.flow.Flow

interface PlannedPhotoRepository {
    fun getPhotosForEvent(eventId: Long): Flow<List<PlannedPhoto>>
    fun getPhotoById(id: Long): Flow<PlannedPhoto?>
    suspend fun insertPhoto(photo: PlannedPhoto): Long
    suspend fun updatePhoto(photo: PlannedPhoto)
    suspend fun deletePhoto(id: Long)
    suspend fun getNextOrder(eventId: Long): Int

    /** Photo plans across every event of a trip — the count behind Home's Photos stat. */
    fun countForTrip(tripId: Long): Flow<Int>
}
