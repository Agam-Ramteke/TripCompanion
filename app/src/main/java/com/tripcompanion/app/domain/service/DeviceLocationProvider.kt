package com.tripcompanion.app.domain.service

import kotlinx.coroutines.flow.Flow

/**
 * Where the device currently is (§11, §13).
 *
 * Android-free like [RoutePoint]: latitude and longitude in degrees, and [accuracyMeters] as the
 * radius the platform is confident the true position lies within. Accuracy is nullable because some
 * fixes carry no estimate — the map then draws the dot without a ring rather than inventing a
 * precision it does not have.
 */
data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?
)

/**
 * A stream of the device's own position (§11).
 *
 * The map's "where am I" dot, behind a port so nothing above `data/` touches Android's
 * `LocationManager` (or a Play Services client) directly — the same boundary routing and train
 * status sit behind. A null emission means "no fix yet / location unavailable": an ordinary state,
 * not an error, so it is a value in the stream rather than an exception.
 *
 * The caller holds the runtime permission before it collects; an implementation without permission
 * simply never emits a non-null fix, so the screen never has to catch a `SecurityException`.
 */
interface DeviceLocationProvider {

    /**
     * Cold stream of fixes, newest wins, emitting null when there is no current fix. Collecting
     * starts the underlying updates and cancelling stops them, so a screen that isn't listening
     * costs no battery.
     */
    fun locationUpdates(): Flow<DeviceLocation?>
}
