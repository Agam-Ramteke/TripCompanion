package com.tripcompanion.app.domain.model

/**
 * What the ticket cost, broken down the way the e-ticket breaks it down.
 *
 * Every field is nullable because a booking entered by hand has none of them and a ticket
 * whose fare block could not be read has some of them. Zero is a real value here — an IRCTC
 * ticket with no agent genuinely has a `0.00` agent service charge — so "not known" has to be
 * a different thing from "nothing".
 */
data class TrainFare(
    val ticketFare: Double? = null,
    val convenienceFee: Double? = null,
    val insurancePremium: Double? = null,
    val agentServiceCharge: Double? = null,
    val paymentGatewayCharge: Double? = null,
    val totalFare: Double? = null
) {

    /** True when there is nothing here worth giving a section to. */
    val isEmpty: Boolean
        get() = listOf(
            ticketFare, convenienceFee, insurancePremium,
            agentServiceCharge, paymentGatewayCharge, totalFare
        ).all { it == null }

    /**
     * The number to put on a summary line: the total if it was printed, else the fare itself.
     *
     * Never a sum computed from the parts. A total this app added up would disagree with the
     * one on the ticket the moment a charge failed to parse, and the ticket is the receipt.
     */
    val headline: Double? get() = totalFare ?: ticketFare
}
