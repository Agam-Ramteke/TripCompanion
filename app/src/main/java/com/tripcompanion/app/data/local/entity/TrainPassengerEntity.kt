package com.tripcompanion.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tripcompanion.app.domain.model.BerthType
import com.tripcompanion.app.domain.model.PassengerGender
import com.tripcompanion.app.domain.model.TrainBookingStatus

/**
 * One passenger on one booking.
 *
 * A separate table because a PNR covers a party and the railway allots a berth per person:
 * columns on `trains` could only ever describe one of them. Deleting the train takes its
 * passengers with it — a passenger with no train is not a record of anything.
 *
 * The allotment is stored flat, as parsed columns *and* as the two original strings. The
 * columns are what screens sort and group by; the strings are what the ticket actually said,
 * kept so that a booking whose status this app failed to understand can still be shown to the
 * person holding it, and so a re-parse after a fix can never disagree with the source.
 */
@Entity(
    tableName = "train_passengers",
    foreignKeys = [
        ForeignKey(
            entity = TrainEntity::class,
            parentColumns = ["id"],
            childColumns = ["trainId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trainId")]
)
data class TrainPassengerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trainId: Long,

    /** The `#` column on the e-ticket, so the app lists people in chart order. */
    val serialNo: Int = 1,
    val name: String = "",

    /** Nullable because "age not recorded" is not the same as an age of zero. */
    val age: Int? = null,
    val gender: String = PassengerGender.UNSPECIFIED.name,

    // ── The allotment, parsed ──

    val status: String = TrainBookingStatus.NOT_BOOKED.name,
    val coach: String = "",
    val berth: String = "",
    val berthType: String = BerthType.UNKNOWN.name,

    /** Place in the waitlist or RAC queue. Never written into [berth]: a queue place is not a berth. */
    val queuePosition: Int? = null,
    val queueKind: String = "",

    // ── The allotment, verbatim ──

    val bookingStatusText: String = "",
    val currentStatusText: String = ""
)
