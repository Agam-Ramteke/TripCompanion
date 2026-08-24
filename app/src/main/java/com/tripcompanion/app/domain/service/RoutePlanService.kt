package com.tripcompanion.app.domain.service

/**
 * A point on a planned route (§11, §13).
 *
 * Domain-level and Android-free on purpose: this is the coordinate currency the routing port speaks,
 * so nothing above `data/` ever sees osmdroid's `GeoPoint` or a provider's `[lon, lat]` ordering. The
 * screen maps these to map-library points at the call site.
 */
data class RoutePoint(val latitude: Double, val longitude: Double)

/**
 * One leg of a planned route: the road distance and driving time from one stop to the next (§13).
 *
 * A route through N stops has N−1 legs, leg *i* spanning stop *i* to stop *i*+1. Metres and seconds
 * are the raw units the provider reports; the screen rounds them to "4.8 km · 15 min" only at the
 * point of display, so no precision is discarded before it has to be. Android-free like [RoutePoint]
 * so the same value is unit-testable on the JVM.
 */
data class RouteLeg(val distanceMeters: Double, val durationSeconds: Double)

/**
 * What a [RoutePlanProvider] hands back: the road geometry, and the per-leg distances and durations
 * when the vendor reports them.
 *
 * Kept separate from [RoutePlanOutcome] because a provider only ever succeeds or throws — the "no
 * route, draw straight legs" decision belongs to the service above it, not here. [legs] may be empty
 * even on success (a provider that returns geometry but no segment breakdown); the caller treats an
 * empty list as "distances unknown", never as "zero distance".
 */
data class PlannedRoute(val points: List<RoutePoint>, val legs: List<RouteLeg>)

/**
 * How a road-route request failed, in the user's terms rather than HTTP's (§11).
 *
 * [NOT_CONFIGURED] is the shipping default, not an error the user did anything to cause: with no
 * routing key the app simply draws straight segments between stops. It is kept distinct from the
 * network/provider failures so the caller can stay silent about it while still reporting a real
 * outage, and so a test can drive the missing-key path without a build variant.
 */
enum class RoutePlanError {
    /** No routing API key configured. The app falls back to straight lines, silently. */
    NOT_CONFIGURED,

    /** No usable connection, DNS failure, socket refused. */
    NETWORK_UNAVAILABLE,

    /** The provider accepted the request but did not answer in time. */
    TIMEOUT,

    /** Too many requests — back off; the stops themselves are fine. */
    RATE_LIMITED,

    /** The provider was reached but could not connect the stops by road. */
    NO_ROUTE,

    /** The provider answered with an error status (bad key, server fault). */
    PROVIDER_ERROR,

    /** The provider answered successfully with something unparseable. */
    MALFORMED_RESPONSE,

    /** Anything unclassified. Kept so the taxonomy is total and nothing is swallowed. */
    UNKNOWN
}

/**
 * The outcome of asking for a road-following line through a day's stops.
 *
 * [Unavailable] is deliberately not `Routed(emptyList())`: "the road route arrived, here it is" and
 * "no road route — draw the stops as straight legs" are different instructions to the map, and
 * collapsing them would make the screen re-derive the distinction from a list size.
 */
sealed interface RoutePlanOutcome {
    /**
     * Road geometry: the many points that trace the roads through the requested stops, plus the
     * per-leg travel info when the provider supplied it. [legs] aligns to the *requested* stops —
     * leg *i* is stop *i* → stop *i*+1 — and may be empty when the provider gave geometry without a
     * segment breakdown, which the map reads as "distances unknown" rather than zero.
     */
    data class Routed(
        val points: List<RoutePoint>,
        val legs: List<RouteLeg> = emptyList()
    ) : RoutePlanOutcome

    /** No line could be planned; the caller draws straight segments between the stops instead. */
    data class Unavailable(val error: RoutePlanError) : RoutePlanOutcome
}

/** Thrown by a [RoutePlanProvider] to report a classified failure; the service turns it into a value. */
class RoutePlanException(
    val error: RoutePlanError,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message ?: error.name, cause)

/**
 * A single routing backend — OpenRouteService, GraphHopper, a fake in a test.
 *
 * Implementations do one job: turn ordered stops into road geometry, or throw a
 * [RoutePlanException]. Timeouts, the minimum number of stops, and the straight-line fallback are
 * **not** their concern; those are policy and live in [RoutePlanService], so swapping providers
 * cannot change app behaviour.
 */
interface RoutePlanProvider {

    /** False when no key is configured — the shipping default. Lets the service skip the network. */
    val isConfigured: Boolean

    /**
     * @param waypoints the stops to connect, in visiting order. At least two.
     * @return the road geometry (densely enough sampled to trace the roads) and, when the vendor
     *   reports them, the per-leg distances and durations.
     * @throws RoutePlanException on any classified failure.
     */
    suspend fun route(waypoints: List<RoutePoint>): PlannedRoute
}

/**
 * What the app actually calls to draw a route (§11).
 *
 * Nothing about OpenRouteService, HTTP or GeoJSON is visible through this interface; everything past
 * it is replaceable without a screen changing. It never throws — a route that could not be planned
 * (offline, no key, no road) is an ordinary state for this screen, so it comes back as a value the
 * map turns into straight legs.
 */
interface RoutePlanService {

    /** Never throws. Returns [RoutePlanOutcome.Unavailable] for every failure, including no key. */
    suspend fun planRoute(waypoints: List<RoutePoint>): RoutePlanOutcome

    companion object {
        /** Fewer than two stops is not a route; the service returns [RoutePlanOutcome.Unavailable]. */
        const val MIN_WAYPOINTS = 2

        /**
         * Hard ceiling on stops in one request. The free routing tier caps waypoints, and a single
         * day rarely approaches this — but a pathological trip should degrade to a shorter road line
         * rather than a rejected request.
         */
        const val MAX_WAYPOINTS = 50

        /** Hard ceiling on one request, whatever the provider's own timeouts do. */
        const val ROUTE_TIMEOUT_MS = 12_000L
    }
}
