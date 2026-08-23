package com.tripcompanion.app.data.network

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
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Place search against OpenStreetMap Nominatim.
 *
 * Chosen because it needs no API key and covers the whole world, which matters
 * given the app must not assume any particular city (§4).
 *
 * This class is a [LocationSearchProvider] and nothing more: it knows about HTTP
 * and Nominatim's JSON, and knows nothing about debouncing, retries or what the
 * screen does with a failure. Policy lives in the service above it.
 */
@Singleton
class NominatimLocationSearchProvider @Inject constructor() : LocationSearchProvider {

    override val providerName: String = PROVIDER_NAME

    override suspend fun search(
        query: String,
        limit: Int,
        viewport: SearchViewport?
    ): List<SearchResultLocation> =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(buildRequestUrl(query, limit, viewport))

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    // Nominatim's usage policy requires an identifying User-Agent;
                    // requests without one are refused.
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                }

                when (val code = connection.responseCode) {
                    in 200..299 -> Unit
                    HTTP_TOO_MANY_REQUESTS -> throw LocationSearchException(
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
                // A cancelled search is not a failure — the user typed another key.
                // Rethrowing keeps structured concurrency intact.
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

    /**
     * The query as Nominatim wants it.
     *
     * Every parameter here earns its place against the complaint that search "cannot find
     * what I am looking for":
     *
     * - `viewbox` + `bounded=0` biases towards the visible map without excluding anything.
     *   This is the single biggest improvement: an unbiased search for a temple or a palace
     *   returns the most famous one in the world, which is rarely the one you can walk to.
     * - `dedupe=1` asks the provider to collapse its own duplicates before spending result
     *   slots on them. The service deduplicates again afterwards; this stops the *limit*
     *   being eaten by three copies of one building.
     * - `namedetails=1` returns every name tag the object carries. Nominatim leaves the
     *   top-level `name` empty more often than one would expect, and the fallback of chopping
     *   `display_name` at the first comma then labels the result with a house number. The
     *   name tags are how such a row gets a name a person recognises.
     * - `accept-language` follows the device, so a result reads in the user's language
     *   rather than in whatever the OSM data happens to be tagged as first.
     *
     * Built as a string rather than with `Uri.Builder` to keep this class free of Android
     * types — it is unit-tested on the JVM.
     */
    internal fun buildRequestUrl(query: String, limit: Int, viewport: SearchViewport?): String {
        val params = mutableListOf(
            "q=" + URLEncoder.encode(query.trim(), "UTF-8"),
            "format=json",
            "addressdetails=1",
            "namedetails=1",
            "dedupe=1",
            "limit=$limit",
            "accept-language=" + URLEncoder.encode(Locale.getDefault().toLanguageTag(), "UTF-8")
        )

        if (viewport != null && viewport.isUsable) {
            // Nominatim's own order: left, top, right, bottom.
            params += "viewbox=%.6f,%.6f,%.6f,%.6f".format(
                Locale.US,
                viewport.west,
                viewport.north,
                viewport.east,
                viewport.south
            )
            params += "bounded=0"
        }

        return "$BASE_URL?" + params.joinToString("&")
    }

    /**
     * Parses Nominatim's array of places.
     *
     * A malformed *document* is a failure and is reported as one — silently
     * returning an empty list would tell the user "no results found" when the
     * truth is "the provider sent us something we cannot read".
     *
     * A malformed *entry* inside a well-formed document is skipped instead. One bad
     * row among ten should not cost the user the other nine.
     */
    internal fun parseResponse(jsonString: String): List<SearchResultLocation> {
        val array = try {
            JSONArray(jsonString)
        } catch (e: JSONException) {
            throw LocationSearchException(LocationSearchError.MALFORMED_RESPONSE, cause = e)
        }

        val results = mutableListOf<SearchResultLocation>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue

            val displayName = item.optString("display_name", "")
            val name = bestName(item, displayName)
            if (name.isBlank()) continue

            // A place with no coordinates cannot be put on a map or saved (§13),
            // so it is not a usable result even though the row parsed.
            val lat = item.optString("lat", "").toDoubleOrNull() ?: continue
            val lon = item.optString("lon", "").toDoubleOrNull() ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue

            val categoryClass = item.optString("class", "")
            val categoryType = item.optString("type", "")
            val category = when {
                categoryClass.isNotBlank() && categoryType.isNotBlank() ->
                    "$categoryClass / $categoryType"
                categoryClass.isNotBlank() -> categoryClass
                else -> categoryType
            }

            results += SearchResultLocation(
                name = name,
                formattedAddress = displayName,
                latitude = lat,
                longitude = lon,
                category = category.replaceFirstChar { it.uppercase() },
                providerPlaceId = item.opt("place_id")?.toString(),
                providerName = PROVIDER_NAME
            )
        }
        return results
    }

    /**
     * The best name this row can offer, in order of how well a person would recognise it.
     *
     * The top-level `name` first, then the name tags `namedetails=1` brings back — `official_name`
     * for the long form, `alt_name` and `short_name` for the one locals use — and only then the
     * first component of `display_name`. That last resort is why the earlier tiers matter: for an
     * unnamed object `display_name` starts with a house number, and a search result labelled "27"
     * is indistinguishable from a bug.
     */
    private fun bestName(item: JSONObject, displayName: String): String {
        item.optString("name", "").trim().takeIf { it.isNotBlank() }?.let { return it }

        val names = item.optJSONObject("namedetails")
        if (names != null) {
            for (key in NAME_TAG_PREFERENCE) {
                names.optString(key, "").trim().takeIf { it.isNotBlank() }?.let { return it }
            }
        }

        return displayName.substringBefore(",").trim()
    }

    companion object {
        const val PROVIDER_NAME = "OpenStreetMap/Nominatim"
        private const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val USER_AGENT =
            "TripCompanion/1.0 (Android; https://github.com/tripcompanion)"
        private const val CONNECT_TIMEOUT_MS = 6_000
        private const val READ_TIMEOUT_MS = 6_000
        private const val HTTP_TOO_MANY_REQUESTS = 429

        /** Name tags worth reading, most recognisable first. */
        private val NAME_TAG_PREFERENCE =
            listOf("name", "official_name", "int_name", "alt_name", "short_name")
    }
}
