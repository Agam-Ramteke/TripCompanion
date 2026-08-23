package com.tripcompanion.app.domain.repository

import com.tripcompanion.app.domain.model.Event
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun getEventsForTrip(tripId: Long): Flow<List<Event>>
    fun getEventById(id: Long): Flow<Event?>

    /** Itinerary entries that point at one place, across every trip. */
    fun getEventsForLocation(locationId: Long): Flow<List<Event>>
    suspend fun insertEvent(event: Event): Long
    suspend fun updateEvent(event: Event)
    suspend fun deleteEvent(id: Long)
    suspend fun getNextOrder(tripId: Long): Int
    suspend fun updateEventOrder(id: Long, order: Int)
}
