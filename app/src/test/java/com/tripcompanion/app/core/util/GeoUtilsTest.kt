package com.tripcompanion.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

/**
 * Coordinates are the other place raw machine precision can reach the screen (§9's
 * concern, applied to geography), so the tests pin the exact strings — including the
 * hemisphere letters and the absence of a minus sign.
 *
 * The southern and western cases matter most: they are the ones a developer sitting in
 * the northern and eastern hemispheres never sees by accident.
 */
class GeoUtilsTest {

    @Test
    fun `a coordinate is cut to four decimals`() {
        // What a geocoder actually returns for a courtyard in Udaipur.
        assertEquals("24.5760° N", GeoUtils.formatLatitude(24.57599123456789))
    }

    @Test
    fun `a whole number still shows four decimals so a column of them lines up`() {
        assertEquals("0.0000° N", GeoUtils.formatLatitude(0.0))
        assertEquals("0.0000° E", GeoUtils.formatLongitude(0.0))
    }

    @Test
    fun `the southern hemisphere is a letter, not a minus sign`() {
        assertEquals("33.8688° S", GeoUtils.formatLatitude(-33.8688))
    }

    @Test
    fun `the western hemisphere is a letter, not a minus sign`() {
        assertEquals("74.0060° W", GeoUtils.formatLongitude(-74.0060))
    }

    @Test
    fun `no formatted coordinate carries a minus sign`() {
        val samples = listOf(-33.8688 to -70.6693, 24.576 to 73.6833, -1.2921 to 36.8219)
        samples.forEach { (lat, lon) ->
            val text = GeoUtils.formatCoordinates(lat, lon)
            assertFalse("`$text` still contains a sign", text.contains('-'))
        }
    }

    @Test
    fun `formatCoordinates puts latitude first, separated by a comma`() {
        assertEquals(
            "24.5760° N, 73.6833° E",
            GeoUtils.formatCoordinates(24.57599, 73.68331)
        )
    }

    @Test
    fun `rounding is half-up at the fourth decimal`() {
        assertEquals("24.5761° N", GeoUtils.formatLatitude(24.57605))
        assertEquals("24.5760° N", GeoUtils.formatLatitude(24.57604))
    }

    @Test
    fun `the equator reads north and the meridian reads east`() {
        // Zero is not negative, so it takes the positive letter. Stated as a test
        // because `-0.0 < 0` is false in Kotlin and this is where that would show up.
        assertEquals("0.0000° N", GeoUtils.formatLatitude(-0.0))
    }

    @Test
    fun `the poles and the date line survive the formatter`() {
        assertEquals("90.0000° N", GeoUtils.formatLatitude(90.0))
        assertEquals("90.0000° S", GeoUtils.formatLatitude(-90.0))
        assertEquals("180.0000° E", GeoUtils.formatLongitude(180.0))
        assertEquals("180.0000° W", GeoUtils.formatLongitude(-180.0))
    }

    @Test
    fun `the decimal separator does not follow the device locale`() {
        // A coordinate is a machine-readable number the user might copy out. Under a
        // comma-decimal locale, locale-sensitive formatting turns "24.5760, 73.6833"
        // into "24,5760, 73,6833" — four numbers where there were two.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("24.5760° N, 73.6833° E", GeoUtils.formatCoordinates(24.576, 73.6833))
        } finally {
            Locale.setDefault(original)
        }
    }
}
