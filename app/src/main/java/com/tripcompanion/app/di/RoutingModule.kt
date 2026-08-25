package com.tripcompanion.app.di

import com.tripcompanion.app.BuildConfig
import com.tripcompanion.app.data.network.geoapify.GeoapifyRoutePlanProvider
import com.tripcompanion.app.data.network.openrouteservice.OpenRouteServiceApiKey
import com.tripcompanion.app.data.network.openrouteservice.OpenRouteServiceRouteProvider
import com.tripcompanion.app.data.routing.DefaultRoutePlanService
import com.tripcompanion.app.domain.service.RoutePlanProvider
import com.tripcompanion.app.domain.service.RoutePlanService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Where the app's road-route lines come from.
 *
 * The two-step binding §11 asks for, the routing twin of [SearchModule]: the app depends on
 * [RoutePlanService]; the service depends on a [RoutePlanProvider]. Swapping OpenRouteService for
 * Geoapify means changing the provider binding here and nothing else — no ViewModel, no screen.
 */
@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {

    @Provides
    @Singleton
    @OpenRouteServiceApiKey
    fun provideOpenRouteServiceApiKey(): String = BuildConfig.OPENROUTESERVICE_API_KEY

    @Provides
    @Singleton
    fun provideRoutePlanProvider(
        geoapifyImpl: GeoapifyRoutePlanProvider,
        orsImpl: OpenRouteServiceRouteProvider
    ): RoutePlanProvider {
        return if (BuildConfig.GEOAPIFY_API_KEY.isNotBlank()) {
            geoapifyImpl
        } else {
            orsImpl
        }
    }

    @Provides
    @Singleton
    fun provideRoutePlanService(impl: DefaultRoutePlanService): RoutePlanService = impl
}
