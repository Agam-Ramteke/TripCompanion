package com.tripcompanion.app.di

import com.tripcompanion.app.BuildConfig
import com.tripcompanion.app.data.network.locationiq.LocationIqApiKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationIqModule {

    @Provides
    @Singleton
    @LocationIqApiKey
    fun provideLocationIqApiKey(): String = BuildConfig.LOCATIONIQ_API_KEY
}
