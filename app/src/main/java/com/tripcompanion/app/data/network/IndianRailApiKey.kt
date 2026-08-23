package com.tripcompanion.app.data.network

import javax.inject.Qualifier

/**
 * Marks the indianrailapi.com key, so injecting a `String` cannot pick up any other one.
 *
 * Declared next to the provider that needs it rather than in `di/`, because the provider is
 * the only thing in the app that knows this key exists — and taking the key as a constructor
 * parameter, instead of reading `BuildConfig` inline, is what lets a test drive the
 * missing-key path without a build variant.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IndianRailApiKey
