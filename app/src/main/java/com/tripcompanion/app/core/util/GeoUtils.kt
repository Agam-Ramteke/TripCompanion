package com.tripcompanion.app.core.util

import java.util.Locale

/**
 * The one place coordinates become text.
 *
 * A latitude carries fourteen digits after the point when it comes back from a search
 * provider, and none of them past the fourth mean anything to someone standing in a
 * street: four places is about eleven metres, which is the width of the courtyard the
 * marker is sitting in. Printing the rest is the same leak as printing nanoseconds on a
 * departure time — machine precision presented as if the user asked for it.
 *
 * Hemispheres are written as letters rather than as signs. "-73.68" requires the reader
 * to remember which of the two numbers is allowed to be negative and what that means;
 * "73.6833° W" does not.
 */
object GeoUtils {

    /** Enough precision to find a doorway, not enough to imply a survey. */
    private const val DISPLAY_DECIMALS = 4

    /** One coordinate pair on one line: "24.5760° N, 73.6833° E". */
    fun formatCoordinates(latitude: Double, longitude: Double): String =
        "${formatLatitude(latitude)}, ${formatLongitude(longitude)}"

    /** A latitude on its own, for a labelled row that already says which axis it is. */
    fun formatLatitude(latitude: Double): String =
        format(latitude, if (latitude < 0) "S" else "N")

    /** A longitude on its own. */
    fun formatLongitude(longitude: Double): String =
        format(longitude, if (longitude < 0) "W" else "E")

    private fun format(value: Double, hemisphere: String): String =
        String.format(Locale.US, "%.${DISPLAY_DECIMALS}f° %s", kotlin.math.abs(value), hemisphere)

    /**
     * Great-circle distance between two points, in kilometres.
     *
     * Haversine on a spherical earth. The error against a proper ellipsoid is about 0.3%,
     * which is under a hundred metres over the ten-kilometre hops this is used for — "1.4 km
     * away" is the answer either way, and the extra machinery would not change a single
     * string on screen.
     */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    /**
     * A distance the way a person would say it.
     *
     * Metres below a kilometre, rounded to fifty so it does not imply GPS-grade certainty
     * about a place whose coordinate came from a search result. One decimal up to ten
     * kilometres, whole numbers beyond — nobody walks 12.3 km.
     */
    fun formatDistance(km: Double): String = when {
        km < 0.05 -> "Here"
        km < 1.0 -> "${(km * 1000 / 50).toInt() * 50} m"
        km < 10.0 -> String.format(Locale.US, "%.1f km", km)
        else -> "${km.toInt()} km"
    }

    /** True when a row has never been given a real position, so distance would be nonsense. */
    fun hasPosition(latitude: Double, longitude: Double): Boolean =
        latitude != 0.0 || longitude != 0.0

    private const val EARTH_RADIUS_KM = 6371.0088
}
