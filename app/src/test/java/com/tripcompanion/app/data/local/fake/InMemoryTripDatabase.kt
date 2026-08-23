package com.tripcompanion.app.data.local.fake

import com.tripcompanion.app.data.local.dao.ActivityDao
import com.tripcompanion.app.data.local.dao.EventDao
import com.tripcompanion.app.data.local.dao.LocationDao
import com.tripcompanion.app.data.local.dao.PlannedPhotoDao
import com.tripcompanion.app.data.local.dao.StayDetailsDao
import com.tripcompanion.app.data.local.dao.TrainDao
import com.tripcompanion.app.data.local.dao.TrainPassengerDao
import com.tripcompanion.app.data.local.dao.TrainRunStatusDao
import com.tripcompanion.app.data.local.dao.TrainStopDao
import com.tripcompanion.app.data.local.dao.TripDao
import com.tripcompanion.app.data.local.entity.ActivityEntity
import com.tripcompanion.app.data.local.entity.EventEntity
import com.tripcompanion.app.data.local.entity.LocationEntity
import com.tripcompanion.app.data.local.entity.PlannedPhotoEntity
import com.tripcompanion.app.data.local.entity.StayDetailsEntity
import com.tripcompanion.app.data.local.entity.TrainEntity
import com.tripcompanion.app.data.local.entity.TrainPassengerEntity
import com.tripcompanion.app.data.local.entity.TrainRunStatusEntity
import com.tripcompanion.app.data.local.entity.TrainRunStopEntity
import com.tripcompanion.app.data.local.entity.TrainStopEntity
import com.tripcompanion.app.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

/**
 * An in-memory stand-in for [com.tripcompanion.app.data.local.TripDatabase].
 *
 * The point of implementing the DAO interfaces rather than the repository ones is
 * that the tests then run the *real* repositories, the real entity mappers and the
 * real state engine. What is faked is only SQLite — so a bug in a `toDomain()` or a
 * missed `updatedAt` still fails a test.
 *
 * Two behaviours are reproduced deliberately because tests depend on them:
 *
 * - **Ordering.** Every read applies the same `ORDER BY` its `@Query` declares. The
 *   event query in particular is the app's one comparator expressed in SQL (§10),
 *   and the ordering tests compare the two.
 * - **Cascade.** Room declares `ON DELETE CASCADE` from trips to events and trains, from
 *   events to activities, photos and hotel paperwork, and from trains to their party, their
 *   timetable and their cached status. Deleting a trip here removes its descendants the same way,
 *   so a test that deletes a trip and checks nothing of another trip's went with it is asking a
 *   real question.
 */
class InMemoryTripDatabase {

    private val trips = MutableStateFlow<List<TripEntity>>(emptyList())
    private val events = MutableStateFlow<List<EventEntity>>(emptyList())
    private val locations = MutableStateFlow<List<LocationEntity>>(emptyList())
    private val activities = MutableStateFlow<List<ActivityEntity>>(emptyList())
    private val photos = MutableStateFlow<List<PlannedPhotoEntity>>(emptyList())
    private val stayDetails = MutableStateFlow<List<StayDetailsEntity>>(emptyList())
    private val trains = MutableStateFlow<List<TrainEntity>>(emptyList())
    private val trainPassengers = MutableStateFlow<List<TrainPassengerEntity>>(emptyList())
    private val trainStops = MutableStateFlow<List<TrainStopEntity>>(emptyList())
    private val runStatus = MutableStateFlow<List<TrainRunStatusEntity>>(emptyList())
    private val runStops = MutableStateFlow<List<TrainRunStopEntity>>(emptyList())

    private var nextTripId = 1L
    private var nextEventId = 1L
    private var nextLocationId = 1L
    private var nextActivityId = 1L
    private var nextPhotoId = 1L
    private var nextTrainId = 1L
    private var nextPassengerId = 1L
    private var nextTrainStopId = 1L
    private var nextRunStopId = 1L

    val tripDao: TripDao = FakeTripDao()
    val eventDao: EventDao = FakeEventDao()
    val locationDao: LocationDao = FakeLocationDao()
    val activityDao: ActivityDao = FakeActivityDao()
    val plannedPhotoDao: PlannedPhotoDao = FakePlannedPhotoDao()
    val stayDetailsDao: StayDetailsDao = FakeStayDetailsDao()
    val trainDao: TrainDao = FakeTrainDao()
    val trainPassengerDao: TrainPassengerDao = FakeTrainPassengerDao()
    val trainStopDao: TrainStopDao = FakeTrainStopDao()
    val trainRunStatusDao: TrainRunStatusDao = FakeTrainRunStatusDao()

    /** Row counts, for assertions about what a cascade left behind. */
    fun rowCounts() = RowCounts(
        trips = trips.value.size,
        events = events.value.size,
        locations = locations.value.size,
        activities = activities.value.size,
        photos = photos.value.size,
        stayDetails = stayDetails.value.size,
        trains = trains.value.size,
        trainPassengers = trainPassengers.value.size,
        trainStops = trainStops.value.size,
        runStatus = runStatus.value.size,
        runStops = runStops.value.size
    )

    data class RowCounts(
        val trips: Int,
        val events: Int,
        val locations: Int,
        val activities: Int,
        val photos: Int,
        val stayDetails: Int = 0,
        val trains: Int = 0,
        val trainPassengers: Int = 0,
        val trainStops: Int = 0,
        val runStatus: Int = 0,
        val runStops: Int = 0
    )

    // ---- Trips -------------------------------------------------------------

    private inner class FakeTripDao : TripDao {
        override fun getAllTrips(): Flow<List<TripEntity>> =
            trips.map { rows -> rows.sortedBy { it.startDate } }

        override fun getTripById(id: Long): Flow<TripEntity?> =
            trips.map { rows -> rows.firstOrNull { it.id == id } }

        override suspend fun getTripByIdOnce(id: Long): TripEntity? =
            trips.value.firstOrNull { it.id == id }

        override suspend fun insertTrip(trip: TripEntity): Long {
            val id = if (trip.id == 0L) nextTripId++ else trip.id
            trips.value = trips.value.filterNot { it.id == id } + trip.copy(id = id)
            return id
        }

        override suspend fun updateTrip(trip: TripEntity) {
            if (trips.value.none { it.id == trip.id }) return
            trips.value = trips.value.map { if (it.id == trip.id) trip else it }
        }

        override suspend fun deleteTrip(trip: TripEntity) = deleteTripById(trip.id)

        override suspend fun deleteTripById(id: Long) {
            trips.value = trips.value.filterNot { it.id == id }
            // ON DELETE CASCADE, one level at a time so the grandchildren go too.
            events.value.filter { it.tripId == id }.forEach { cascadeDeleteEvent(it.id) }
            // Trains cascade from the trip as well, and carry their own two children.
            trains.value.filter { it.tripId == id }.forEach { cascadeDeleteTrain(it.id) }
        }
    }

    // ---- Events ------------------------------------------------------------

    private inner class FakeEventDao : EventDao {
        override fun getEventsForTrip(tripId: Long): Flow<List<EventEntity>> =
            events.map { rows ->
                rows.filter { it.tripId == tripId }
                    .sortedWith(
                        compareBy<EventEntity> { it.startTime }
                            .thenBy { it.order }
                            .thenBy { it.id }
                    )
            }

        override fun getEventById(id: Long): Flow<EventEntity?> =
            events.map { rows -> rows.firstOrNull { it.id == id } }

        override fun getEventsForLocation(locationId: Long): Flow<List<EventEntity>> =
            events.map { rows ->
                rows.filter { it.locationId == locationId }
                    .sortedWith(
                        compareBy<EventEntity> { it.startTime }
                            .thenBy { it.order }
                            .thenBy { it.id }
                    )
            }

        override suspend fun getEventByIdOnce(id: Long): EventEntity? =
            events.value.firstOrNull { it.id == id }

        override suspend fun insertEvent(event: EventEntity): Long {
            val id = if (event.id == 0L) nextEventId++ else event.id
            events.value = events.value.filterNot { it.id == id } + event.copy(id = id)
            return id
        }

        override suspend fun updateEvent(event: EventEntity) {
            if (events.value.none { it.id == event.id }) return
            events.value = events.value.map { if (it.id == event.id) event else it }
        }

        override suspend fun deleteEvent(event: EventEntity) = deleteEventById(event.id)

        override suspend fun deleteEventById(id: Long) = cascadeDeleteEvent(id)

        override suspend fun getNextOrder(tripId: Long): Int =
            (events.value.filter { it.tripId == tripId }.maxOfOrNull { it.order } ?: -1) + 1

        override suspend fun updateOrder(id: Long, order: Int) {
            events.value = events.value.map { if (it.id == id) it.copy(order = order) else it }
        }
    }

    private fun cascadeDeleteEvent(eventId: Long) {
        events.value = events.value.filterNot { it.id == eventId }
        activities.value = activities.value.filterNot { it.eventId == eventId }
        photos.value = photos.value.filterNot { it.eventId == eventId }
        stayDetails.value = stayDetails.value.filterNot { it.eventId == eventId }
    }

    // ---- Locations ---------------------------------------------------------

    private inner class FakeLocationDao : LocationDao {
        override fun getAllLocations(): Flow<List<LocationEntity>> =
            locations.map { rows -> rows.sortedBy { it.name } }

        override fun getLocationById(id: Long): Flow<LocationEntity?> =
            locations.map { rows -> rows.firstOrNull { it.id == id } }

        override suspend fun getLocationByIdOnce(id: Long): LocationEntity? =
            locations.value.firstOrNull { it.id == id }

        override suspend fun insertLocation(location: LocationEntity): Long {
            val id = if (location.id == 0L) nextLocationId++ else location.id
            locations.value = locations.value.filterNot { it.id == id } + location.copy(id = id)
            return id
        }

        override suspend fun updateLocation(location: LocationEntity) {
            if (locations.value.none { it.id == location.id }) return
            locations.value = locations.value.map { if (it.id == location.id) location else it }
        }

        override suspend fun deleteLocation(location: LocationEntity) =
            deleteLocationById(location.id)

        override suspend fun deleteLocationById(id: Long) {
            locations.value = locations.value.filterNot { it.id == id }
        }

        // The three Places tabs. Each mirrors its @Query's ORDER BY, because the tab order
        // is what the tests assert and getting it from the fake for free would prove nothing.

        override fun getPlacesToVisit(): Flow<List<LocationEntity>> =
            locations.map { rows -> rows.filterNot { it.isVisited }.sortedBy { it.name } }

        override fun getVisitedPlaces(): Flow<List<LocationEntity>> =
            locations.map { rows ->
                rows.filter { it.isVisited }
                    .sortedWith(compareByDescending<LocationEntity> { it.updatedAt }.thenBy { it.name })
            }

        override fun getSavedPlaces(): Flow<List<LocationEntity>> =
            locations.map { rows -> rows.filter { it.isSaved }.sortedBy { it.name } }

        override suspend fun setSaved(id: Long, saved: Boolean, updatedAt: LocalDateTime) {
            locations.value = locations.value.map {
                if (it.id == id) it.copy(isSaved = saved, updatedAt = updatedAt) else it
            }
        }

        override suspend fun setVisited(id: Long, visited: Boolean, updatedAt: LocalDateTime) {
            locations.value = locations.value.map {
                if (it.id == id) it.copy(isVisited = visited, updatedAt = updatedAt) else it
            }
        }
    }

    // ---- Activities (legacy, §3) -------------------------------------------

    private inner class FakeActivityDao : ActivityDao {
        override fun getActivitiesForEvent(eventId: Long): Flow<List<ActivityEntity>> =
            activities.map { rows ->
                rows.filter { it.eventId == eventId }.sortedBy { it.order }
            }

        override fun getActivityById(id: Long): Flow<ActivityEntity?> =
            activities.map { rows -> rows.firstOrNull { it.id == id } }

        override suspend fun getActivityByIdOnce(id: Long): ActivityEntity? =
            activities.value.firstOrNull { it.id == id }

        override suspend fun insertActivity(activity: ActivityEntity): Long {
            val id = if (activity.id == 0L) nextActivityId++ else activity.id
            activities.value = activities.value.filterNot { it.id == id } + activity.copy(id = id)
            return id
        }

        override suspend fun updateActivity(activity: ActivityEntity) {
            if (activities.value.none { it.id == activity.id }) return
            activities.value = activities.value.map { if (it.id == activity.id) activity else it }
        }

        override suspend fun deleteActivity(activity: ActivityEntity) =
            deleteActivityById(activity.id)

        override suspend fun deleteActivityById(id: Long) {
            activities.value = activities.value.filterNot { it.id == id }
        }

        override suspend fun getNextOrder(eventId: Long): Int =
            (activities.value.filter { it.eventId == eventId }.maxOfOrNull { it.order } ?: -1) + 1

        override suspend fun updateCompletionStatus(id: Long, status: String) {
            activities.value = activities.value.map {
                if (it.id == id) it.copy(completionStatus = status) else it
            }
        }
    }

    // ---- Planned photos ----------------------------------------------------

    private inner class FakePlannedPhotoDao : PlannedPhotoDao {
        override fun getPhotosForEvent(eventId: Long): Flow<List<PlannedPhotoEntity>> =
            photos.map { rows -> rows.filter { it.eventId == eventId }.sortedBy { it.order } }

        override fun getPhotoById(id: Long): Flow<PlannedPhotoEntity?> =
            photos.map { rows -> rows.firstOrNull { it.id == id } }

        override suspend fun getPhotoByIdOnce(id: Long): PlannedPhotoEntity? =
            photos.value.firstOrNull { it.id == id }

        override suspend fun insertPhoto(photo: PlannedPhotoEntity): Long {
            val id = if (photo.id == 0L) nextPhotoId++ else photo.id
            photos.value = photos.value.filterNot { it.id == id } + photo.copy(id = id)
            return id
        }

        override suspend fun updatePhoto(photo: PlannedPhotoEntity) {
            if (photos.value.none { it.id == photo.id }) return
            photos.value = photos.value.map { if (it.id == photo.id) photo else it }
        }

        override suspend fun deletePhoto(photo: PlannedPhotoEntity) = deletePhotoById(photo.id)

        override suspend fun deletePhotoById(id: Long) {
            photos.value = photos.value.filterNot { it.id == id }
        }

        override suspend fun getNextOrder(eventId: Long): Int =
            (photos.value.filter { it.eventId == eventId }.maxOfOrNull { it.order } ?: -1) + 1

        /**
         * Photos across a whole trip, reached through its events.
         *
         * The real query is a subselect on `events`, so the fake joins the same way rather
         * than counting a denormalised column that does not exist. Combined over both flows
         * because Room re-runs the query when either table changes.
         */
        override fun countForTrip(tripId: Long): Flow<Int> =
            combine(photos, events) { photoRows, eventRows ->
                val eventIds = eventRows.filter { it.tripId == tripId }.map { it.id }.toSet()
                photoRows.count { it.eventId in eventIds }
            }
    }

    // ---- Stay details -------------------------------------------------------

    /**
     * Hotel paperwork, one row per STAY event.
     *
     * `eventId` is the primary key rather than a generated id, so [upsert] replaces instead of
     * appending — the same REPLACE the real `@Insert` declares. A fake that appended would let a
     * second save silently double the row and no test would notice.
     */
    private inner class FakeStayDetailsDao : StayDetailsDao {
        override fun getForEvent(eventId: Long): Flow<StayDetailsEntity?> =
            stayDetails.map { rows -> rows.firstOrNull { it.eventId == eventId } }

        override suspend fun getForEventOnce(eventId: Long): StayDetailsEntity? =
            stayDetails.value.firstOrNull { it.eventId == eventId }

        override fun getAll(): Flow<List<StayDetailsEntity>> = stayDetails

        override suspend fun upsert(details: StayDetailsEntity) {
            stayDetails.value =
                stayDetails.value.filterNot { it.eventId == details.eventId } + details
        }

        override suspend fun deleteForEvent(eventId: Long) {
            stayDetails.value = stayDetails.value.filterNot { it.eventId == eventId }
        }
    }

    // ---- Trains -------------------------------------------------------------

    private inner class FakeTrainDao : TrainDao {
        override fun getTrainsForTrip(tripId: Long): Flow<List<TrainEntity>> =
            trains.map { rows -> rows.filter { it.tripId == tripId }.sortedByRunOrder() }

        override fun getAllTrains(): Flow<List<TrainEntity>> =
            trains.map { rows -> rows.sortedByRunOrder() }

        override fun getTrainById(id: Long): Flow<TrainEntity?> =
            trains.map { rows -> rows.firstOrNull { it.id == id } }

        override suspend fun getTrainByIdOnce(id: Long): TrainEntity? =
            trains.value.firstOrNull { it.id == id }

        /** `LIMIT 1` on an advisory link, so the lowest id wins exactly as the query does. */
        override fun getTrainForEvent(eventId: Long): Flow<TrainEntity?> =
            trains.map { rows ->
                rows.filter { it.eventId == eventId }.minByOrNull { it.id }
            }

        override suspend fun insertTrain(train: TrainEntity): Long {
            val id = if (train.id == 0L) nextTrainId++ else train.id
            trains.value = trains.value.filterNot { it.id == id } + train.copy(id = id)
            return id
        }

        override suspend fun updateTrain(train: TrainEntity) {
            if (trains.value.none { it.id == train.id }) return
            trains.value = trains.value.map { if (it.id == train.id) train else it }
        }

        override suspend fun deleteTrainById(id: Long) = cascadeDeleteTrain(id)

        private fun List<TrainEntity>.sortedByRunOrder() =
            sortedWith(compareBy<TrainEntity> { it.departureTime }.thenBy { it.id })
    }

    /** CASCADE from `trains` to the party, the timetable and both halves of the cached snapshot. */
    private fun cascadeDeleteTrain(trainId: Long) {
        trains.value = trains.value.filterNot { it.id == trainId }
        trainPassengers.value = trainPassengers.value.filterNot { it.trainId == trainId }
        trainStops.value = trainStops.value.filterNot { it.trainId == trainId }
        runStatus.value = runStatus.value.filterNot { it.trainId == trainId }
        runStops.value = runStops.value.filterNot { it.trainId == trainId }
    }

    /**
     * `replacePassengers` is not overridden: it is a `@Transaction` method with a body on the
     * interface, so the real delete-then-insert runs against this fake unchanged.
     */
    private inner class FakeTrainPassengerDao : TrainPassengerDao {
        override fun getPassengersForTrain(trainId: Long): Flow<List<TrainPassengerEntity>> =
            trainPassengers.map { rows -> rows.filter { it.trainId == trainId }.inChartOrder() }

        override suspend fun getPassengersForTrainOnce(trainId: Long): List<TrainPassengerEntity> =
            trainPassengers.value.filter { it.trainId == trainId }.inChartOrder()

        override fun getAllPassengers(): Flow<List<TrainPassengerEntity>> =
            trainPassengers.map { rows ->
                rows.sortedWith(
                    compareBy<TrainPassengerEntity> { it.trainId }
                        .thenBy { it.serialNo }
                        .thenBy { it.id }
                )
            }

        override suspend fun insertPassengers(passengers: List<TrainPassengerEntity>) {
            val incoming = passengers.map { row ->
                if (row.id == 0L) row.copy(id = nextPassengerId++) else row
            }
            val replaced = incoming.map { it.id }.toSet()
            trainPassengers.value =
                trainPassengers.value.filterNot { it.id in replaced } + incoming
        }

        override suspend fun deletePassengersForTrain(trainId: Long) {
            trainPassengers.value = trainPassengers.value.filterNot { it.trainId == trainId }
        }

        private fun List<TrainPassengerEntity>.inChartOrder() =
            sortedWith(compareBy<TrainPassengerEntity> { it.serialNo }.thenBy { it.id })
    }

    private inner class FakeTrainStopDao : TrainStopDao {
        override fun getStopsForTrain(trainId: Long): Flow<List<TrainStopEntity>> =
            trainStops.map { rows ->
                rows.filter { it.trainId == trainId }.sortedBy { it.serialNo }
            }

        override suspend fun getStopsForTrainOnce(trainId: Long): List<TrainStopEntity> =
            trainStops.value.filter { it.trainId == trainId }.sortedBy { it.serialNo }

        override suspend fun countStopsForTrain(trainId: Long): Int =
            trainStops.value.count { it.trainId == trainId }

        override suspend fun insertStops(stops: List<TrainStopEntity>) {
            trainStops.value = trainStops.value + stops.map { stop ->
                if (stop.id == 0L) stop.copy(id = nextTrainStopId++) else stop
            }
        }

        override suspend fun deleteStopsForTrain(trainId: Long) {
            trainStops.value = trainStops.value.filterNot { it.trainId == trainId }
        }
    }

    private inner class FakeTrainRunStatusDao : TrainRunStatusDao {
        override fun getStatus(trainId: Long): Flow<TrainRunStatusEntity?> =
            runStatus.map { rows -> rows.firstOrNull { it.trainId == trainId } }

        override suspend fun getStatusOnce(trainId: Long): TrainRunStatusEntity? =
            runStatus.value.firstOrNull { it.trainId == trainId }

        override fun getRunStops(trainId: Long): Flow<List<TrainRunStopEntity>> =
            runStops.map { rows ->
                rows.filter { it.trainId == trainId }.sortedBy { it.serialNo }
            }

        override suspend fun getRunStopsOnce(trainId: Long): List<TrainRunStopEntity> =
            runStops.value.filter { it.trainId == trainId }.sortedBy { it.serialNo }

        /** `trainId` is the primary key here, so a second write replaces rather than adds. */
        override suspend fun upsertStatus(status: TrainRunStatusEntity) {
            runStatus.value =
                runStatus.value.filterNot { it.trainId == status.trainId } + status
        }

        override suspend fun insertRunStops(stops: List<TrainRunStopEntity>) {
            runStops.value = runStops.value + stops.map { stop ->
                if (stop.id == 0L) stop.copy(id = nextRunStopId++) else stop
            }
        }

        override suspend fun deleteRunStops(trainId: Long) {
            runStops.value = runStops.value.filterNot { it.trainId == trainId }
        }

        override suspend fun deleteStatus(trainId: Long) {
            runStatus.value = runStatus.value.filterNot { it.trainId == trainId }
        }
    }
}
