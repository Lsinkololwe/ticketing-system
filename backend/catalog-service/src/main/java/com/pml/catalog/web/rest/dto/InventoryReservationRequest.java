package com.pml.catalog.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request to reserve inventory for a ticket tier.
 *
 * @param quantity Number of tickets to reserve
 * @param reservationId Unique identifier for this reservation (for idempotency)
 */
public record InventoryReservationRequest(
        @Positive(message = "Quantity must be positive")
        int quantity,

        @NotBlank(message = "Reservation ID is required")
        String reservationId
) {}
