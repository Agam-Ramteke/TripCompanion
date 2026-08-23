package com.tripcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tripcompanion.app.domain.model.ActivityStatus

@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("eventId")]
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: Long,
    val title: String,
    val category: String = "",
    val order: Int = 0,
    val isOptional: Boolean = false,
    val completionStatus: String = ActivityStatus.PENDING.name,
    val notes: String = ""
)
