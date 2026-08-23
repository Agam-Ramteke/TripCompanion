package com.tripcompanion.app.di

import com.tripcompanion.app.data.repository.*
import com.tripcompanion.app.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTripRepository(impl: TripRepositoryImpl): TripRepository

    @Binds
    @Singleton
    abstract fun bindEventRepository(impl: EventRepositoryImpl): EventRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindActivityRepository(impl: ActivityRepositoryImpl): ActivityRepository

    @Binds
    @Singleton
    abstract fun bindPlannedPhotoRepository(impl: PlannedPhotoRepositoryImpl): PlannedPhotoRepository

    @Binds
    @Singleton
    abstract fun bindTrainRepository(impl: TrainRepositoryImpl): TrainRepository

    @Binds
    @Singleton
    abstract fun bindStayDetailsRepository(impl: StayDetailsRepositoryImpl): StayDetailsRepository
}
