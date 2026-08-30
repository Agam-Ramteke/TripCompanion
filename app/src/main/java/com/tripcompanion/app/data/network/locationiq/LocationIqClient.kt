package com.tripcompanion.app.data.network.locationiq

import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.service.LocationSearchError
import com.tripcompanion.app.domain.service.LocationSearchException
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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service and client layer for all LocationIQ API communications.
 *
 * Implements forward geocoding (`/v1/search`) and autocomplete (`/v1/autocomplete`)
 * with address extraction, category normalization, viewport biasing, and India country preference.
 */
@Singleton
class LocationIqClient @Inject constructor(
    @LocationIqApiKey private val apiKey: String
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /**
     * Search for places or addresses using LocationIQ Autocomplete / Forward Geocoding.
     *
     * @param query Text query typed by the user (e.g. "Agra Cantt", "Taj Mahal Hotel", "Blue Tokai").
     * @param limit Maximum results to return.
     * @param viewport Optional bounding box to bias results towards what is currently on screen.
     */
    suspend fun search(
        query: String,
        limit: Int = 20,
        viewport: SearchViewport? = null
    ): List<SearchResultLocation> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            throw LocationSearchException(
                LocationSearchError.PROVIDER_ERROR,
                "LocationIQ API key is not configured"
            )
        }

        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        // 1. Try autocomplete endpoint first (best for POIs like cafes, hotels, stations)
        val autocompleteResults = fetchFromEndpoint(
            endpoint = AUTOCOMPLETE_URL,
            query = trimmed,
            limit = limit,
            viewport = viewport
        )

        if (autocompleteResults.isNotEmpty()) {
            return@withContext autocompleteResults
        }

        // 2. Fallback to forward geocoding search endpoint
        fetchFromEndpoint(
            endpoint = SEARCH_URL,
            query = trimmed,
            limit = limit,
            viewport = viewport
        )
    }

    private fun fetchFromEndpoint(
        endpoint: String,
        query: String,
        limit: Int,
        viewport: SearchViewport?
    ): List<SearchResultLocation> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val builder = StringBuilder("$endpoint?key=$apiKey&q=$encodedQuery&format=json&limit=$limit&addressdetails=1&namedetails=1&normalizecity=1")

        // Bias towards India while still permitting world queries if explicit
        builder.append("&countrycodes=in")

        if (viewport != null && viewport.isUsable) {
            // LocationIQ viewbox format: viewbox=minLon,maxLat,maxLon,minLat (west,north,east,south)
            builder.append("&viewbox=%.6f,%.6f,%.6f,%.6f&bounded=0".format(
                Locale.US,
                viewport.west,
                viewport.north,
                viewport.east,
                viewport.south
            ))
        }

        val jsonString = executeGet(builder.toString())
        return parseResponse(jsonString)
    }

    internal fun parseResponse(jsonString: String): List<SearchResultLocation> {
        if (jsonString.isBlank() || jsonString == "[]" || jsonString.startsWith("{\"error\"")) {
            return emptyList()
        }

        val array = try {
            JSONArray(jsonString)
        } catch (e: JSONException) {
            throw LocationSearchException(
                LocationSearchError.MALFORMED_RESPONSE,
                "Failed to parse LocationIQ response",
                e
            )
        }

        val results = mutableListOf<SearchResultLocation>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            val lat = item.optString("lat", "").toDoubleOrNull() ?: continue
            val lon = item.optString("lon", "").toDoubleOrNull() ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue

            val displayName = item.optString("display_name", "")
            val displayPlace = item.optString("display_place", "").ifBlank { null }
            val name = displayPlace ?: bestName(item, displayName)
            if (name.isBlank()) continue

            val formattedAddress = item.optString("display_address", "").ifBlank { displayName }
            val categoryClass = item.optString("class", "")
            val categoryType = item.optString("type", "")
            val normalizedCategory = mapCategory(categoryClass, categoryType, name)
            val placeId = item.optString("place_id").ifBlank { null }

            results.add(
                SearchResultLocation(
                    name = name,
                    formattedAddress = formattedAddress,
                    latitude = lat,
                    longitude = lon,
                    category = normalizedCategory,
                    providerPlaceId = placeId,
                    providerName = PROVIDER_NAME
                )
            )
        }
        return results
    }

    private fun bestName(item: JSONObject, displayName: String): String {
        val address = item.optJSONObject("address")
        if (address != null) {
            val candidateKeys = listOf(
                "name", "hotel", "restaurant", "cafe", "railway", "station",
                "historic", "tourism", "amenity", "building", "suburb", "road"
            )
            for (key in candidateKeys) {
                val candidate = address.optString(key, "").trim()
                if (candidate.isNotBlank() && !candidate.all { it.isDigit() }) {
                    return candidate
                }
            }
        }

        val names = item.optJSONObject("namedetails")
        if (names != null) {
            for (key in listOf("name", "official_name", "int_name", "alt_name", "short_name")) {
                val candidate = names.optString(key, "").trim()
                if (candidate.isNotBlank()) return candidate
            }
        }

        return displayName.substringBefore(",").trim()
    }

    private fun mapCategory(cls: String, type: String, name: String): String {
        val combined = "$cls $type ${name.lowercase()}".lowercase()
        return when {
            combined.contains("hotel") || combined.contains("hostel") || combined.contains("resort") ||
                combined.contains("guest_house") || combined.contains("motel") || combined.contains("lodging") -> "Stay"

            combined.contains("cafe") || combined.contains("coffee") || combined.contains("restaurant") ||
                combined.contains("food") || combined.contains("bakery") || combined.contains("pub") ||
                combined.contains("bar") || combined.contains("dhaba") -> "Food"

            combined.contains("station") || combined.contains("railway") || combined.contains("train") ||
                combined.contains("transit") || combined.contains("bus_station") || combined.contains("airport") -> "Transit"

            combined.contains("temple") || combined.contains("worship") || combined.contains("church") ||
                combined.contains("mosque") || combined.contains("gurdwara") || combined.contains("shrine") -> "Temple"

            combined.contains("garden") || combined.contains("park") -> "Garden"

            combined.contains("museum") || combined.contains("gallery") || combined.contains("monument") ||
                combined.contains("fort") || combined.contains("palace") || combined.contains("attraction") ||
                combined.contains("tourism") || combined.contains("viewpoint") || combined.contains("lake") -> "Visit"

            else -> "Visit"
        }
    }

    private fun executeGet(urlStr: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "TripCompanion/3.0 (Android; LocationIQ)")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = true
            }

            when (val code = connection.responseCode) {
                in 200..299 -> Unit
                404, 422 -> {
                    // LocationIQ returns 404/422 when no places match the query
                    return "[]"
                }
                401, 403 -> {
                    throw LocationSearchException(
                        LocationSearchError.PROVIDER_ERROR,
                        "LocationIQ API token rejected (HTTP $code)"
                    )
                }
                429 -> {
                    throw LocationSearchException(
                        LocationSearchError.RATE_LIMITED,
                        "LocationIQ rate limit reached"
                    )
                }
                else -> {
                    throw LocationSearchException(
                        LocationSearchError.PROVIDER_ERROR,
                        "LocationIQ returned HTTP $code"
                    )
                }
            }

            return BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use {
                it.readText()
            }
        } catch (e: LocationSearchException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw LocationSearchException(LocationSearchError.TIMEOUT, "LocationIQ request timed out", e)
        } catch (e: UnknownHostException) {
            throw LocationSearchException(LocationSearchError.NETWORK_UNAVAILABLE, "Network unavailable", e)
        } catch (e: IOException) {
            throw LocationSearchException(LocationSearchError.NETWORK_UNAVAILABLE, e.message, e)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        const val PROVIDER_NAME = "LocationIQ"
        private const val SEARCH_URL = "https://us1.locationiq.com/v1/search"
        private const val AUTOCOMPLETE_URL = "https://us1.locationiq.com/v1/autocomplete"
    }
}
