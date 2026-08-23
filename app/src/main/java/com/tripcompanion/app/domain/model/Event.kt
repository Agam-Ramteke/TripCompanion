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
    val status: EventStatus = EventStatus.UPCOMING,
    val order: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
