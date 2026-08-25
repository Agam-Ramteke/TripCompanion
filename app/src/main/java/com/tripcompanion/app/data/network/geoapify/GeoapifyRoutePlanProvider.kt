package com.tripcompanion.app.data.network.geoapify

import com.tripcompanion.app.domain.service.PlannedRoute
import com.tripcompanion.app.domain.service.RoutePlanProvider
import com.tripcompanion.app.domain.service.RoutePoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Road-following route geometry and per-leg distances from Geoapify Routing API.
 *
 * Implements [RoutePlanProvider] (ADR-016), connecting stops with real road geometry.
 */
@Singleton
class GeoapifyRoutePlanProvider @Inject constructor(
    private val client: GeoapifyClient
) : RoutePlanProvider {

    override val isConfigured: Boolean get() = client.isConfigured

    override suspend fun route(waypoints: List<RoutePoint>): PlannedRoute {
        return client.getRoute(waypoints = waypoints, mode = "drive")
    }
}
