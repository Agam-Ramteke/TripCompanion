package com.tripcompanion.app.ui.screens

import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.Event
import com.tripcompanion.app.domain.model.Location
import com.tripcompanion.app.domain.model.StayDetails
import com.tripcompanion.app.ui.components.BadgeTone
import java.time.Duration
import java.time.LocalDateTime

/**
 * How a stay is described, wherever it is drawn.
 *
 * The stay screen and the itinerary show the same booking, so they share these sentences rather
 * than each writing their own — a room that says "Checked in" on one screen cannot say "Upcoming"
 * on the other. Everything is derived from the STAY event's own two timestamps plus the paperwork
 * keyed to it; nothing about a stay is stored twice.
 *
 * These take an [Event] and an instant rather than a screen's state object, because two different
 * screens with two different states need the same answer.
 */

/** Check-in and check-out carry the date as well as the time: a stay spans days. */
internal fun stayStamp(moment: LocalDateTime): String =
    DateTimeUtils.formatShortDate(moment.toLocalDate()) + " · " + DateTimeUtils.formatTime(moment)

/**
 * Nights, counted from the two dates on the event.
 *
 * Whole days between the dates, not hours divided by 24: a 14:00 check-in to a 10:30 check-out
 * four days later is four nights, and the hours arithmetic says three.
 */
internal fun stayNights(stay: Event): Int =
    Duration.between(
        stay.startTime.toLocalDate().atStartOfDay(),
        stay.endTime.toLocalDate().atStartOfDay()
    ).toDays().toInt().coerceAtLeast(0)

/** Null rather than "0 nights" — a same-day booking has a duration, not a night count. */
internal fun stayNightsLabel(nights: Int): String? = when (nights) {
    0 -> null
    1 -> "1 night"
    else -> "$nights nights"
}

internal fun stayIsCheckedIn(stay: Event, now: LocalDateTime): Boolean =
    !now.isBefore(stay.startTime) && now.isBefore(stay.endTime)

internal fun stayHasCheckedOut(stay: Event, now: LocalDateTime): Boolean =
    !now.isBefore(stay.endTime)

/** Where the stay stands, as the pill on the card. */
internal fun stayStatusLabel(stay: Event, now: LocalDateTime): String = when {
    stayHasCheckedOut(stay, now) -> "Checked out"
    stayIsCheckedIn(stay, now) -> "Checked in"
    else -> "Upcoming"
}

internal fun stayStatusTone(stay: Event, now: LocalDateTime): BadgeTone = when {
    stayHasCheckedOut(stay, now) -> BadgeTone.NEUTRAL
    stayIsCheckedIn(stay, now) -> BadgeTone.POSITIVE
    else -> BadgeTone.INFO
}

/**
 * The hotel's own address if it was entered, else the geocoder's.
 *
 * A confirmation gives an address in the property's words, which is what a driver understands;
 * the location's address is a rendering of a coordinate, which is often less useful.
 */
internal fun stayAddress(details: StayDetails?, place: Location?): String? =
    details?.address?.takeIf { it.isNotBlank() }
        ?: place?.address?.takeIf { it.isNotBlank() }

/**
 * The picture on the card: the room the user photographed, else the place's own picture.
 *
 * Both are the user's images copied into app storage, so either one loads offline; when there is
 * neither, [com.tripcompanion.app.ui.components.HotelCard] draws its own placeholder.
 */
internal fun stayImageUri(details: StayDetails?, place: Location?): String? =
    details?.photoUri ?: place?.photoUri
