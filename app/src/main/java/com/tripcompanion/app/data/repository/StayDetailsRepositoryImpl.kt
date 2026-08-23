package com.tripcompanion.app.data.repository

import com.tripcompanion.app.data.local.dao.StayDetailsDao
import com.tripcompanion.app.data.local.toDomain
import com.tripcompanion.app.data.local.toEntity
import com.tripcompanion.app.domain.model.StayDetails
import com.tripcompanion.app.domain.repository.StayDetailsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StayDetailsRepositoryImpl @Inject constructor(
    private val stayDetailsDao: StayDetailsDao
) : StayDetailsRepository {

    override fun getForEvent(eventId: Long): Flow<StayDetails?> =
        stayDetailsDao.getForEvent(eventId).map { it?.toDomain() }

    override suspend fun getForEventOnce(eventId: Long): StayDetails? =
        stayDetailsDao.getForEventOnce(eventId)?.toDomain()

    override fun getAll(): Flow<List<StayDetails>> =
        stayDetailsDao.getAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(details: StayDetails) =
        stayDetailsDao.upsert(details.toEntity())

    override suspend fun deleteForEvent(eventId: Long) =
        stayDetailsDao.deleteForEvent(eventId)
}
