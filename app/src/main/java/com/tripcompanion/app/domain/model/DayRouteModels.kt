package com.tripcompanion.app.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Subway
import androidx.compose.material.icons.filled.Train
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Transportation modes between stops in a day itinerary.
 */
enum class TransportMode(val label: String) {
    CAR("Car"),
    WALK("Walk"),
    BUS("Bus"),
    METRO("Metro"),
    TRAIN("Train");

    val icon: ImageVector
        get() = when (this) {
            CAR -> Icons.Default.DirectionsCar
            WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
            BUS -> Icons.Default.DirectionsBus
            METRO -> Icons.Default.Subway
            TRAIN -> Icons.Default.Train
        }
}

/**
 * Visual progress status for a route leg connecting two consecutive stops.
 */
enum class RouteLegStatus {
    /** The leg has already been traversed (both stops behind traveller). */
    COMPLETED,
    /** The leg currently leading into the next or active destination. */
    CURRENT,
    /** Ahead in the day's itinerary. */
    FUTURE
}

/**
 * A single route leg connecting two consecutive stops in a day's journey.
 */
data class DayRouteLeg(
    val id: String,
    val fromStopId: Long,
    val toStopId: Long,
    val fromName: String,
    val toName: String,
    val mode: TransportMode = TransportMode.CAR,
    val distanceMeters: Double,
    val durationSeconds: Double?,
    val points: List<Pair<Double, Double>>,
    val status: RouteLegStatus = RouteLegStatus.FUTURE
)
