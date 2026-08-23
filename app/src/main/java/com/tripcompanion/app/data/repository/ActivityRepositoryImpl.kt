package com.tripcompanion.app.data.repository

import com.tripcompanion.app.data.local.dao.ActivityDao
import com.tripcompanion.app.data.local.toDomain
import com.tripcompanion.app.data.local.toEntity
import com.tripcompanion.app.domain.model.Activity
import com.tripcompanion.app.domain.repository.ActivityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepositoryImpl @Inject constructor(
    private val activityDao: ActivityDao
) : ActivityRepository {

    override fun getActivitiesForEvent(eventId: Long): Flow<List<Activity>> =
        activityDao.getActivitiesForEvent(eventId).map { entities -> entities.map { it.toDomain() } }

    override fun getActivityById(id: Long): Flow<Activity?> =
        activityDao.getActivityById(id).map { it?.toDomain() }

    override suspend fun insertActivity(activity: Activity): Long =
        activityDao.insertActivity(activity.toEntity())

    override suspend fun updateActivity(activity: Activity) =
        activityDao.updateActivity(activity.toEntity())

    override suspend fun deleteActivity(id: Long) =
        activityDao.deleteActivityById(id)

    override suspend fun getNextOrder(eventId: Long): Int =
        activityDao.getNextOrder(eventId)

    override suspend fun updateCompletionStatus(id: Long, status: String) =
        activityDao.updateCompletionStatus(id, status)
}
