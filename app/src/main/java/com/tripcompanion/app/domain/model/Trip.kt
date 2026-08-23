package com.tripcompanion.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class Trip(
    val id: Long = 0,
    val name: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val status: TripStatus = TripStatus.PLANNING,
    /** A photo for the trip, in app-private storage. Null is ordinary and handled everywhere. */
    val coverImageUri: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
