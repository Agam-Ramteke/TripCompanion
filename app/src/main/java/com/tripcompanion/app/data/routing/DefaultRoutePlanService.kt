package com.tripcompanion.app.data.routing

import com.tripcompanion.app.domain.service.RoutePlanError
import com.tripcompanion.app.domain.service.RoutePlanException
import com.tripcompanion.app.domain.service.RoutePlanOutcome
import com.tripcompanion.app.domain.service.RoutePlanProvider
import com.tripcompanion.app.domain.service.RoutePlanService
import com.tripcompanion.app.domain.service.RoutePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routing policy, in one place (§11).
 *
 * Everything here is the kind of decision that should not change when the backend changes: how few
 * stops is not a route, how long to wait, and — the point of the whole class — that a failure is a
 * *value*, not an exception. "No key", "offline on a train", "no road between these two stops" are
 * ordinary states for a trip map to be in, and each comes back as [RoutePlanOutcome.Unavailable] so
 * the screen can quietly fall back to straight legs rather than crash or show an error.
 *
 * The provider supplies road geometry or throws; this decides what the app does about it. The
 * no-key short-circuit avoids a pointless network round trip on the shipping default.
 */
@Singleton
class DefaultRoutePlanService @Inject constructor(
    private val provider: RoutePlanProvider
) : RoutePlanService {

    override suspend fun planRoute(waypoints: List<RoutePoint>): RoutePlanOutcome {
        if (!provider.isConfigured) {
            return RoutePlanOutcome.Unavailable(RoutePlanError.NOT_CONFIGURED)
        }
        if (waypoints.size < RoutePlanService.MIN_WAYPOINTS) {
            return RoutePlanOutcome.Unavailable(RoutePlanError.NO_ROUTE)
        }

        // A pathological day should degrade to a shorter road line, not a rejected request.
        val capped = if (waypoints.size > RoutePlanService.MAX_WAYPOINTS) {
            waypoints.take(RoutePlanService.MAX_WAYPOINTS)
        } else {
            waypoints
        }

        return try {
            val planned = withTimeout(RoutePlanService.ROUTE_TIMEOUT_MS) {
                provider.route(capped)
            }
            if (planned.points.size >= 2) {
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
