package com.tripcompanion.app.data.routing

import com.tripcompanion.app.domain.service.PlannedRoute
import com.tripcompanion.app.domain.service.RoutePlanError
import com.tripcompanion.app.domain.service.RoutePlanException
import com.tripcompanion.app.domain.service.RoutePlanOutcome
import com.tripcompanion.app.domain.service.RoutePlanProvider
import com.tripcompanion.app.domain.service.RoutePlanService
import com.tripcompanion.app.domain.service.RoutePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routing policy, in one place (§11).
 *
 * Automatically caches calculated routes between waypoints to minimize external API calls.
 */
@Singleton
class DefaultRoutePlanService @Inject constructor(
    private val provider: RoutePlanProvider
) : RoutePlanService {

    private val routeCache = ConcurrentHashMap<List<RoutePoint>, PlannedRoute>()

    override suspend fun planRoute(waypoints: List<RoutePoint>): RoutePlanOutcome {
        if (!provider.isConfigured) {
            return RoutePlanOutcome.Unavailable(RoutePlanError.NOT_CONFIGURED)
        }
        if (waypoints.size < RoutePlanService.MIN_WAYPOINTS) {
            return RoutePlanOutcome.Unavailable(RoutePlanError.NO_ROUTE)
        }

        val capped = if (waypoints.size > RoutePlanService.MAX_WAYPOINTS) {
            waypoints.take(RoutePlanService.MAX_WAYPOINTS)
        } else {
            waypoints
        }

        // Return from memory cache if already computed for these exact waypoints
        routeCache[capped]?.let { cached ->
            if (cached.points.size >= 2) {
                return RoutePlanOutcome.Routed(cached.points, cached.legs)
            }
        }

        return try {
            val planned = withTimeout(RoutePlanService.ROUTE_TIMEOUT_MS) {
                provider.route(capped)
            }
            if (planned.points.size >= 2) {
                routeCache[capped] = planned
                RoutePlanOutcome.Routed(planned.points, planned.legs)
            } else {
                RoutePlanOutcome.Unavailable(RoutePlanError.NO_ROUTE)
            }
        } catch (e: TimeoutCancellationException) {
            // Caught before CancellationException: withTimeout signals a timeout by cancelling, and a
            // timeout is a real failure to fall back on, whereas an ordinary cancellation is not.
            RoutePlanOutcome.Unavailable(RoutePlanError.TIMEOUT)
        } catch (e: CancellationException) {
            // The caller moved on — a new day, a new trip, the screen closing. Rethrow so structured
            // concurrency is not broken by swallowing it.
            throw e
        } catch (e: RoutePlanException) {
            RoutePlanOutcome.Unavailable(e.error)
        } catch (e: Exception) {
            RoutePlanOutcome.Unavailable(RoutePlanError.UNKNOWN)
        }
    }
}
