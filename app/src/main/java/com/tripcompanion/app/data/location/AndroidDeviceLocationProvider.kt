package com.tripcompanion.app.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.tripcompanion.app.domain.service.DeviceLocation
import com.tripcompanion.app.domain.service.DeviceLocationProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device's position from the platform's own [LocationManager] — no Google Play Services.
 *
 * The keyless, no-Google choice, matching how the map already avoids proprietary SDKs: osmdroid
 * tiles rather than the Maps SDK, framework location rather than the fused provider. It costs a
 * little more code here — two providers to juggle, a last-known seed — but adds no dependency and
 * works on a de-Googled device.
 *
 * The §11 boundary holds: this class knows Android's location API and nothing above `data/` does.
 * Failures never cross it. A missing permission or a disabled provider is emitted as a null fix —
 * the ordinary "I don't know where you are" state — so the ViewModel and screen never catch a
 * `SecurityException`.
 */
@Singleton
class AndroidDeviceLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceLocationProvider {

    private val locationManager: LocationManager? =
        ContextCompat.getSystemService(context, LocationManager::class.java)

    // Guarded by hasPermission() before any LocationManager call; lint can't see across that.
    @SuppressLint("MissingPermission")
    override fun locationUpdates(): Flow<DeviceLocation?> = callbackFlow {
        val manager = locationManager
        if (manager == null || !hasPermission()) {
            // No permission, or a device with no location service at all: emit one null so the
            // collector settles into "no fix", then finish. The map draws no dot; center-on-me
            // falls back to recentring on the trip.
            trySend(null)
            close()
            return@callbackFlow
        }

        // A pre-30 LocationListener has four abstract methods, so this is a full object rather than a
        // SAM lambda — a lambda compiled against API 35's default methods would throw
        // AbstractMethodError when the framework calls one of the others on an older device.
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toDeviceLocation())
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // Seed with the freshest last-known fix so the dot appears at once rather than after the
        // first update tick; a live fix supersedes it moments later.
        val enabled = PROVIDERS.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val seed = enabled
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        trySend(seed?.toDeviceLocation())

        // Register on every enabled provider; fine and network together give a quick coarse fix that
        // GPS then refines. If location is off entirely, the flow simply stays open on the seed.
        enabled.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    MIN_INTERVAL_MS,
                    MIN_DISTANCE_M,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }

        awaitClose { runCatching { manager.removeUpdates(listener) } }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun Location.toDeviceLocation(): DeviceLocation =
        DeviceLocation(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = if (hasAccuracy()) accuracy else null
        )

    companion object {
        // GPS first so its fresher fixes win the last-known seed; both are registered when enabled.
        private val PROVIDERS =
            listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

        /** Gentle cadence — this is a glanceable dot, not turn-by-turn navigation. */
        private const val MIN_INTERVAL_MS = 5_000L
        private const val MIN_DISTANCE_M = 10f
    }
}
