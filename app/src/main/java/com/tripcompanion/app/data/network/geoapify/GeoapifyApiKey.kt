package com.tripcompanion.app.data.network.geoapify

import javax.inject.Qualifier

/**
 * Distinguishes the Geoapify API key from other String injections.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeoapifyApiKey
