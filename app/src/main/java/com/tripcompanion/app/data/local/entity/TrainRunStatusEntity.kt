package com.tripcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tripcompanion.app.domain.model.TrainRunSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The cached running status of a train, and the per-station actuals that go with it.
 *
 * Two tables for one snapshot, which is why they share a file. The header is 1:1 with the
 * train — hence `trainId` as the primary key, so a fetch overwrites rather than accumulates —
 * and the stop rows are replaced wholesale on every fetch. A snapshot is never merged into an
 * older one: half-old, half-new times would be worse than either.
 *
 * This is a cache, and it is treated as one. `fetchedAt` is written on every insert and every
 * screen shows its age. Deleting these two tables costs the user nothing but a refresh.
 */
@Entity(
    tableName = "train_run_status",
    foreignKeys = [
        ForeignKey(
            entity = TrainEntity::class,
            parentColumns = ["id"],
            childColumns = ["trainId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrainRunStatusEntity(
    @PrimaryKey val trainId: Long,
    val fetchedAt: LocalDateTime,
    val runDate: LocalDate,
    val source: String = TrainRunSource.PROJECTED.name,
    val currentStationCode: String = "",
    val currentStationName: String = "",
    val delayMinutes: Int = 0,
    val lastDepartedSerial: Int = 0,
    val progressFraction: Float = 0f,
    val nextStopCode: String = "",
    val nextStopName: String = "",
    val nextStopEta: LocalTime? = null,
    val averageSpeedKmph: Double? = null,
    val message: String = ""
)

/** One station's reported actuals inside a [TrainRunStatusEntity]. */
@Entity(
    tableName = "train_run_stops",
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
data class TrainRunStopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainId: Long,
    val serialNo: Int,
    val stationCode: String,
    val stationName: String,
    val scheduledArrival: LocalTime? = null,
    val actualArrival: LocalTime? = null,
    val scheduledDeparture: LocalTime? = null,
    val actualDeparture: LocalTime? = null,
    val arrivalDelayMinutes: Int? = null,
    val departureDelayMinutes: Int? = null,
    val distanceKm: Int = 0,
    val dayOffset: Int = 0,
    val isDeparted: Boolean = false,
    val isCurrent: Boolean = false
)
