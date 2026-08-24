package com.tripcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import java.time.LocalDateTime

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val type: String = EventType.VISIT.name,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val locationId: Long? = null,
    val whatWeAreDoing: String = "",
    val notes: String = "",
    val backgroundImageUri: String? = null,
    val status: String = EventStatus.UPCOMING.name,
    val order: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
