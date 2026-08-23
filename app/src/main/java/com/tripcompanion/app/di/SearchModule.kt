package com.tripcompanion.app.di

import com.tripcompanion.app.data.network.NominatimLocationSearchProvider
import com.tripcompanion.app.data.search.DefaultLocationSearchService
import com.tripcompanion.app.domain.service.LocationSearchProvider
import com.tripcompanion.app.domain.service.LocationSearchService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The two-step binding §11 asks for.
 *
 * The app depends on [LocationSearchService]; the service depends on a
 * [LocationSearchProvider]. Swapping Nominatim for Photon means changing the
 * second binding here and nothing else — no ViewModel, no screen, no test of
 * search policy.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {

    @Binds
    @Singleton
    abstract fun bindLocationSearchProvider(
        provider: NominatimLocationSearchProvider
    ): LocationSearchProvider

    @Binds
    @Singleton
    abstract fun bindLocationSearchService(
        service: DefaultLocationSearchService
    ): LocationSearchService
}
