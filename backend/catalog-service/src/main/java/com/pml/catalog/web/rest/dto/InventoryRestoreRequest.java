package com.pml.catalog.web.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request to restore sold inventory back to available pool.
 * Called on refunds or chargebacks.
 *
 * @param quantity Number of tickets to restore
 * @param reason Reason for restoration (REFUND, CHARGEBACK, CANCELLATION)
 * @param referenceId Reference to the refund/chargeback record
 */
public record InventoryRestoreRequest(
        @Positive(message = "Quantity must be positive")
        int quantity,

        @NotBlank(message = "Reason is required")
        String reason,

        String referenceId
) {}
