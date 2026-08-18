package com.pml.booking.web.graphql.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Asks for the mobile-money prompt on a held reservation.
 *
 * <p>Notably absent: an amount. The reservation already carries the quote it was
 * created with, and taking a number from the request here would let the caller
 * choose their own price.
 */
public record PayReservationInput(
        @NotBlank(message = "Reservation ID is required")
        String reservationId,

        /** E.164, the handset that will be prompted. */
        @NotBlank(message = "Phone number is required for mobile money payment")
        String phoneNumber
) {}
