package com.tripcompanion.app.di

import com.tripcompanion.app.BuildConfig
import com.tripcompanion.app.data.network.NominatimLocationSearchProvider
import com.tripcompanion.app.data.network.maptiler.MapTilerLocationSearchProvider
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
        nominatimProvider: NominatimLocationSearchProvider,
        mapTilerProvider: MapTilerLocationSearchProvider
    ): LocationSearchProvider {
        return if (BuildConfig.MAPTILER_API_KEY.isNotBlank()) {
            mapTilerProvider
        } else {
            nominatimProvider
        }
    }

    @Provides
    @Singleton
    fun provideLocationSearchService(
        service: DefaultLocationSearchService
    ): LocationSearchService {
        return service
    }
}
