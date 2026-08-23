package com.tripcompanion.app.domain.repository

import com.tripcompanion.app.domain.model.StayDetails
import kotlinx.coroutines.flow.Flow

/** Hotel paperwork attached to a STAY event. */
interface StayDetailsRepository {
    fun getForEvent(eventId: Long): Flow<StayDetails?>
    suspend fun getForEventOnce(eventId: Long): StayDetails?

    /**
     * Every stay's paperwork, for screens that draw more than one stay.
     *
     * The itinerary shows a stay card per STAY event on the day and Home needs one for the
     * next-up backdrop; both want the rows keyed by event id rather than fetched one by one.
     */
    fun getAll(): Flow<List<StayDetails>>
    suspend fun save(details: StayDetails)
    suspend fun deleteForEvent(eventId: Long)
}
