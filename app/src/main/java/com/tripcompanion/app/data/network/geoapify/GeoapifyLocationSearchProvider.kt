package com.tripcompanion.app.data.network.geoapify

import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.service.LocationSearchProvider
import com.tripcompanion.app.domain.service.SearchViewport
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a place or destination search query into place candidates via Geoapify.
 *
 * Implements the [LocationSearchProvider] port (§11) so that switching to Geoapify
 * is completely transparent to the UI and domain layers.
 */
@Singleton
class GeoapifyLocationSearchProvider @Inject constructor(
    private val client: GeoapifyClient
) : LocationSearchProvider {

    override val providerName: String = "Geoapify"

    override suspend fun search(
        query: String,
        limit: Int,
        viewport: SearchViewport?
    ): List<SearchResultLocation> {
        return client.geocode(query = query, limit = limit, viewport = viewport)
    }
}
