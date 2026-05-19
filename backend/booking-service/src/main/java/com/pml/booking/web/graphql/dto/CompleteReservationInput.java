package com.pml.booking.web.graphql.dto;

import com.pml.shared.constants.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Input for completing a reservation with payment.
 */
public record CompleteReservationInput(
    @NotBlank(message = "Reservation ID is required")
    String reservationId,

    @NotNull(message = "Payment method is required")
    PaymentMethod paymentMethod,

    @NotBlank(message = "Phone number is required")
    String phoneNumber,

    String promoCode
) {}
