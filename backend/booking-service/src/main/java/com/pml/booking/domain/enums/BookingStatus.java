package com.pml.booking.domain.enums;

/**
 * What a buyer, an organizer and an administrator are told about a booking.
 *
 * <p>The first five are stored and move with the reservation; the remaining three are
 * <em>derived</em> from the money that came back ({@code BookingStatusRules}) and are never
 * written, so they cannot disagree with the refunds they describe.
 */
public enum BookingStatus {
    /** The hold is open and the buyer is paying. */
    PENDING,
    /** Paid; tickets issued. */
    CONFIRMED,
    /** Released by the buyer or the system before payment. */
    CANCELLED,
    /** The hold lapsed unpaid. */
    EXPIRED,
    /** The payment failed. */
    FAILED,
    /** Some, but not all, of the amount paid has been refunded. */
    PARTIALLY_REFUNDED,
    /** Everything paid has been refunded. */
    REFUNDED,
    /** The payment arrived after the hold lapsed and was refunded in full automatically. */
    PAID_AFTER_EXPIRY_AUTO_REFUNDED
}
