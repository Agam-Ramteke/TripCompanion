package com.tripcompanion.app.data.search

import com.tripcompanion.app.core.util.GeoUtils
import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.service.LocationSearchError
import com.tripcompanion.app.domain.service.LocationSearchException
import com.tripcompanion.app.domain.service.LocationSearchOutcome
import com.tripcompanion.app.domain.service.LocationSearchProvider
import com.tripcompanion.app.domain.service.LocationSearchService
import com.tripcompanion.app.domain.service.SearchViewport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Search policy, in one place (§11).
 *
 * Everything here is the kind of decision that should not change when the backend
 * changes: how short a query is too short, how long to wait, what "no results"
 * means, how to describe a failure. A provider supplies places; this decides what
 * the app does about them.
 *
 * Debouncing is the one piece of policy that is *not* here, and deliberately —
 * it belongs to whatever is producing keystrokes, so the ViewModel owns it. This
 * class is called once per query it should actually run.
 */
@Singleton
class DefaultLocationSearchService @Inject constructor(
    private val provider: LocationSearchProvider
) : LocationSearchService {

    override suspend fun searchPlaces(
        query: String,
        viewport: SearchViewport?
    ): LocationSearchOutcome {
        val trimmed = query.trim()
        if (trimmed.length < LocationSearchService.MIN_QUERY_LENGTH) {
            return LocationSearchOutcome.Empty
        }

        return try {
            val places = withTimeout(LocationSearchService.SEARCH_TIMEOUT_MS) {
                provider.search(trimmed, LocationSearchService.RESULT_LIMIT, viewport)
            }
            // Deduplicate on coordinates rounded to ~11 m. Nominatim frequently
            // returns the same building as several rows (the node, the way, the
            // relation), which reads to the user as the app being broken.
            val deduped = places.distinctBy {
                Triple(
                    it.name.lowercase(),
                    String.format("%.4f", it.latitude),
                    String.format("%.4f", it.longitude)
                )
            }
            val ranked = rankByProximity(deduped, viewport)
            if (ranked.isEmpty()) {
                LocationSearchOutcome.Empty
            } else {
                LocationSearchOutcome.Results(ranked)
            }
        } catch (e: TimeoutCancellationException) {
            // Must be caught before CancellationException: withTimeout signals a
            // timeout *by* cancelling, and a timeout is a real failure to report
            // whereas an ordinary cancellation is not.
            LocationSearchOutcome.Failed(LocationSearchError.TIMEOUT)
        } catch (e: CancellationException) {
            // The caller cancelled — a newer keystroke, or the screen closing.
            // Rethrow so structured concurrency is not broken by swallowing it.
            throw e
        } catch (e: LocationSearchException) {
            LocationSearchOutcome.Failed(e.error)
        } catch (e: Exception) {
            LocationSearchOutcome.Failed(LocationSearchError.UNKNOWN)
        }
    }

    /**
     * Nearest first, when we know where the user is looking.
     *
     * The provider's own ranking is global relevance — population, importance, how much of the
     * world has heard of the place. That is the wrong question here. Someone planning a day in
     * one city and typing three letters wants the match they could walk to, and the famous
     * namesake two thousand kilometres away is the answer they have to scroll past.
     *
     * Re-ranked rather than filtered, so nothing is hidden: `viewbox` already asked the
     * provider to prefer this region, and anything it returned from outside it stays in the
     * list, at the bottom where it belongs.
     *
     * With no viewport the provider's order is kept untouched — an unbiased search has no
     * "near" to sort by, and inventing one would be worse than leaving relevance alone.
     */
    private fun rankByProximity(
        places: List<SearchResultLocation>,
        viewport: SearchViewport?
    ): List<SearchResultLocation> {
        if (viewport == null || !viewport.isUsable) return places
        return places.sortedBy {
            GeoUtils.distanceKm(
                viewport.centerLatitude,
                viewport.centerLongitude,
                it.latitude,
                it.longitude
            )
        }
    }
}
