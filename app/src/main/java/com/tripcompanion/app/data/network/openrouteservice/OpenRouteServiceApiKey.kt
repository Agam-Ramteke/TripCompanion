package com.tripcompanion.app.data.network.openrouteservice

import javax.inject.Qualifier

/**
 * Marks the OpenRouteService API key, so injecting a `String` cannot pick up any other one.
 *
 * Declared next to the provider that needs it rather than in `di/`, because the data layer is the
 * only part of the app that knows this key exists — and taking the key as a constructor parameter,
 * instead of reading `BuildConfig` inline, is what lets a test drive the missing-key path without a
 * build variant. The twin of
 * [com.tripcompanion.app.data.network.railradar.RailRadarApiKey].
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenRouteServiceApiKey
