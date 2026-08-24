package com.tripcompanion.app.di

import com.tripcompanion.app.BuildConfig
import com.tripcompanion.app.data.network.ScheduleProjectionTrainStatusProvider
import com.tripcompanion.app.data.network.railradar.RailRadarApiKey
import com.tripcompanion.app.data.network.railradar.RailRadarPnrLookupService
import com.tripcompanion.app.data.network.railradar.RailRadarTrainStatusProvider
import com.tripcompanion.app.data.train.DefaultTrainStatusService
import com.tripcompanion.app.domain.service.PnrLookupService
import com.tripcompanion.app.domain.service.TrainStatusProvider
import com.tripcompanion.app.domain.service.TrainStatusService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Picks which source of train information the app runs on.
 *
 * `@Provides` rather than `@Binds` because the choice is a decision, not an alias: with a key
 * configured the app tracks trains live through RailRadar, and without one it projects from the
 * stored timetable. Both satisfy [TrainStatusProvider], so nothing above this module can tell
 * which is in place except by asking [TrainStatusProvider.isLive] — which the live-tracking
 * screen does, in order to label a projected position as projected.
 *
 * The unselected provider is never constructed: [Provider] defers instantiation to the branch
 * that wins.
 */
@Module
@InstallIn(SingletonComponent::class)
object TrainModule {

    /**
     * The key from `local.properties`, surfaced through `BuildConfig` at build time.
     *
     * Empty when unset, which is the shipping default. It is passed as a constructor argument
     * rather than read inside the client so that the missing-key path is testable without a
     * build variant.
     */
    @Provides
    @Singleton
    @RailRadarApiKey
    fun provideRailRadarApiKey(): String = BuildConfig.RAILRADAR_API_KEY

    @Provides
    @Singleton
    fun provideTrainStatusProvider(
        live: Provider<RailRadarTrainStatusProvider>,
        projection: Provider<ScheduleProjectionTrainStatusProvider>
    ): TrainStatusProvider =
        if (BuildConfig.RAILRADAR_API_KEY.isNotBlank()) live.get() else projection.get()

    @Provides
    @Singleton
    fun provideTrainStatusService(impl: DefaultTrainStatusService): TrainStatusService = impl

    /**
     * PNR lookups always go through RailRadar, key or not: the implementation reports itself
     * unavailable when the key is blank, so the editor hides the affordance rather than the app
     * binding a different service.
     */
    @Provides
    @Singleton
    fun providePnrLookupService(impl: RailRadarPnrLookupService): PnrLookupService = impl
}
