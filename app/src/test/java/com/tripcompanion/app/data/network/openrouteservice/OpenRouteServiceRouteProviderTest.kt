package com.tripcompanion.app.data.network.openrouteservice

import com.tripcompanion.app.domain.service.RoutePlanError
import com.tripcompanion.app.domain.service.RoutePlanException
import com.tripcompanion.app.domain.service.RouteLeg
import com.tripcompanion.app.domain.service.RoutePoint
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two halves of the OpenRouteService provider that run without a device: the request body it
 * writes and the GeoJSON it reads back.
 *
 * The stakes are the coordinate order. OpenRouteService speaks `[lon, lat]` and the app speaks
 * `(lat, lon)`; a single inverted pair sends the whole route to the wrong hemisphere, and that bug
 * is invisible until a map is on screen. So the tests pin the order in both directions, using
 * coordinates where a swap would be unmistakable (a positive longitude that is an impossible
 * latitude). The parser is constructed with a blank key because parsing never touches the network.
 */
class OpenRouteServiceRouteProviderTest {

    private val provider = OpenRouteServiceRouteProvider(apiKey = "")

    @Test
    fun `request body lists coordinates as lon,lat in order`() {
        val body = provider.buildRequestBody(
            listOf(
                RoutePoint(latitude = 24.57, longitude = 73.68),
                RoutePoint(latitude = 27.17, longitude = 78.04)
            )
        )

        val coordinates = JSONObject(body).getJSONArray("coordinates")
        assertEquals(2, coordinates.length())
        // First pair: longitude then latitude — the vendor's order, not the app's.
        assertEquals(73.68, coordinates.getJSONArray(0).getDouble(0), 1e-9)
        assertEquals(24.57, coordinates.getJSONArray(0).getDouble(1), 1e-9)
        assertEquals(78.04, coordinates.getJSONArray(1).getDouble(0), 1e-9)
        assertEquals(27.17, coordinates.getJSONArray(1).getDouble(1), 1e-9)
    }

    @Test
    fun `geometry coordinates parse back to lat,lon in order`() {
        // A LineString of three points, [lon, lat] as GeoJSON requires.
        val json = geoJson(
            listOf(
                doubleArrayOf(73.68, 24.57),
                doubleArrayOf(75.79, 25.90),
                doubleArrayOf(78.04, 27.17)
            )
        )

        val points = provider.parseGeoJson(json).points

        assertEquals(
            listOf(
                RoutePoint(24.57, 73.68),
                RoutePoint(25.90, 75.79),
                RoutePoint(27.17, 78.04)
            ),
            points
        )
    }

    @Test
    fun `a third elevation element is ignored`() {
        val json = geoJson(
            listOf(
                doubleArrayOf(73.68, 24.57, 598.0),
                doubleArrayOf(78.04, 27.17, 171.0)
            )
        )

        val points = provider.parseGeoJson(json).points

        assertEquals(listOf(RoutePoint(24.57, 73.68), RoutePoint(27.17, 78.04)), points)
    }

    @Test
    fun `segments parse into per-leg distance and duration`() {
        // Two legs, in metres and seconds — the vendor's raw units, carried through unrounded.
        val json = geoJson(
            coordinates = listOf(
                doubleArrayOf(73.68, 24.57),
                doubleArrayOf(75.79, 25.90),
                doubleArrayOf(78.04, 27.17)
            ),
            segments = listOf(4812.3 to 903.0, 5211.7 to 1024.4)
        )

        val legs = provider.parseGeoJson(json).legs

        assertEquals(
            listOf(
                RouteLeg(distanceMeters = 4812.3, durationSeconds = 903.0),
                RouteLeg(distanceMeters = 5211.7, durationSeconds = 1024.4)
            ),
            legs
        )
    }

    @Test
    fun `geometry without segments still parses, carrying no legs`() {
        // A valid line with no properties.segments — the map falls back to straight-line distance.
        val json = geoJson(
            listOf(
                doubleArrayOf(73.68, 24.57),
                doubleArrayOf(78.04, 27.17)
            )
        )

        val route = provider.parseGeoJson(json)

        assertEquals(2, route.points.size)
        assertTrue(route.legs.isEmpty())
    }

    @Test
    fun `a segment missing a number is skipped, not defaulted to zero`() {
        // A phantom "0 km · 0 min" leg would read as truth; a malformed segment is dropped instead.
        val coords = JSONArray().apply {
            put(JSONArray().put(73.68).put(24.57))
            put(JSONArray().put(78.04).put(27.17))
        }
        val geometry = JSONObject().put("type", "LineString").put("coordinates", coords)
        val segments = JSONArray()
            .put(JSONObject().put("duration", 903.0)) // no distance
            .put(JSONObject().put("distance", 5211.7).put("duration", 1024.4))
        val properties = JSONObject().put("segments", segments)
        val feature = JSONObject()
            .put("type", "Feature")
            .put("geometry", geometry)
            .put("properties", properties)
        val json = JSONObject()
            .put("type", "FeatureCollection")
            .put("features", JSONArray().put(feature))
            .toString()

        val legs = provider.parseGeoJson(json).legs

        assertEquals(listOf(RouteLeg(distanceMeters = 5211.7, durationSeconds = 1024.4)), legs)
    }

    @Test
    fun `an empty features array means no route, not a malformed document`() {
        val json = JSONObject().put("type", "FeatureCollection")
            .put("features", JSONArray())
            .toString()

        val error = runCatching { provider.parseGeoJson(json) }.exceptionOrNull()
        assertTrue(error is RoutePlanException)
        assertEquals(RoutePlanError.NO_ROUTE, (error as RoutePlanException).error)
    }

    @Test
    fun `missing geometry is a malformed response`() {
        val feature = JSONObject().put("type", "Feature") // no geometry
        val json = JSONObject().put("features", JSONArray().put(feature)).toString()

        val error = runCatching { provider.parseGeoJson(json) }.exceptionOrNull()
        assertTrue(error is RoutePlanException)
        assertEquals(RoutePlanError.MALFORMED_RESPONSE, (error as RoutePlanException).error)
    }

    @Test
    fun `unparseable body is a malformed response`() {
        val error = runCatching { provider.parseGeoJson("not json at all") }.exceptionOrNull()
        assertTrue(error is RoutePlanException)
        assertEquals(RoutePlanError.MALFORMED_RESPONSE, (error as RoutePlanException).error)
    }

    @Test
    fun `a single point is too few to be a line`() {
        val json = geoJson(listOf(doubleArrayOf(73.68, 24.57)))

        val error = runCatching { provider.parseGeoJson(json) }.exceptionOrNull()
        assertTrue(error is RoutePlanException)
        assertEquals(RoutePlanError.MALFORMED_RESPONSE, (error as RoutePlanException).error)
    }

    /**
     * Builds a minimal but valid ORS GeoJSON envelope around a LineString of `[lon, lat(, ele)]`,
     * optionally carrying `properties.segments` as `(distanceMeters, durationSeconds)` pairs.
     */
    private fun geoJson(
        coordinates: List<DoubleArray>,
        segments: List<Pair<Double, Double>> = emptyList()
    ): String {
        val coords = JSONArray()
        for (c in coordinates) {
            val pair = JSONArray()
            for (v in c) pair.put(v)
            coords.put(pair)
        }
        val geometry = JSONObject().put("type", "LineString").put("coordinates", coords)
        val feature = JSONObject().put("type", "Feature").put("geometry", geometry)
        if (segments.isNotEmpty()) {
            val segmentArray = JSONArray()
            for ((distance, duration) in segments) {
                segmentArray.put(JSONObject().put("distance", distance).put("duration", duration))
            }
            feature.put("properties", JSONObject().put("segments", segmentArray))
        }
        return JSONObject()
            .put("type", "FeatureCollection")
            .put("features", JSONArray().put(feature))
            .toString()
    }
}
