package com.tripcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tripcompanion.app.domain.model.TrainBookingStatus
import java.time.LocalDateTime

/**
 * A booked train, owned by its trip.
 *
 * CASCADE on the trip: deleting a trip deletes its trains, because a ticket with no trip is
 * unreachable from every screen in the app.
 *
 * [eventId] is *not* a foreign key. The link to the itinerary is advisory — a train may be
 * recorded before any JOURNEY event exists, and deleting an event should not delete the
 * ticket — so it is a plain nullable column that readers treat as a hint.
 *
 * There is no coach or berth here. Those belong to a person, not to a booking, and live on
 * [TrainPassengerEntity] — see its note. [bookingStatus] survives as the status of a train with
 * no passengers recorded, which is the ordinary state of one added by hand before the ticket
 * comes through.
 *
 * Everything from [quota] down is printed on an e-ticket and nothing else supplies it. All of it
 * is optional, and nullable where zero would be a lie: a fare of `0.0` is a real fare, so an
 * unknown fare has to be `null`.
 */
@Entity(
    tableName = "trains",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class TrainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val eventId: Long? = null,
    val number: String,
    val name: String = "",
    val originCode: String = "",
    val originName: String = "",
    val destinationCode: String = "",
    val destinationName: String = "",
    val departureTime: LocalDateTime,
    val arrivalTime: LocalDateTime,
    val actualBoardingTime: LocalDateTime? = null,
    val actualDepartureTime: LocalDateTime? = null,
    val actualArrivalTime: LocalDateTime? = null,
    val arrivalSource: String? = null,
    val travelClass: String = "",
    val pnr: String = "",
    val bookingStatus: String = TrainBookingStatus.NOT_BOOKED.name,
    val platform: String = "",
    val knownDelayMinutes: Int = 0,
    val notes: String = "",

    // ── From the e-ticket ──

    val quota: String = "",
    val distanceKm: Int = 0,
    val boardingCode: String = "",
    val boardingName: String = "",
    val bookedAt: LocalDateTime? = null,
    val agentName: String = "",
    val agentBookingId: String = "",
    val transactionId: String = "",
    val fareTicket: Double? = null,
    val fareConvenience: Double? = null,
    val fareInsurance: Double? = null,
    val fareAgentService: Double? = null,
    val farePaymentGateway: Double? = null,
    val fareTotal: Double? = null,

    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)
