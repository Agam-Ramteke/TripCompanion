package com.tripcompanion.app.data.local.converter

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class Converters {
    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Timetable times are minute-resolution and stored as "HH:mm" (§9 — seconds are never
    // part of a time in this app). ISO_LOCAL_TIME would also emit seconds when they are
    // non-zero, which would let a stray second survive a round trip.
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? =
        value?.format(dateTimeFormatter)

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? =
        value?.let { LocalDateTime.parse(it, dateTimeFormatter) }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? =
        value?.format(dateFormatter)

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? =
        value?.let { LocalDate.parse(it, dateFormatter) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? =
        value?.format(timeFormatter)

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? =
        value?.let { LocalTime.parse(it, timeFormatter) }
}
