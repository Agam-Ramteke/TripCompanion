package com.tripcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores a reference to an image, never the image itself (§16) — the bytes live
 * in app-private storage and only the path is persisted here.
 */
@Entity(
    tableName = "planned_photos",
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
data class PlannedPhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: Long,
    val title: String = "",
    val referenceImageUri: String? = null,
    val order: Int = 0
)
