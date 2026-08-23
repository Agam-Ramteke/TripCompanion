package com.tripcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tripcompanion.app.domain.model.TripStatus
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: String = TripStatus.PLANNING.name,
    /**
     * A photo for the trip, copied into app-private storage.
     *
     * Nullable and expected to be null often. Where it is, the hero falls back to a
     * category-coloured gradient rather than a broken-image box.
     */
    val coverImageUri: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
