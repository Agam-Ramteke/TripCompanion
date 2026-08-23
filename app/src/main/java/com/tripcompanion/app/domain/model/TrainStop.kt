package com.tripcompanion.app.domain.model

import java.time.LocalTime

/**
 * One station on a train's timetable.
 *
 * The *schedule*, not the run: nothing here changes when the train is late. Live actuals
 * live in [TrainStopStatus], which is fetched, cached and thrown away — keeping the two
 * apart is what lets the Route tab render a full route offline and then overlay real times
 * when they arrive.
 *
 * [scheduledArrival] is null at the origin and [scheduledDeparture] is null at the
 * terminus, because a train does not arrive where it starts. The API says `"Source"` and
 * `"Destination"` in those slots; those strings are the API's business and are parsed away
 * at the boundary rather than carried into the app as sentinel text.
 */
data class TrainStop(
    val id: Long = 0,
    /** Zero when a provider builds a schedule it has not yet been persisted against. */
    val trainId: Long = 0,
    /** 1-based position along the route, as the timetable numbers it. */
    val serialNo: Int,
    val stationCode: String,
    val stationName: String,
    val scheduledArrival: LocalTime? = null,
    val scheduledDeparture: LocalTime? = null,
    /** Cumulative distance from the origin. The origin is 0. */
    val distanceKm: Int = 0,
    /**
     * Days after departure that this stop falls on — 0 on the first day, 1 after the first
     * midnight. The API's `Day` field is 1-based; it is normalised here.
     *
     * Without this an overnight train's 02:15 arrival sorts before its own 22:40 departure
     * and the route renders backwards.
     */
    val dayOffset: Int = 0
) {
    /** The time this stop is *used*: departure where there is one, otherwise arrival. */
    val scheduledTime: LocalTime?
        get() = scheduledDeparture ?: scheduledArrival

    val isOrigin: Boolean get() = scheduledArrival == null
    val isTerminus: Boolean get() = scheduledDeparture == null
}
