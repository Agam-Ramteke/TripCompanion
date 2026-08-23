package com.tripcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val category: String = "",
    val providerPlaceId: String? = null,
    val providerName: String? = null,
    /** A photo of the place, copied into app-private storage. */
    val photoUri: String? = null,
    /**
     * Rating out of 5, or null when nobody has recorded one.
     *
     * Null rather than 0.0: an unrated place is not a badly rated place, and the difference
     * is visible on screen — one shows no stars, the other shows an empty row of them.
     */
    val rating: Double? = null,
    /** Free text, as it appears on the door: `"9:30 AM – 5:30 PM"`, `"Closed Mondays"`. */
    val openingHours: String = "",
    /** How long to allow for a visit, for planning a day. Null when unknown. */
    val estimatedVisitMinutes: Int? = null,
    /** Ticked off after going. Drives the Visited tab. */
    val isVisited: Boolean = false,
    /** Kept for later. Drives the Saved tab; independent of being on any itinerary. */
    val isSaved: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
