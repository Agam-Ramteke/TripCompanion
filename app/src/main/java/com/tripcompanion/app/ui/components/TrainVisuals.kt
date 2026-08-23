package com.tripcompanion.app.ui.components

import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainBookingStatus

/**
 * How a reservation status reads and what colour it carries — declared once.
 *
 * The same five states now appear on a train card, a ticket, the detail screen's passenger
 * list and the editor. Written as a `when` at each of those, one of them ends up calling a
 * waitlisted passenger "Not booked", and the four disagree about whether RAC is a warning.
 * Adding a case to [TrainBookingStatus] breaks exactly this file, which is the intent —
 * the same contract [EventType.icon] carries for events.
 */
val TrainBookingStatus.label: String
    get() = when (this) {
        TrainBookingStatus.CONFIRMED -> "Confirmed"
        TrainBookingStatus.RAC -> "RAC"
        TrainBookingStatus.WAITLISTED -> "Waitlisted"
        TrainBookingStatus.CANCELLED -> "Cancelled"
        TrainBookingStatus.NOT_BOOKED -> "Not booked"
    }

/**
 * The label, minus the one that says nothing.
 *
 * A ticket stamped "Confirmed" is stating the obvious: every screen here is showing a booking
 * the traveller made, and confirmed is what a booking normally is. The states worth a badge
 * are the ones that change what the traveller does next.
 */
val TrainBookingStatus.noteworthyLabel: String?
    get() = if (this == TrainBookingStatus.CONFIRMED) null else label

/**
 * The badge colour for a booking on its own.
 *
 * A confirmed booking reads positive here. Where a screen also knows *when* the train is —
 * the Trains list does — it may soften a confirmed badge to neutral once the train has
 * arrived, because by then the confirmation is history rather than news.
 */
val TrainBookingStatus.tone: BadgeTone
    get() = when (this) {
        TrainBookingStatus.CONFIRMED -> BadgeTone.POSITIVE
        TrainBookingStatus.RAC -> BadgeTone.WARNING
        TrainBookingStatus.WAITLISTED -> BadgeTone.WARNING
        TrainBookingStatus.CANCELLED -> BadgeTone.NEGATIVE
        TrainBookingStatus.NOT_BOOKED -> BadgeTone.NEUTRAL
    }

/**
 * The one line under a train card: class, coach, berths.
 *
 * Singular or plural from what is actually there, because "Berths 8" on a solo ticket and
 * "Berth 6, 8" on a pair both read as mistakes. Two screens show this line — Home's journey
 * card and the Trains list — and they had a copy each before a booking could carry a party.
 *
 * Null rather than a line of dashes when nothing has been allotted. A shorter card is better
 * than a card padded with em dashes, except when the party is waitlisted: then the queue
 * position is the whole story and it goes here instead of the berth.
 */
fun Train.bookingSummary(): String? {
    val coaches = coachSummary
    val berths = berthSummary
    val parts = buildList {
        if (travelClass.isNotBlank()) add(travelClass)
        if (coaches.isNotBlank()) {
            add(if (coaches.contains(',')) "Coaches $coaches" else "Coach $coaches")
        }
        if (berths.isNotBlank()) {
            add(if (berths.contains(',')) "Berths $berths" else "Berth $berths")
        }
        if (coaches.isBlank() && berths.isBlank()) {
            passengers.firstNotNullOfOrNull { passenger ->
                passenger.allotment.queueLabel.takeIf { it.isNotBlank() }
            }?.let(::add)
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
