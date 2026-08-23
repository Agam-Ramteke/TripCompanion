package com.tripcompanion.app.di

import android.content.Context
import androidx.room.Room
import com.tripcompanion.app.data.local.Migrations
import com.tripcompanion.app.data.local.TripDatabase
import com.tripcompanion.app.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TripDatabase =
        Room.databaseBuilder(
            context,
            TripDatabase::class.java,
            "trip_companion.db"
        )
        // No fallbackToDestructiveMigration: a trip plan the user typed by hand
        // is not disposable, so an unhandled version must fail loudly instead.
        .addMigrations(*Migrations.ALL)
        .build()

    @Provides
    fun provideTripDao(db: TripDatabase): TripDao = db.tripDao()

    @Provides
    fun provideEventDao(db: TripDatabase): EventDao = db.eventDao()

    @Provides
    fun provideLocationDao(db: TripDatabase): LocationDao = db.locationDao()

    @Provides
    fun provideActivityDao(db: TripDatabase): ActivityDao = db.activityDao()

    @Provides
    fun providePlannedPhotoDao(db: TripDatabase): PlannedPhotoDao = db.plannedPhotoDao()

    @Provides
    fun provideTrainDao(db: TripDatabase): TrainDao = db.trainDao()

    @Provides
    fun provideTrainStopDao(db: TripDatabase): TrainStopDao = db.trainStopDao()

    @Provides
    fun provideTrainPassengerDao(db: TripDatabase): TrainPassengerDao = db.trainPassengerDao()

    @Provides
    fun provideTrainRunStatusDao(db: TripDatabase): TrainRunStatusDao = db.trainRunStatusDao()

    @Provides
    fun provideStayDetailsDao(db: TripDatabase): StayDetailsDao = db.stayDetailsDao()
}
