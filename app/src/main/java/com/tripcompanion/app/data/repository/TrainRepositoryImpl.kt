package com.tripcompanion.app.data.repository

import com.tripcompanion.app.data.local.dao.TrainDao
import com.tripcompanion.app.data.local.dao.TrainPassengerDao
import com.tripcompanion.app.data.local.dao.TrainRunStatusDao
import com.tripcompanion.app.data.local.dao.TrainStopDao
import com.tripcompanion.app.data.local.entity.TrainPassengerEntity
import com.tripcompanion.app.data.local.toDomain
import com.tripcompanion.app.data.local.toEntity
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainPassenger
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.repository.TrainRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrainRepositoryImpl @Inject constructor(
    private val trainDao: TrainDao,
    private val trainPassengerDao: TrainPassengerDao,
    private val trainStopDao: TrainStopDao,
    private val trainRunStatusDao: TrainRunStatusDao
) : TrainRepository {

    // ── The booking ──

    /**
     * Trains joined to their parties in the collector.
     *
     * Every passenger in the database is read as one flow and grouped by train id, rather than a
     * query taking the list of visible train ids. A parameterised query would not re-run when
     * that list changed — adding a train would need the flow resubscribed, and the screen would
     * flicker through an empty state to get there. A trip has a handful of trains, so grouping in
     * memory costs nothing and always agrees with the list beside it.
     */
    override fun getTrainsForTrip(tripId: Long): Flow<List<Train>> =
        combine(
            trainDao.getTrainsForTrip(tripId),
            trainPassengerDao.getAllPassengers()
        ) { trains, passengers -> trains.withParties(passengers) }

    override fun getAllTrains(): Flow<List<Train>> =
        combine(
            trainDao.getAllTrains(),
            trainPassengerDao.getAllPassengers()
        ) { trains, passengers -> trains.withParties(passengers) }

    override fun getTrainById(id: Long): Flow<Train?> =
        combine(
            trainDao.getTrainById(id),
            trainPassengerDao.getPassengersForTrain(id)
        ) { train, passengers ->
            train?.toDomain(passengers.map { it.toDomain() })
        }

    override suspend fun getTrainByIdOnce(id: Long): Train? =
        trainDao.getTrainByIdOnce(id)?.let { entity ->
            entity.toDomain(trainPassengerDao.getPassengersForTrainOnce(id).map { it.toDomain() })
        }

    override fun getTrainForEvent(eventId: Long): Flow<Train?> =
        combine(
            trainDao.getTrainForEvent(eventId),
            trainPassengerDao.getAllPassengers()
        ) { train, passengers ->
            train?.toDomain(passengers.filter { it.trainId == train.id }.map { it.toDomain() })
        }

    /**
     * The booking first, then its party, because a passenger row needs the train's id.
     *
     * The id Room hands back is the one the passengers are written against, which is why the
     * caller's [Train.id] is not used here: on an insert it is still zero.
     */
    override suspend fun insertTrain(train: Train): Long {
        val trainId = trainDao.insertTrain(train.toEntity())
        trainPassengerDao.replacePassengers(trainId, train.passengers.forInsertUnder(trainId))
        return trainId
    }

    override suspend fun updateTrain(train: Train) {
        trainDao.updateTrain(train.copy(updatedAt = LocalDateTime.now()).toEntity())
        trainPassengerDao.replacePassengers(train.id, train.passengers.forInsertUnder(train.id))
    }

    override suspend fun deleteTrain(id: Long) =
        trainDao.deleteTrainById(id)

    /**
     * Ids are dropped, exactly as in [replaceSchedule]: the previous rows have just been deleted,
     * so every one of these is new, and carrying an old id over would resurrect a row the
     * replace was meant to remove.
     */
    private fun List<TrainPassenger>.forInsertUnder(trainId: Long): List<TrainPassengerEntity> =
        map { it.copy(id = 0).toEntity(trainId) }

    private fun List<com.tripcompanion.app.data.local.entity.TrainEntity>.withParties(
        passengers: List<TrainPassengerEntity>
    ): List<Train> {
        val byTrain = passengers.groupBy { it.trainId }
        return map { entity ->
            entity.toDomain(byTrain[entity.id].orEmpty().map { it.toDomain() })
        }
    }

    // ── The timetable ──

    override fun getStops(trainId: Long): Flow<List<TrainStop>> =
        trainStopDao.getStopsForTrain(trainId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getStopsOnce(trainId: Long): List<TrainStop> =
        trainStopDao.getStopsForTrainOnce(trainId).map { it.toDomain() }

    override suspend fun hasSchedule(trainId: Long): Boolean =
        trainStopDao.countStopsForTrain(trainId) > 0

    override suspend fun replaceSchedule(trainId: Long, stops: List<TrainStop>) {
        // Ids are dropped rather than carried over: these rows are new, and reusing an id
        // from a provider-built stop (always 0) would collide on the second stop.
        trainStopDao.replaceStops(
            trainId = trainId,
            stops = stops.map { it.copy(id = 0).toEntity(trainId) }
        )
    }

    // ── The running status ──

    /**
     * Header and stop rows joined in the collector.
     *
     * `combine` re-emits when either table changes, which is what makes a refresh appear on
     * screen without the ViewModel polling. Both halves are always written in one transaction
     * ([TrainRunStatusDao.replaceSnapshot]), so the brief moment where the header is new and
     * the stops are old never escapes the database.
     */
    override fun observeRunStatus(trainId: Long): Flow<TrainRunStatus?> =
        combine(
            trainRunStatusDao.getStatus(trainId),
            trainRunStatusDao.getRunStops(trainId)
        ) { status, stops ->
            status?.toDomain(stops)
        }

    override suspend fun getRunStatusOnce(trainId: Long): TrainRunStatus? {
        val header = trainRunStatusDao.getStatusOnce(trainId) ?: return null
        return header.toDomain(trainRunStatusDao.getRunStopsOnce(trainId))
    }

    override suspend fun saveRunStatus(status: TrainRunStatus) {
        trainRunStatusDao.replaceSnapshot(
            status = status.toEntity(),
            stops = status.stops.map { it.toEntity(status.trainId) }
        )
    }

    override suspend fun clearRunStatus(trainId: Long) =
        trainRunStatusDao.clearSnapshot(trainId)
}
