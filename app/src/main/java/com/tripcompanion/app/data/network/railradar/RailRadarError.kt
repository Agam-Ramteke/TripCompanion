package com.tripcompanion.app.data.network.railradar

/**
 * How a RailRadar request or parse failed, in transport terms.
 *
 * Deliberately vendor-shaped rather than user-shaped: two different features consume RailRadar —
 * live train status and PNR lookup — and each maps these into its own user-facing taxonomy
 * ([com.tripcompanion.app.domain.service.TrainStatusError] and
 * [com.tripcompanion.app.domain.service.PnrLookupError] respectively). Putting either of those
 * error types down here would tie the shared client and parser to one of its two callers.
 */
enum class RailRadarErrorKind {
    /** No usable connection, DNS failure, socket refused. */
    NETWORK,

    /** The request was accepted but no answer came back in time. */
    TIMEOUT,

    /** Too many requests. The query is fine; the caller should wait. */
    RATE_LIMITED,

    /** The number or PNR asked about does not exist or is not running. */
    NOT_FOUND,

    /** 401/403 — the API key was missing, wrong, or not entitled to this endpoint. */
    KEY_REJECTED,

    /** Any other error status from RailRadar, or `success: false` in the envelope. */
    PROVIDER_ERROR,

    /** A 200 whose body could not be understood. */
    MALFORMED,

    /** Anything unclassified, kept so the taxonomy is total. */
    UNKNOWN
}

/** Thrown by [RailRadarClient] and [RailRadarParser] to report a classified failure. */
class RailRadarException(
    val kind: RailRadarErrorKind,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message ?: kind.name, cause)
