package com.tripcompanion.app.data.repository

import com.tripcompanion.app.data.local.dao.TripDao
import com.tripcompanion.app.data.local.toDomain
import com.tripcompanion.app.data.local.toEntity
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao
) : TripRepository {

    override fun getAllTrips(): Flow<List<Trip>> =
        tripDao.getAllTrips().map { entities -> entities.map { it.toDomain() } }

    override fun getTripById(id: Long): Flow<Trip?> =
        tripDao.getTripById(id).map { it?.toDomain() }

    override suspend fun insertTrip(trip: Trip): Long =
        tripDao.insertTrip(trip.toEntity())

    override suspend fun updateTrip(trip: Trip) =
        tripDao.updateTrip(trip.copy(updatedAt = LocalDateTime.now()).toEntity())

    override suspend fun deleteTrip(id: Long) =
        tripDao.deleteTripById(id)
}
