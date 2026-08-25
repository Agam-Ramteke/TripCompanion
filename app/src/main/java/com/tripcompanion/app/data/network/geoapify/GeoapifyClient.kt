package com.tripcompanion.app.data.network.geoapify

import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.service.LocationSearchError
import com.tripcompanion.app.domain.service.LocationSearchException
import com.tripcompanion.app.domain.service.PlannedRoute
import com.tripcompanion.app.domain.service.RouteLeg
import com.tripcompanion.app.domain.service.RoutePlanError
import com.tripcompanion.app.domain.service.RoutePlanException
import com.tripcompanion.app.domain.service.RoutePoint
import com.tripcompanion.app.domain.service.SearchViewport
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
import java.net.URLEncoder
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single reusable service and client layer for all Geoapify API communications.
 *
 * Encapsulates:
 * - **Geocoding API** (`/v1/geocode/search`) for destination search.
 * - **Places API** (`/v2/places`) for nearby POIs (cafes, restaurants, hotels, attractions).
 * - **Routing API** (`/v1/routing`) for road route geometry and per-leg distances/durations.
 * - **Autocomplete API** (`/v1/geocode/autocomplete`) prepared architecture.
 */
@Singleton
class GeoapifyClient @Inject constructor(
    @GeoapifyApiKey private val apiKey: String
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /**
     * Search for places or addresses using the Geoapify Geocoding API.
     *
     * @param query Text query typed by the user (e.g. "Taj Mahal", "Blue Tokai Coffee").
     * @param limit Maximum results to return.
     * @param viewport Optional bounding box to bias results towards what is on screen.
     */
    suspend fun geocode(
        query: String,
        limit: Int = 20,
        viewport: SearchViewport? = null
    ): List<SearchResultLocation> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw LocationSearchException(
                LocationSearchError.PROVIDER_ERROR,
                "Geoapify API key is not configured"
            )
        }

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val builder = StringBuilder("https://api.geoapify.com/v1/geocode/search?text=$encodedQuery&format=json&limit=$limit&apiKey=$apiKey")

        if (viewport != null && viewport.isUsable) {
            // Geoapify rect bias format: bias=rect:minLon,minLat,maxLon,maxLat (west,south,east,north)
            builder.append("&bias=rect:${viewport.west},${viewport.south},${viewport.east},${viewport.north}")
        }

        val jsonString = executeGet(builder.toString(), isRouting = false)

        try {
            val root = JSONObject(jsonString)
            val resultsArray = root.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<SearchResultLocation>()

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.getJSONObject(i)
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue

                val formatted = item.optString("formatted", "")
                val name = item.optString("name").ifBlank {
                    item.optString("address_line1").ifBlank {
                        formatted.substringBefore(",")
                    }
                }
                val rawCategory = item.optString("category", item.optString("result_type", ""))
                val normalizedCategory = mapCategory(rawCategory)
                val placeId = item.optString("place_id").ifBlank { null }

                list.add(
                    SearchResultLocation(
                        name = name,
                        formattedAddress = formatted,
                        latitude = lat,
                        longitude = lon,
                        category = normalizedCategory,
                        providerPlaceId = placeId,
                        providerName = "Geoapify"
                    )
                )
            }
            list
        } catch (e: JSONException) {
            throw LocationSearchException(
                LocationSearchError.MALFORMED_RESPONSE,
                "Failed to parse Geoapify geocoding response",
                e
            )
        }
    }

    /**
     * Search for nearby POIs by category using the Geoapify Places API v2.
     *
     * @param categories List of Geoapify category IDs (e.g. `catering.cafe`, `accommodation.hotel`, `tourism.sights`).
     * @param latitude Center latitude.
     * @param longitude Center longitude.
     * @param radiusMeters Search radius in meters.
     * @param limit Maximum results.
     */
    suspend fun getNearbyPlaces(
        categories: List<String>,
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = 5000,
        limit: Int = 20
    ): List<SearchResultLocation> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw LocationSearchException(
                LocationSearchError.PROVIDER_ERROR,
                "Geoapify API key is not configured"
            )
        }

        val joinedCategories = URLEncoder.encode(categories.joinToString(","), "UTF-8")
        val url = "https://api.geoapify.com/v2/places?categories=$joinedCategories&filter=circle:$longitude,$latitude,$radiusMeters&bias=proximity:$longitude,$latitude&limit=$limit&apiKey=$apiKey"

        val jsonString = executeGet(url, isRouting = false)

        try {
            val root = JSONObject(jsonString)
            val features = root.optJSONArray("features") ?: JSONArray()
            val list = mutableListOf<SearchResultLocation>()

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                val geometry = feature.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates")
                val lon = coordinates?.optDouble(0, Double.NaN) ?: Double.NaN
                val lat = coordinates?.optDouble(1, Double.NaN) ?: Double.NaN
                if (lat.isNaN() || lon.isNaN()) continue

                val props = feature.optJSONObject("properties") ?: JSONObject()
                val formatted = props.optString("formatted", "")
                val name = props.optString("name").ifBlank {
                    props.optString("address_line1").ifBlank {
                        formatted.substringBefore(",")
                    }
                }
                val rawCategories = props.optJSONArray("categories")
                val rawCategory = if (rawCategories != null && rawCategories.length() > 0) {
                    rawCategories.optString(0, "")
                } else {
                    ""
                }
                val normalizedCategory = mapCategory(rawCategory)
                val placeId = props.optString("place_id").ifBlank { null }

                list.add(
                    SearchResultLocation(
                        name = name,
                        formattedAddress = formatted,
                        latitude = lat,
                        longitude = lon,
                        category = normalizedCategory,
                        providerPlaceId = placeId,
                        providerName = "Geoapify"
                    )
                )
            }
            list
        } catch (e: JSONException) {
            throw LocationSearchException(
                LocationSearchError.MALFORMED_RESPONSE,
                "Failed to parse Geoapify places response",
                e
            )
        }
    }

    /**
     * Compute road route and leg metrics for ordered stops using the Geoapify Routing API.
     *
     * @param waypoints Ordered list of stops.
     * @param mode Transport mode (`drive`, `walk`, `bicycle`, `transit`).
     */
    suspend fun getRoute(
        waypoints: List<RoutePoint>,
        mode: String = "drive"
    ): PlannedRoute = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw RoutePlanException(RoutePlanError.NOT_CONFIGURED)
        }
        if (waypoints.size < 2) {
            return@withContext PlannedRoute(emptyList(), emptyList())
        }

        // Geoapify Routing waypoints format: waypoints=lat1,lon1|lat2,lon2|...
        val waypointsParam = waypoints.joinToString("|") { "${it.latitude},${it.longitude}" }
        val encodedWaypoints = URLEncoder.encode(waypointsParam, "UTF-8")
        val url = "https://api.geoapify.com/v1/routing?waypoints=$encodedWaypoints&mode=$mode&format=geojson&apiKey=$apiKey"

        val jsonString = executeGet(url, isRouting = true)

        try {
            val root = JSONObject(jsonString)
            val features = root.optJSONArray("features")
            if (features == null || features.length() == 0) {
                throw RoutePlanException(RoutePlanError.NO_ROUTE, "No route found between waypoints")
            }

            val feature = features.getJSONObject(0)
            val geometry = feature.optJSONObject("geometry")
            val rawCoordinates = geometry?.optJSONArray("coordinates")

            val points = mutableListOf<RoutePoint>()
            if (rawCoordinates != null) {
                // GeoJSON coordinates can be an array of points or MultiLineString (array of arrays)
                if (rawCoordinates.length() > 0 && rawCoordinates.get(0) is JSONArray) {
                    val firstElement = rawCoordinates.getJSONArray(0)
                    if (firstElement.length() > 0 && firstElement.get(0) is JSONArray) {
                        // MultiLineString: [[[lon, lat], ...]]
                        for (i in 0 until rawCoordinates.length()) {
                            val line = rawCoordinates.getJSONArray(i)
                            for (j in 0 until line.length()) {
                                val coord = line.getJSONArray(j)
                                val lon = coord.getDouble(0)
                                val lat = coord.getDouble(1)
                                points.add(RoutePoint(lat, lon))
                            }
                        }
                    } else {
                        // LineString: [[lon, lat], ...]
                        for (i in 0 until rawCoordinates.length()) {
                            val coord = rawCoordinates.getJSONArray(i)
                            val lon = coord.getDouble(0)
                            val lat = coord.getDouble(1)
                            points.add(RoutePoint(lat, lon))
                        }
                    }
                }
            }

            val properties = feature.optJSONObject("properties") ?: JSONObject()
            val rawLegs = properties.optJSONArray("legs")
            val legs = mutableListOf<RouteLeg>()

            if (rawLegs != null) {
                for (i in 0 until rawLegs.length()) {
                    val leg = rawLegs.getJSONObject(i)
                    val distanceMeters = leg.optDouble("distance", 0.0)
                    val timeSeconds = leg.optDouble("time", 0.0)
                    legs.add(RouteLeg(distanceMeters, timeSeconds))
                }
            }

            PlannedRoute(points = points, legs = legs)
        } catch (e: JSONException) {
            throw RoutePlanException(
                RoutePlanError.MALFORMED_RESPONSE,
                "Failed to parse Geoapify routing GeoJSON response",
                e
            )
        }
    }

    /**
     * Autocomplete suggestions architecture (Prepared for future typeahead UI).
     */
    suspend fun autocomplete(
        text: String,
        limit: Int = 10,
        latitude: Double? = null,
        longitude: Double? = null
    ): List<SearchResultLocation> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()

        val encodedText = URLEncoder.encode(text, "UTF-8")
        val builder = StringBuilder("https://api.geoapify.com/v1/geocode/autocomplete?text=$encodedText&format=json&limit=$limit&apiKey=$apiKey")
        if (latitude != null && longitude != null) {
            builder.append("&bias=proximity:$longitude,$latitude")
        }

        val jsonString = executeGet(builder.toString(), isRouting = false)
        try {
            val root = JSONObject(jsonString)
            val results = root.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<SearchResultLocation>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN()) continue
                val formatted = item.optString("formatted", "")
                val name = item.optString("name", formatted.substringBefore(","))
                val category = mapCategory(item.optString("category", item.optString("result_type", "")))
                list.add(
                    SearchResultLocation(
                        name = name,
                        formattedAddress = formatted,
                        latitude = lat,
                        longitude = lon,
                        category = category,
                        providerPlaceId = item.optString("place_id").ifBlank { null },
                        providerName = "Geoapify"
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun executeGet(urlStr: String, isRouting: Boolean): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "TripCompanion/3.0 (Android)")
                setRequestProperty("Accept", "application/json, application/geo+json")
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }

            when (val code = connection.responseCode) {
                in 200..299 -> Unit
                401, 403 -> {
                    if (isRouting) {
                        throw RoutePlanException(RoutePlanError.PROVIDER_ERROR, "Geoapify API key rejected (HTTP $code)")
                    } else {
                        throw LocationSearchException(LocationSearchError.PROVIDER_ERROR, "Geoapify API key rejected (HTTP $code)")
                    }
                }
                404 -> {
                    if (isRouting) {
                        throw RoutePlanException(RoutePlanError.NO_ROUTE, "No route found (HTTP 404)")
                    } else {
                        return "{}"
                    }
                }
                429 -> {
                    if (isRouting) {
                        throw RoutePlanException(RoutePlanError.RATE_LIMITED, "Geoapify rate limit reached")
                    } else {
                        throw LocationSearchException(LocationSearchError.RATE_LIMITED, "Geoapify rate limit reached")
                    }
                }
                else -> {
                    if (isRouting) {
                        throw RoutePlanException(RoutePlanError.PROVIDER_ERROR, "Geoapify HTTP $code")
                    } else {
                        throw LocationSearchException(LocationSearchError.PROVIDER_ERROR, "Geoapify HTTP $code")
                    }
                }
            }

            return BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
        } catch (e: RoutePlanException) {
            throw e
        } catch (e: LocationSearchException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            if (isRouting) {
                throw RoutePlanException(RoutePlanError.TIMEOUT, "Request timed out", e)
            } else {
                throw LocationSearchException(LocationSearchError.TIMEOUT, "Request timed out", e)
            }
        } catch (e: UnknownHostException) {
            if (isRouting) {
                throw RoutePlanException(RoutePlanError.NETWORK_UNAVAILABLE, "Network unavailable", e)
            } else {
                throw LocationSearchException(LocationSearchError.NETWORK_UNAVAILABLE, "Network unavailable", e)
            }
        } catch (e: IOException) {
            if (isRouting) {
                throw RoutePlanException(RoutePlanError.NETWORK_UNAVAILABLE, e.message, e)
            } else {
                throw LocationSearchException(LocationSearchError.NETWORK_UNAVAILABLE, e.message, e)
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun mapCategory(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("catering") || lower.contains("cafe") || lower.contains("restaurant") || lower.contains("food") -> "Food"
            lower.contains("accommodation") || lower.contains("hotel") || lower.contains("hostel") || lower.contains("motel") || lower.contains("guest_house") -> "Stay"
            lower.contains("tourism") || lower.contains("attraction") || lower.contains("sights") || lower.contains("monument") || lower.contains("museum") -> "Visit"
            lower.contains("transit") || lower.contains("railway") || lower.contains("station") || lower.contains("airport") || lower.contains("bus") -> "Transit"
            else -> "Visit"
        }
    }
}
