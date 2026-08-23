package com.tripcompanion.app.ui.screens

import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainBookingStatus
import com.tripcompanion.app.feature.train.TrainPhase
import com.tripcompanion.app.feature.train.trainPhaseAt
import com.tripcompanion.app.ui.components.BadgeTone
import com.tripcompanion.app.ui.components.label
import com.tripcompanion.app.ui.components.tone
import java.time.LocalDateTime

/**
 * How a train booking is described, wherever it is drawn.
 *
 * The Trains list and the itinerary show the same booking (§4), so they share these sentences —
 * the same reasoning as [stayStamp] and its neighbours in `StaySummary.kt`. A journey that says
 * "On board" on one screen cannot say "Upcoming" on the other.
 *
 * Everything here is derived from the booking's own columns and an instant. Nothing asks the
 * network: a list of twelve trains would be twelve requests on a metered free tier to render
 * three words each, and live delay belongs to the one journey the user opened.
 */

/**
 * The pill on the card.
 *
 * A booking that is not confirmed says so first — that is the fact that changes what the traveller
 * does next. It reads the *effective* status, the least settled of the party, so a card cannot say
 * "Departs in 3 h" while one of the two people on the ticket is still waitlisted. Otherwise it
 * answers "where is this journey in time".
 */
internal fun trainStatusLabel(train: Train, now: LocalDateTime): String {
    val booking = train.effectiveBookingStatus
    if (booking != TrainBookingStatus.CONFIRMED) return booking.label
    return when (trainPhaseAt(train, now)) {
        TrainPhase.UPCOMING -> "Departs ${DateTimeUtils.formatTimeUntil(now, train.departureTime)}"
        TrainPhase.RUNNING -> "On board"
        TrainPhase.ARRIVED -> "Arrived"
    }
}

/**
 * Confirmed takes its colour from the clock; everything else from the booking.
 *
 * A confirmed reservation on a train that arrived yesterday is history, not good news, so it goes
 * neutral — which is the one thing this cannot delegate to [TrainBookingStatus.tone], because that
 * property does not know what time it is.
 */
internal fun trainStatusTone(train: Train, now: LocalDateTime): BadgeTone {
    val booking = train.effectiveBookingStatus
    if (booking != TrainBookingStatus.CONFIRMED) return booking.tone
    return when (trainPhaseAt(train, now)) {
        TrainPhase.UPCOMING -> BadgeTone.INFO
        TrainPhase.RUNNING -> BadgeTone.POSITIVE
        TrainPhase.ARRIVED -> BadgeTone.NEUTRAL
    }
}

/** A train with no name of its own is still a train, and its number is what the boards show. */
internal fun trainDisplayName(train: Train): String =
    train.name.ifBlank { "Train ${train.number}" }
