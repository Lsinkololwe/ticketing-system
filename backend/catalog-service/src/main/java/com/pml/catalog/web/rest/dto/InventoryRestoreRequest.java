package com.pml.catalog.web.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request to restore sold inventory back to available pool.
 * Called on refunds or chargebacks.
 *
 * @param quantity         Number of tickets to restore
 * @param reason           Reason for restoration (REFUND, CHARGEBACK, CANCELLATION)
 * @param grossAmount      What is given back for these tickets, if booking reports it
 * @param commissionAmount The commission given back with it, if booking reports it
 */
public record InventoryRestoreRequest(
        @Positive(message = "Quantity must be positive")
        int quantity,

        @NotBlank(message = "Reason is required")
        String reason,

        @DecimalMin("0.00")
        BigDecimal grossAmount,

        @DecimalMin("0.00")
        BigDecimal commissionAmount
) {
    public InventoryRestoreRequest(int quantity, String reason) {
        this(quantity, reason, null, null);
    }
}
