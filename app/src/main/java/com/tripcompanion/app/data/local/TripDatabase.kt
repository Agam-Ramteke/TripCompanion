package com.tripcompanion.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tripcompanion.app.data.local.converter.Converters
import com.tripcompanion.app.data.local.dao.*
import com.tripcompanion.app.data.local.entity.*

@Database(
    entities = [
        TripEntity::class,
        EventEntity::class,
        LocationEntity::class,
        ActivityEntity::class,
        PlannedPhotoEntity::class,
        TrainEntity::class,
        TrainStopEntity::class,
        TrainRunStatusEntity::class,
        TrainRunStopEntity::class,
        TrainPassengerEntity::class,
        StayDetailsEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TripDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun eventDao(): EventDao
    abstract fun locationDao(): LocationDao
    abstract fun activityDao(): ActivityDao
    abstract fun plannedPhotoDao(): PlannedPhotoDao
    abstract fun trainDao(): TrainDao
    abstract fun trainPassengerDao(): TrainPassengerDao
    abstract fun trainStopDao(): TrainStopDao
    abstract fun trainRunStatusDao(): TrainRunStatusDao
    abstract fun stayDetailsDao(): StayDetailsDao
}
