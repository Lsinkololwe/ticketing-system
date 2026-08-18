package com.pml.booking.exception;

/**
 * No reservation exists with the given id.
 *
 * <p>Distinct from {@link ReservationExpiredException} because the two demand
 * opposite responses to an arriving payment: an unknown reservation means there
 * is nothing to refund, an expired one means there is.
 */
public class ReservationNotFoundException extends RuntimeException {

    /** ET-PLT-005's row for this failure. */
    public static final String CODE = "RESERVATION_UNKNOWN";

    private final String reservationId;

    public ReservationNotFoundException(String reservationId) {
        super(CODE + ": " + reservationId);
        this.reservationId = reservationId;
    }

    public String getReservationId() {
        return reservationId;
    }
}
