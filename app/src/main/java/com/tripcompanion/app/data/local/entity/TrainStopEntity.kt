package com.tripcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalTime

/**
 * One station on a train's stored timetable.
 *
 * Written once when the schedule is fetched or entered, then read offline forever. Nothing
 * about a delay belongs in this table — that is [TrainRunStopEntity]'s job — which is what
 * lets the Route tab draw a complete route on a plane.
 */
@Entity(
    tableName = "train_stops",
    foreignKeys = [
        ForeignKey(
            entity = TrainEntity::class,
            parentColumns = ["id"],
            childColumns = ["trainId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trainId")]
)
data class TrainStopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainId: Long,
    val serialNo: Int,
    val stationCode: String,
    val stationName: String,
    val scheduledArrival: LocalTime? = null,
    val scheduledDeparture: LocalTime? = null,
    val distanceKm: Int = 0,
    val dayOffset: Int = 0
)
