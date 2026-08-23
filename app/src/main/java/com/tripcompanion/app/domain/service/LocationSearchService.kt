package com.tripcompanion.app.domain.service

import com.tripcompanion.app.domain.model.SearchResultLocation

/**
 * What can go wrong when searching for a place (§11).
 *
 * These are the *user's* categories, not HTTP's. "Rate limited" and "the provider
 * is broken" are both 4xx/5xx to a network library, but they need different words
 * on screen: one means wait a moment, the other means this is not your fault and
 * retrying now will not help.
 */
enum class LocationSearchError {
    /** No usable connection, DNS failure, socket refused. */
    NETWORK_UNAVAILABLE,

    /** The provider accepted the request but did not answer in time. */
    TIMEOUT,

    /** Too many requests — the caller should back off, the query itself is fine. */
    RATE_LIMITED,

    /** The provider answered with an error status. */
    PROVIDER_ERROR,

    /** The provider answered successfully with something unparseable. */
    MALFORMED_RESPONSE,

    /** Anything unclassified. Kept so the taxonomy is total and nothing is swallowed. */
    UNKNOWN
}

/**
 * The outcome of a search.
 *
 * [Empty] is deliberately not `Results(emptyList())`: "no such place" and "found
 * places" are different things to say to someone, and collapsing them means the
 * screen has to re-derive the distinction from a list size.
 */
sealed interface LocationSearchOutcome {
    data class Results(val places: List<SearchResultLocation>) : LocationSearchOutcome
    data object Empty : LocationSearchOutcome
    data class Failed(val error: LocationSearchError) : LocationSearchOutcome
}

/**
 * The rectangle the user is currently looking at.
 *
 * Passed into a search so the provider can prefer what is on screen. This is the difference
 * between typing "City Palace" and getting the one in the city you are planning, and getting
 * a list of every palace of that name in the country — the complaint that a place "cannot be
 * found" is usually a place that was found, in the wrong country, on page two.
 *
 * A bias, never a filter: matches outside the rectangle must still come back, ranked lower.
 * Someone searching for tomorrow's station while looking at today's hotel is not making a
 * mistake, and a screen that hid the answer would be.
 */
data class SearchViewport(
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double
) {
    val centerLatitude: Double get() = (north + south) / 2
    val centerLongitude: Double get() = (east + west) / 2

    /**
     * False for a rectangle nothing useful can be built from: an empty span, or one that
     * crosses the antimeridian, where `west > east` and the numbers stop meaning a box.
     * Rather than clamping it into something wrong, such a viewport is simply not used.
     */
    val isUsable: Boolean
        get() = north > south && east > west &&
            north <= 90.0 && south >= -90.0 && east <= 180.0 && west >= -180.0
}

/** Thrown by a [LocationSearchProvider] to report a classified failure. */
class LocationSearchException(
    val error: LocationSearchError,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message ?: error.name, cause)

/**
 * A single place-search backend — Nominatim, Photon, Google, a fake in a test.
 *
 * Implementations do one job: turn a query into places, or throw a
 * [LocationSearchException]. Timeouts, retries, debouncing and minimum query
 * length are **not** their concern; those are policy and live in
 * [LocationSearchService], so swapping providers cannot change app behaviour.
 */
interface LocationSearchProvider {

    /** Name recorded on saved locations for provenance (§13). */
    val providerName: String

    /**
     * @param viewport where the user is looking, or null when nothing on screen suggests a
     *   region. Implementations must treat it as a preference and still return distant matches.
     * @throws LocationSearchException on any classified failure.
     */
    suspend fun search(
        query: String,
        limit: Int,
        viewport: SearchViewport? = null
    ): List<SearchResultLocation>
}

/**
 * What the app actually calls to search for a place.
 *
 * §11 requires that the UI and domain never touch a provider directly, and this
 * interface is that boundary: nothing about Nominatim, HTTP or JSON is visible
 * through it. Everything past it is replaceable without a screen changing.
 */
interface LocationSearchService {

    /**
     * Never throws. A failed search is a value, because "the network is down" is
     * an ordinary state for this screen to be in, not an exception.
     *
     * [viewport] is optional and advisory: it changes the *order* results come back in and
     * nudges the provider, and passing none simply means an unbiased world search.
     */
    suspend fun searchPlaces(
        query: String,
        viewport: SearchViewport? = null
    ): LocationSearchOutcome

    companion object {
        /** Below this, a query is noise; the service returns [LocationSearchOutcome.Empty]. */
        const val MIN_QUERY_LENGTH = 2

        /** Hard ceiling on one search, whatever the provider's own timeouts do. */
        const val SEARCH_TIMEOUT_MS = 8_000L

        /**
         * Results requested per query.
         *
         * Deliberately more than the list shows. Nominatim returns the same building as
         * several rows and ranks by its own relevance, so asking for ten and then
         * deduplicating and re-ranking by distance can leave four — with the one the user
         * meant among the six that were never fetched.
         */
        const val RESULT_LIMIT = 25
    }
}
