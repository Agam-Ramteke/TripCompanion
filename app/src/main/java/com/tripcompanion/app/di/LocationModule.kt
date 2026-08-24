package com.tripcompanion.app.di

import com.tripcompanion.app.data.location.AndroidDeviceLocationProvider
import com.tripcompanion.app.domain.service.DeviceLocationProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Where the map's "where am I" dot comes from.
 *
 * One binding, the §11 port pattern applied to location: the ViewModel depends on
 * [DeviceLocationProvider]; the framework-`LocationManager` implementation is bound here. Swapping to
 * a fused provider — were Play Services ever added — would change this line and nothing else, no
 * ViewModel and no screen.
 *
 * Bound in [SingletonComponent] so a single provider (and its last-known-fix seed) is shared, the
 * way [RoutingModule] and [TrainModule] bind theirs.
 */
@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideDeviceLocationProvider(
        impl: AndroidDeviceLocationProvider
    ): DeviceLocationProvider = impl
}
