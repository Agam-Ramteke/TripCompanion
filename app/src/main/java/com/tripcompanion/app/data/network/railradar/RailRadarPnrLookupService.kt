package com.tripcompanion.app.data.network.railradar

import com.tripcompanion.app.domain.service.PnrLookupError
import com.tripcompanion.app.domain.service.PnrLookupOutcome
import com.tripcompanion.app.domain.service.PnrLookupService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PNR lookups over RailRadar's `GET /v1/pnr/{pnr}`.
 *
 * Shares [RailRadarClient] with the live-status provider, so the transport is configured once. Its
 * job here is the same translation the provider does for status — [RailRadarException] into the
 * feature's own [PnrLookupError] — plus the two guards a lookup needs that a status refresh does
 * not: a key must exist, and a PNR is exactly ten digits, so both are checked before a request is
 * spent on something that cannot succeed.
 */
@Singleton
class RailRadarPnrLookupService @Inject constructor(
    private val client: RailRadarClient
) : PnrLookupService {

    override val isAvailable: Boolean get() = client.isConfigured

    override suspend fun lookup(pnr: String): PnrLookupOutcome {
        if (!client.isConfigured) return PnrLookupOutcome.Failed(PnrLookupError.NOT_CONFIGURED)

        val trimmed = pnr.trim()
        if (trimmed.length != PNR_LENGTH || !trimmed.all { it.isDigit() }) {
            return PnrLookupOutcome.Failed(PnrLookupError.NOT_FOUND)
        }

        return try {
            val body = withTimeout(REQUEST_TIMEOUT_MS) { client.get("/pnr/$trimmed") }
            PnrLookupOutcome.Found(RailRadarParser.parsePnr(body))
        } catch (_: TimeoutCancellationException) {
            PnrLookupOutcome.Failed(PnrLookupError.TIMEOUT)
        } catch (e: CancellationException) {
            // The screen moved on. Not a failure; rethrowing keeps structured concurrency intact.
            throw e
        } catch (e: RailRadarException) {
            PnrLookupOutcome.Failed(e.kind.toPnrLookupError())
        } catch (_: Exception) {
            PnrLookupOutcome.Failed(PnrLookupError.UNKNOWN)
        }
    }

    private fun RailRadarErrorKind.toPnrLookupError(): PnrLookupError = when (this) {
        RailRadarErrorKind.NETWORK -> PnrLookupError.NETWORK_UNAVAILABLE
        RailRadarErrorKind.TIMEOUT -> PnrLookupError.TIMEOUT
        RailRadarErrorKind.RATE_LIMITED -> PnrLookupError.RATE_LIMITED
        RailRadarErrorKind.NOT_FOUND -> PnrLookupError.NOT_FOUND
        RailRadarErrorKind.MALFORMED -> PnrLookupError.MALFORMED
        // A rejected or unentitled key, or any other provider error, is nothing the user can act
        // on from the editor — reported as the catch-all rather than a misleading "bad PNR".
        RailRadarErrorKind.KEY_REJECTED,
        RailRadarErrorKind.PROVIDER_ERROR,
        RailRadarErrorKind.UNKNOWN -> PnrLookupError.UNKNOWN
    }

    companion object {
        private const val PNR_LENGTH = 10

        /** Hard ceiling on one lookup, whatever the client's own socket timeouts do. */
        private const val REQUEST_TIMEOUT_MS = 12_000L
    }
}
