package com.tripcompanion.app.data.network.maptiler

import com.tripcompanion.app.BuildConfig
import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.service.LocationSearchError
import com.tripcompanion.app.domain.service.LocationSearchException
import com.tripcompanion.app.domain.service.LocationSearchProvider
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

@Singleton
class MapTilerLocationSearchProvider @Inject constructor() : LocationSearchProvider {

    override val providerName: String = "MapTiler"

    override suspend fun search(
        query: String,
        limit: Int,
        viewport: SearchViewport?
    ): List<SearchResultLocation> =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val apiKey = BuildConfig.MAPTILER_API_KEY
                if (apiKey.isBlank()) {
                    return@withContext emptyList()
                }

                val url = URL(buildRequestUrl(query, limit, viewport, apiKey))

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    instanceFollowRedirects = true
                }

                when (val code = connection.responseCode) {
                    in 200..299 -> Unit
                    403 -> throw LocationSearchException(
                        LocationSearchError.PROVIDER_ERROR,
                        "MapTiler API key rejected (HTTP 403)"
                    )
                    429 -> throw LocationSearchException(
                        LocationSearchError.RATE_LIMITED,
                        "Provider rate limit hit (HTTP $code)"
                    )
                    else -> throw LocationSearchException(
                        LocationSearchError.PROVIDER_ERROR,
                        "Provider returned HTTP $code"
                    )
                }

                val body = BufferedReader(InputStreamReader(connection.inputStream))
                    .use { it.readText() }

                parseResponse(body)
            } catch (e: LocationSearchException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw LocationSearchException(LocationSearchError.TIMEOUT, cause = e)
            } catch (e: UnknownHostException) {
                throw LocationSearchException(LocationSearchError.NETWORK_UNAVAILABLE, cause = e)
            } catch (e: IOException) {
                throw LocationSearchException(LocationSearchError.NETWORK_UNAVAILABLE, cause = e)
            } catch (e: Exception) {
                throw LocationSearchException(LocationSearchError.UNKNOWN, cause = e)
            } finally {
                connection?.disconnect()
            }
        }

    private fun buildRequestUrl(query: String, limit: Int, viewport: SearchViewport?, apiKey: String): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val builder = StringBuilder("https://api.maptiler.com/geocoding/$encodedQuery.json?key=$apiKey&limit=$limit")

        if (viewport != null) {
            val minLon = viewport.west
            val minLat = viewport.south
            val maxLon = viewport.east
            val maxLat = viewport.north
            builder.append("&bbox=$minLon,$minLat,$maxLon,$maxLat")
        }

        return builder.toString()
    }

    private fun parseResponse(jsonStr: String): List<SearchResultLocation> {
        val results = mutableListOf<SearchResultLocation>()
        try {
            val root = JSONObject(jsonStr)
            val features = root.optJSONArray("features") ?: return emptyList()

            for (i in 0 until features.length()) {
                val feature = features.getJSONObject(i)
                
                val text = feature.optString("text", "Unknown Place")
                val placeName = feature.optString("place_name", text)
                val id = feature.optString("id", "")
                
                val geometry = feature.optJSONObject("geometry")
                val coordinates = geometry?.optJSONArray("coordinates")
                
                if (coordinates != null && coordinates.length() >= 2) {
                    val lon = coordinates.getDouble(0)
                    val lat = coordinates.getDouble(1)
                    
                    val placeTypes = feature.optJSONArray("place_type")
                    val category = if (placeTypes != null && placeTypes.length() > 0) placeTypes.getString(0) else ""

                    results.add(
                        SearchResultLocation(
                            name = text,
                            formattedAddress = placeName,
                            latitude = lat,
                            longitude = lon,
                            category = category,
                            providerPlaceId = id,
                            providerName = providerName
                        )
                    )
                }
            }
        } catch (e: JSONException) {
            throw LocationSearchException(
                LocationSearchError.MALFORMED_RESPONSE,
                "Failed to parse MapTiler GeoJSON response",
                e
            )
        }
        return results
    }
}
