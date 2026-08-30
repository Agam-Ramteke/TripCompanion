package com.tripcompanion.app.data.repository

import com.tripcompanion.app.data.local.dao.EventDao
import com.tripcompanion.app.data.local.toDomain
import com.tripcompanion.app.data.local.toEntity
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao
) : EventRepository {

    override fun getEventsForTrip(tripId: Long): Flow<List<Event>> =
        eventDao.getEventsForTrip(tripId).map { entities -> entities.map { it.toDomain() } }

    override fun getEventById(id: Long): Flow<Event?> =
        eventDao.getEventById(id).map { it?.toDomain() }

    override suspend fun getEventByIdOnce(id: Long): Event? =
        eventDao.getEventByIdOnce(id)?.toDomain()

    override fun getEventsForLocation(locationId: Long): Flow<List<Event>> =
        eventDao.getEventsForLocation(locationId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun insertEvent(event: Event): Long =
        eventDao.insertEvent(event.toEntity())

    override suspend fun updateEvent(event: Event) =
        eventDao.updateEvent(event.copy(updatedAt = LocalDateTime.now()).toEntity())

    override suspend fun deleteEvent(id: Long) =
        eventDao.deleteEventById(id)

    override suspend fun getNextOrder(tripId: Long): Int =
        eventDao.getNextOrder(tripId)

    override suspend fun updateEventOrder(id: Long, order: Int) =
        eventDao.updateOrder(id, order)
}
