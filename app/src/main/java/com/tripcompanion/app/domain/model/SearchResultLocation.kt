package com.tripcompanion.app.domain.model

/**
 * Represents a place candidate returned from a search provider before user confirmation.
 */
data class SearchResultLocation(
    val name: String,
    val formattedAddress: String,
    val latitude: Double,
    val longitude: Double,
    val category: String = "",
    val providerPlaceId: String? = null,
    val providerName: String = "OpenStreetMap"
)
