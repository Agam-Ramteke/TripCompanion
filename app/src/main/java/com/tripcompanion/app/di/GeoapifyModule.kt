package com.tripcompanion.app.di

import com.tripcompanion.app.BuildConfig
import com.tripcompanion.app.data.network.geoapify.GeoapifyApiKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GeoapifyModule {

    @Provides
    @Singleton
    @GeoapifyApiKey
    fun provideGeoapifyApiKey(): String = BuildConfig.GEOAPIFY_API_KEY
}
