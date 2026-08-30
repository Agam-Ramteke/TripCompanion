package com.tripcompanion.app.domain.model

import java.time.LocalDateTime

data class Event(
    val id: Long = 0,
    val tripId: Long,
    val type: EventType = EventType.VISIT,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val locationId: Long? = null,
    val whatWeAreDoing: String = "",
    val notes: String = "",
    /**
     * A photo the user chose to sit behind this activity on Home's next-up card (§14).
     * Null means "no explicit choice" — the card then falls back to the place or trip photo.
     */
    val backgroundImageUri: String? = null,
    val status: EventStatus = EventStatus.UPCOMING,
    val order: Int = 0,
    val actualStartTime: LocalDateTime? = null,
    val actualEndTime: LocalDateTime? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
