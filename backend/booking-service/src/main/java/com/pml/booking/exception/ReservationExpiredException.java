package com.pml.booking.exception;

/**
 * A payment landed on a hold that had already lapsed.
 *
 * <h2>Why this is not just "reservation not found"</h2>
 * The distinction decides what happens to the buyer's money. An unknown
 * reservation means the callback is spurious and there is nothing to refund. An
 * expired one means the platform <em>took the payment</em> and no longer has the
 * seats — the inventory went back to the pool ten minutes ago and may already be
 * somebody else's. ET-TKT-001 R7 requires that case be refunded, so it needs its
 * own type rather than a shared "cannot confirm".
 *
 * @see <a href="file:../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001 R7</a>
 */
public class ReservationExpiredException extends RuntimeException {

    /** ET-PLT-005's row for this failure. */
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
