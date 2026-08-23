package com.tripcompanion.app.di

import com.tripcompanion.app.core.time.SystemTimeProvider
import com.tripcompanion.app.core.time.TimeProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the clock.
 *
 * Injecting time rather than calling `LocalDateTime.now()` is what makes the state
 * engine's six statuses reachable in a test (§21, §28) — a test replaces this
 * binding with a `FixedTimeProvider` and places the app at any instant it likes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    @Singleton
    abstract fun bindTimeProvider(provider: SystemTimeProvider): TimeProvider
}
