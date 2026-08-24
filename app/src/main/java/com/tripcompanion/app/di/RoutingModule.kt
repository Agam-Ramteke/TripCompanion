package com.tripcompanion.app.di

import com.tripcompanion.app.BuildConfig
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
 * GraphHopper means changing the provider binding here and nothing else — no ViewModel, no screen.
 *
 * Unlike [TrainModule], there is no offline sibling to fall back to: routing has no local
 * projection to compute, so the provider reports itself unconfigured when the key is blank and the
 * service turns that into a straight-line value. One provider, always bound; the key decides
 * whether it actually reaches the network.
 */
@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {

    /**
     * The key from `local.properties`, surfaced through `BuildConfig` at build time.
     *
     * Empty when unset, which is the shipping default. Passed as a constructor argument rather than
     * read inside the provider so the missing-key path is testable without a build variant.
     */
    @Provides
    @Singleton
    @OpenRouteServiceApiKey
    fun provideOpenRouteServiceApiKey(): String = BuildConfig.OPENROUTESERVICE_API_KEY

    @Provides
    @Singleton
    fun provideRoutePlanProvider(impl: OpenRouteServiceRouteProvider): RoutePlanProvider = impl

    @Provides
    @Singleton
    fun provideRoutePlanService(impl: DefaultRoutePlanService): RoutePlanService = impl
}
