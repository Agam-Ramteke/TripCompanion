package com.tripcompanion.app.data.network.openrouteservice

import com.tripcompanion.app.domain.service.PlannedRoute
import com.tripcompanion.app.domain.service.RouteLeg
import com.tripcompanion.app.domain.service.RoutePlanError
import com.tripcompanion.app.domain.service.RoutePlanException
import com.tripcompanion.app.domain.service.RoutePlanProvider
import com.tripcompanion.app.domain.service.RoutePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Road geometry from OpenRouteService.
 *
 * The map's route line was straight legs between stops until this provider: a plan that draws a
 * diagonal across a lake is worse than honest, so a real routing service traces the roads instead.
 * OpenRouteService was chosen over the keyless alternatives for its generous free tier and clean
 * GeoJSON, at the cost of one more key in `local.properties` — the same shape the app already
 * carries for RailRadar, so it costs nothing new to operate.
 *
 * This class is a [RoutePlanProvider] and nothing more, the routing twin of
 * [com.tripcompanion.app.data.network.NominatimLocationSearchProvider]: it knows HTTP and this one
 * vendor's GeoJSON, and knows nothing about the minimum number of stops, the timeout, or what the
 * map does when there is no route. That policy lives in the service above it.
 *
 * Two things shape the code:
 *
 * **The key travels in the `Authorization` header, not the URL**, so an error message may name the
 * endpoint freely — it carries no secret.
 *
 * **Many stops need a POST.** The GET directions endpoint takes only a start and an end; a day with
 * three stops needs the `/geojson` POST with a `coordinates` array, which is why this provider
 * writes a request body where Nominatim and RailRadar only read.
 */
@Singleton
class OpenRouteServiceRouteProvider @Inject constructor(
    @OpenRouteServiceApiKey private val apiKey: String
) : RoutePlanProvider {

    /** False when no key is configured, which is the shipping default. */
    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    override suspend fun route(waypoints: List<RoutePoint>): PlannedRoute =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw RoutePlanException(RoutePlanError.NOT_CONFIGURED)

            var connection: HttpURLConnection? = null
            try {
                connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    // Header auth: the key never reaches the URL, a log, or an exception message.
                    setRequestProperty("Authorization", apiKey)
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/geo+json")
                    setRequestProperty("User-Agent", USER_AGENT)
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                }

                connection.outputStream.use { out ->
                    out.write(buildRequestBody(waypoints).toByteArray(Charsets.UTF_8))
                }

                when (val code = connection.responseCode) {
                    in 200..299 -> Unit
                    HTTP_TOO_MANY_REQUESTS -> throw RoutePlanException(
                        RoutePlanError.RATE_LIMITED,
                        "Request limit reached"
                    )
                    HttpURLConnection.HTTP_NOT_FOUND -> throw RoutePlanException(
                        RoutePlanError.NO_ROUTE,
                        "No road route between the stops"
                    )
                    HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                        throw RoutePlanException(
                            RoutePlanError.PROVIDER_ERROR,
                            "The routing key was rejected"
                        )
                    else -> throw RoutePlanException(
                        RoutePlanError.PROVIDER_ERROR,
                        "Provider returned HTTP $code"
                    )
                }

                val body = BufferedReader(InputStreamReader(connection.inputStream))
                    .use { it.readText() }

                parseGeoJson(body)
            } catch (e: RoutePlanException) {
                throw e
            } catch (e: CancellationException) {
                // The map moved on (a new day, a new trip) mid-request. Rethrow so structured
                // concurrency stays intact — a cancelled route is not a failure to report.
                throw e
            } catch (e: SocketTimeoutException) {
                throw RoutePlanException(RoutePlanError.TIMEOUT, cause = e)
            } catch (e: UnknownHostException) {
                throw RoutePlanException(RoutePlanError.NETWORK_UNAVAILABLE, cause = e)
            } catch (e: IOException) {
                throw RoutePlanException(RoutePlanError.NETWORK_UNAVAILABLE, cause = e)
            } catch (e: Exception) {
                throw RoutePlanException(RoutePlanError.UNKNOWN, cause = e)
            } finally {
                connection?.disconnect()
            }
        }

    /**
     * The request body OpenRouteService wants: `{"coordinates":[[lon,lat],…]}`.
     *
     * The `[lon, lat]` order is the vendor's, and inverting it — the classic routing bug — puts the
     * whole trip in the wrong hemisphere, so the swap from the app's `(lat, lon)` happens here,
     * once, at the boundary. Built with `org.json` rather than string concatenation so a stop's
     * coordinate can never break the JSON.
     */
    internal fun buildRequestBody(waypoints: List<RoutePoint>): String {
        val coordinates = JSONArray()
        for (point in waypoints) {
            coordinates.put(JSONArray().put(point.longitude).put(point.latitude))
        }
        return JSONObject().put("coordinates", coordinates).toString()
    }

    /**
     * Parses OpenRouteService's GeoJSON into ordered route points and per-leg travel info.
     *
     * A malformed *document* is a failure and is reported as one — silently returning an empty line
     * would tell the map "these stops don't connect" when the truth is "the provider sent something
     * we cannot read". An empty `features` array is the provider's own way of saying no route was
     * found, and is classified as such.
     *
     * The `properties.segments[]` breakdown is treated as *optional* data, not a validity condition:
     * a route that traces the roads but carries no readable segments is still a usable line, so it
     * comes back with empty [PlannedRoute.legs] (the map then shows straight-line distances) rather
     * than a MALFORMED_RESPONSE. Only the geometry is load-bearing enough to fail on.
     *
     * Kept `internal` and free of HTTP so it can be unit-tested on the JVM against a captured
     * response, the way [com.tripcompanion.app.data.network.NominatimLocationSearchProvider] tests
     * its parser.
     */
    internal fun parseGeoJson(json: String): PlannedRoute {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw RoutePlanException(RoutePlanError.MALFORMED_RESPONSE, cause = e)
        }

        val features = root.optJSONArray("features")
            ?: throw RoutePlanException(RoutePlanError.MALFORMED_RESPONSE, "No features array")
        if (features.length() == 0) {
            throw RoutePlanException(RoutePlanError.NO_ROUTE, "Empty features — no route found")
        }

        val feature = features.optJSONObject(0)
            ?: throw RoutePlanException(RoutePlanError.MALFORMED_RESPONSE, "No first feature")

        val coordinates = feature
            .optJSONObject("geometry")
            ?.optJSONArray("coordinates")
            ?: throw RoutePlanException(RoutePlanError.MALFORMED_RESPONSE, "No geometry coordinates")

        val points = ArrayList<RoutePoint>(coordinates.length())
        for (i in 0 until coordinates.length()) {
            val pair = coordinates.optJSONArray(i) ?: continue
            // GeoJSON is [lon, lat] (a third element is elevation, ignored). Back to the app's order.
            val lon = pair.optDouble(0, Double.NaN)
            val lat = pair.optDouble(1, Double.NaN)
            if (lon.isNaN() || lat.isNaN()) continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            points += RoutePoint(lat, lon)
        }

        // A line needs at least two points to draw; one is a document that parsed but says nothing.
        if (points.size < 2) {
            throw RoutePlanException(RoutePlanError.MALFORMED_RESPONSE, "Too few route points")
        }
        return PlannedRoute(points, parseLegs(feature))
    }

    /**
     * The per-leg distances and durations from `properties.segments[]`, or an empty list.
     *
     * ORS returns one segment per leg — segment *i* is stop *i* → stop *i*+1 — carrying `distance`
     * in metres and `duration` in seconds. A segment missing either number is skipped rather than
     * defaulted to zero, since a phantom "0 km · 0 min" leg reads as truth; the map falls back to a
     * straight-line distance for any leg it has no segment for.
     */
    private fun parseLegs(feature: JSONObject): List<RouteLeg> {
        val segments = feature.optJSONObject("properties")?.optJSONArray("segments")
            ?: return emptyList()
        val legs = ArrayList<RouteLeg>(segments.length())
        for (i in 0 until segments.length()) {
            val segment = segments.optJSONObject(i) ?: continue
            val distance = segment.optDouble("distance", Double.NaN)
            val duration = segment.optDouble("duration", Double.NaN)
            if (distance.isNaN() || duration.isNaN()) continue
            legs += RouteLeg(distanceMeters = distance, durationSeconds = duration)
        }
        return legs
    }

    companion object {
        /** The multi-stop directions endpoint. Profile is baked in: a trip map is a driving plan. */
        private const val ENDPOINT =
            "https://api.openrouteservice.org/v2/directions/driving-car/geojson"
        private const val USER_AGENT = "TripCompanion/1.0 (Android)"
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
