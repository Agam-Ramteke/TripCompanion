package com.tripcompanion.app.domain.model

import java.time.LocalDateTime

data class Location(
    val id: Long = 0,
    val name: String,
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val category: String = "",
    val providerPlaceId: String? = null,
    val providerName: String? = null,
    val photoUri: String? = null,
    /** Out of 5, or null when unrated — which is not the same as rated zero. */
    val rating: Double? = null,
    val openingHours: String = "",
    val estimatedVisitMinutes: Int? = null,
    val isVisited: Boolean = false,
    val isSaved: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
