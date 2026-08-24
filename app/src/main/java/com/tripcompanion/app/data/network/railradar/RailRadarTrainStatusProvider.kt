package com.tripcompanion.app.data.network.railradar

import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.service.TrainStatusError
import com.tripcompanion.app.domain.service.TrainStatusException
import com.tripcompanion.app.domain.service.TrainStatusProvider
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live running status from RailRadar (railradar.in).
 *
 * The train-side twin of [com.tripcompanion.app.data.network.NominatimLocationSearchProvider]:
 * the vendor's endpoint shape lives here, the JSON lives in [RailRadarParser], the transport in
 * [RailRadarClient], and everything above the data layer is told only about trains. Swapping
 * vendors means writing these files, not touching a screen.
 *
 * This class owns one translation: [RailRadarException] — the client's transport-level failure —
 * into [TrainStatusException] with the user-facing [TrainStatusError] the service layer expects.
 * The parser and client stay ignorant of who is asking; the mapping lives at the seam that knows.
 */
@Singleton
class RailRadarTrainStatusProvider @Inject constructor(
    private val client: RailRadarClient,
    private val timeProvider: TimeProvider
) : TrainStatusProvider {

    override val providerName: String = PROVIDER_NAME

    override val isLive: Boolean = true

    override suspend fun fetchStatus(train: Train, schedule: List<TrainStop>): TrainRunStatus {
        val number = train.number.trim()
        requireUsable(number)

        // The date the *run* began, taken from the booking rather than the clock. An overnight
        // train boarded at 23:40 is still yesterday's train at 01:00; it seeds runDate only when
        // the response omits its own start date.
        val runDate = train.departureTime.toLocalDate()

        val body = mapErrors { client.get("/trains/${encode(number)}/live") }
        return mapErrors {
            RailRadarParser.parseLiveStatus(
                json = body,
                trainId = train.id,
                fetchedAt = timeProvider.now(),
                requestedDate = runDate
            )
        }
    }

    override suspend fun fetchSchedule(trainNumber: String): List<TrainStop> {
        val number = trainNumber.trim()
        requireUsable(number)

        val body = mapErrors { client.get("/trains/${encode(number)}") }
        return mapErrors { RailRadarParser.parseSchedule(body) }
    }

    /**
     * Fails fast on the two inputs that cannot produce a request.
     *
     * A blank key is a build-configuration mistake rather than a network problem, and reporting
     * it as one saves someone reading a stack trace about DNS.
     */
    private fun requireUsable(trainNumber: String) {
        if (!client.isConfigured) {
            throw TrainStatusException(
                TrainStatusError.PROVIDER_ERROR,
                "No API key configured for $PROVIDER_NAME"
            )
        }
        if (trainNumber.isEmpty()) {
            throw TrainStatusException(
                TrainStatusError.TRAIN_NOT_FOUND,
                "This train has no number recorded"
            )
        }
    }

    /** Runs [block], turning any [RailRadarException] into the matching [TrainStatusException]. */
    private inline fun <T> mapErrors(block: () -> T): T =
        try {
            block()
        } catch (e: RailRadarException) {
            throw TrainStatusException(e.kind.toTrainStatusError(), e.message, e)
        }

    private fun RailRadarErrorKind.toTrainStatusError(): TrainStatusError = when (this) {
        RailRadarErrorKind.NETWORK -> TrainStatusError.NETWORK_UNAVAILABLE
        RailRadarErrorKind.TIMEOUT -> TrainStatusError.TIMEOUT
        RailRadarErrorKind.RATE_LIMITED -> TrainStatusError.RATE_LIMITED
        RailRadarErrorKind.NOT_FOUND -> TrainStatusError.TRAIN_NOT_FOUND
        RailRadarErrorKind.KEY_REJECTED -> TrainStatusError.PROVIDER_ERROR
        RailRadarErrorKind.PROVIDER_ERROR -> TrainStatusError.PROVIDER_ERROR
        RailRadarErrorKind.MALFORMED -> TrainStatusError.MALFORMED_RESPONSE
        RailRadarErrorKind.UNKNOWN -> TrainStatusError.UNKNOWN
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val PROVIDER_NAME = "RailRadar"
    }
}
