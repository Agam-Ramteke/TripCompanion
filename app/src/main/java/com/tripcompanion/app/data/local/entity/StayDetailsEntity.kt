package com.tripcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Hotel paperwork for a STAY event.
 *
 * A side table rather than columns on `events`, so that the eight fields only a hotel has do
 * not sit empty on every sightseeing stop. Keyed by `eventId` and CASCADEd from it: the
 * booking exists because the stay is on the itinerary.
 */
@Entity(
    tableName = "stay_details",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StayDetailsEntity(
    @PrimaryKey val eventId: Long,
    val bookingReference: String = "",
    val roomType: String = "",
    val guests: Int = 1,
    val contactPhone: String = "",
    val address: String = "",
    val checkInInstructions: String = "",
    val photoUri: String? = null
)
