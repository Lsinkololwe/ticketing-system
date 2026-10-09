package com.pml.booking.domain.enums;

/**
 * Which kind of mobile-money provider call a payment attempt records.
 *
 * <ul>
 *   <li>{@code COLLECT} — a buyer's payment for tickets (a provider deposit).</li>
 *   <li>{@code REFUND} — money returned to a buyer against an earlier deposit.</li>
 *   <li>{@code PAYOUT} — an organizer's escrow balance sent to their account.</li>
 *   <li>{@code VERIFICATION} — the small deposit that proves an organizer controls an account.</li>
 * </ul>
 */
public enum PaymentAttemptType {
    COLLECT,
    REFUND,
    PAYOUT,
    VERIFICATION
}
