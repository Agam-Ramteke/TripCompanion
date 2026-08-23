package com.tripcompanion.app.domain.model

data class Activity(
    val id: Long = 0,
    val eventId: Long,
    val title: String,
    val category: String = "",
    val order: Int = 0,
    val isOptional: Boolean = false,
    val completionStatus: ActivityStatus = ActivityStatus.PENDING,
    val notes: String = ""
)
