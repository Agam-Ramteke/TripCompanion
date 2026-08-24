package com.tripcompanion.app.data.transfer

import javax.inject.Qualifier

/**
 * Marks the directory transferred photographs are read from and written to.
 *
 * A `File` rather than a `Context` so [TripTransferServiceImpl] has no Android import in it and
 * can be tested against a temporary directory on a plain JVM. Declared beside the consumer, the
 * way [com.tripcompanion.app.data.network.railradar.RailRadarApiKey] is, because nothing else in the app
 * injects a bare `File` and a qualifier in `di/` would suggest otherwise.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TripImagesDir
