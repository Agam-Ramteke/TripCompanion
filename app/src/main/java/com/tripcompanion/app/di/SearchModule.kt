package com.tripcompanion.app.di

import com.tripcompanion.app.data.network.locationiq.LocationIqLocationSearchProvider
import com.tripcompanion.app.data.search.DefaultLocationSearchService
import com.tripcompanion.app.domain.service.LocationSearchProvider
import com.tripcompanion.app.domain.service.LocationSearchService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SearchModule {

    @Provides
    @Singleton
    fun provideLocationSearchProvider(
        locationIqProvider: LocationIqLocationSearchProvider
    ): LocationSearchProvider {
        return locationIqProvider
    }

    @Provides
    @Singleton
    fun provideLocationSearchService(
        service: DefaultLocationSearchService
    ): LocationSearchService {
        return service
    }
}
