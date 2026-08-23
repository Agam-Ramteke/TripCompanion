package com.tripcompanion.app.domain.model

/**
 * The parts of a hotel booking that are not already on the STAY event.
 *
 * Check-in and check-out are the event's own start and end times, so they are deliberately
 * absent here — one fact, one home. What is left is the paperwork: the reference to quote at
 * the desk, the room, who it is booked for, and how to reach the property.
 *
 * Keyed by [eventId] rather than carrying its own id, because a stay without an event on the
 * itinerary is not a thing this app can show anywhere.
 */
data class StayDetails(
    val eventId: Long,
    val bookingReference: String = "",
    val roomType: String = "",
    val guests: Int = 1,
    val contactPhone: String = "",
    /**
     * The property's street address.
     *
     * Duplicated from the linked [Location] on purpose: a hotel confirmation gives an
     * address in the hotel's own words, and that is what someone shows a driver. The
     * location's address is a geocoder's rendering of a coordinate, which is a different
     * and often less useful string.
     */
    val address: String = "",
    val checkInInstructions: String = "",
    val photoUri: String? = null
)
