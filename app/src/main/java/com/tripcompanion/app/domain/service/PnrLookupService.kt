package com.tripcompanion.app.domain.service

import com.tripcompanion.app.domain.model.ParsedTicket

/**
 * Looks up a booking by its PNR.
 *
 * §11's boundary, same as [TrainStatusService] and [LocationSearchProvider]: nothing about HTTP,
 * JSON, API keys or which railway's API answers is visible through here. The editor asks for a
 * PNR and gets back a [ParsedTicket] to fill its form with — the same type the e-ticket import
 * produces, so a fetched booking lands in exactly the controls a scanned one does.
 *
 * A PNR is a thinner source than an e-ticket by nature: it carries the train, the route ends, the
 * boarding point and each passenger's coach, berth and status, but never their names, ages or
 * genders, and no clock times. The result reflects that honestly rather than inventing the gaps.
 */
interface PnrLookupService {

    /**
     * Whether a lookup can be made at all.
     *
     * False when no API key is configured — the same condition that binds the offline projection
     * instead of live status. Screens hide the fetch affordance rather than offer a button that
     * can only fail.
     */
    val isAvailable: Boolean

    /**
     * Fetch the booking behind [pnr].
     *
     * Never throws for an expected failure — a bad PNR, no signal, a metered limit — because those
     * are values the editor renders inline, not exceptions. [PnrLookupOutcome.Failed] carries the
     * classified reason.
     */
    suspend fun lookup(pnr: String): PnrLookupOutcome
}

/** The result of a PNR lookup. */
sealed interface PnrLookupOutcome {
    /** A booking was found. [ticket] is a first draft to fill the editor with, all of it editable. */
    data class Found(val ticket: ParsedTicket) : PnrLookupOutcome

    /** The lookup failed. [error] says why, in the user's terms. */
    data class Failed(val error: PnrLookupError) : PnrLookupOutcome
}

/**
 * What can go wrong looking up a PNR, in the user's categories rather than HTTP's.
 *
 * Same reasoning as [TrainStatusError]: "no such PNR" and "we couldn't reach the railway" are one
 * thing to a network library and two different sentences on screen.
 */
enum class PnrLookupError {
    /** No API key configured, so no lookup is possible. The screen should not have offered one. */
    NOT_CONFIGURED,

    /** No usable connection, DNS failure, socket refused. */
    NETWORK_UNAVAILABLE,

    /** The provider accepted the request but did not answer in time. */
    TIMEOUT,

    /** Too many requests. The query is fine; the caller should wait. */
    RATE_LIMITED,

    /** No booking exists for that PNR, or the number is not a valid one. */
    NOT_FOUND,

    /** The provider answered successfully with something unparseable. */
    MALFORMED,

    /** Anything unclassified, kept so the taxonomy is total. */
    UNKNOWN
}
