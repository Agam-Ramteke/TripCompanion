package com.tripcompanion.app.data.network.locationiq

import javax.inject.Qualifier

/**
 * Distinguishes the LocationIQ API key from other String injections.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocationIqApiKey
