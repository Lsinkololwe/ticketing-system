package com.pml.booking.exception;

/**
 * A payment landed on a hold that had already lapsed.
 *
 * <h2>Why this is not just "reservation not found"</h2>
 * The distinction decides what happens to the buyer's money. An unknown
 * reservation means the callback is spurious and there is nothing to refund. An
 * expired one means the platform <em>took the payment</em> and no longer has the
 * seats — the inventory went back to the pool ten minutes ago and may already be
 * somebody else's. That case has to be refunded, so it needs its
 * own type rather than a shared "cannot confirm".
 */
public class ReservationExpiredException extends RuntimeException {

    /** The error code for this failure. */
    public static final String CODE = "RESERVATION_EXPIRED";

    private final String reservationId;

    public ReservationExpiredException(String reservationId) {
        super(CODE + ": reservation " + reservationId + " expired before the payment completed");
        this.reservationId = reservationId;
    }

    public String getReservationId() {
        return reservationId;
    }
}
