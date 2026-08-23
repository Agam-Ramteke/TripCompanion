package com.tripcompanion.app.domain.repository

import com.tripcompanion.app.domain.model.Activity
import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    fun getActivitiesForEvent(eventId: Long): Flow<List<Activity>>
    fun getActivityById(id: Long): Flow<Activity?>
    suspend fun insertActivity(activity: Activity): Long
    suspend fun updateActivity(activity: Activity)
    suspend fun deleteActivity(id: Long)
    suspend fun getNextOrder(eventId: Long): Int
    suspend fun updateCompletionStatus(id: Long, status: String)
}
