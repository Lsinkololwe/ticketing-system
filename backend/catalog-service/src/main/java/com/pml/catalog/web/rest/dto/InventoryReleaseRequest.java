package com.pml.catalog.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request to release reserved inventory back to available pool.
 *
 * @param quantity Number of tickets to release
 * @param reservationId Original reservation identifier (for audit)
 */
public record InventoryReleaseRequest(
        @Positive(message = "Quantity must be positive")
        int quantity,

        @NotBlank(message = "Reservation ID is required")
        String reservationId
) {}
