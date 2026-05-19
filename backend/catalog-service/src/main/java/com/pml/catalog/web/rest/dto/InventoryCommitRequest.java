package com.pml.catalog.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request to commit reserved inventory to sold state.
 * Called when payment is completed successfully.
 *
 * @param quantity Number of tickets to commit
 * @param reservationId Original reservation identifier
 */
public record InventoryCommitRequest(
        @Positive(message = "Quantity must be positive")
        int quantity,

        @NotBlank(message = "Reservation ID is required")
        String reservationId
) {}
