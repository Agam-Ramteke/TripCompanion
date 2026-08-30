package com.tripcompanion.app.data.network.locationiq

import com.tripcompanion.app.domain.model.SearchResultLocation
import com.tripcompanion.app.domain.service.LocationSearchProvider
import com.tripcompanion.app.domain.service.SearchViewport
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a place or destination search query into place candidates via LocationIQ.
 *
 * Implements the [LocationSearchProvider] port (§11) so that switching to LocationIQ
 * is completely transparent to the UI and domain layers.
 */
@Singleton
class LocationIqLocationSearchProvider @Inject constructor(
    private val client: LocationIqClient
) : LocationSearchProvider {

    override val providerName: String = LocationIqClient.PROVIDER_NAME

    override suspend fun search(
        query: String,
        limit: Int,
        viewport: SearchViewport?
    ): List<SearchResultLocation> {
        return client.search(query = query, limit = limit, viewport = viewport)
    }
}
