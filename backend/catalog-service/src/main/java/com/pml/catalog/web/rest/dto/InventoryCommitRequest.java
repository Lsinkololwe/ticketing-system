package com.pml.catalog.web.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request to commit reserved inventory to sold state.
 * Called when payment is completed successfully.
 *
 * @param quantity         Number of tickets to commit
 * @param reservationId    Original reservation identifier
 * @param grossAmount      What the buyer paid for these tickets, if booking reports it; the event's
 *                         gross sales follow it
 * @param commissionAmount The platform's commission on that amount, if booking reports it
 */
public record InventoryCommitRequest(
        @Positive(message = "Quantity must be positive")
        int quantity,

        @NotBlank(message = "Reservation ID is required")
        String reservationId,

        @DecimalMin("0.00")
        BigDecimal grossAmount,

        @DecimalMin("0.00")
        BigDecimal commissionAmount
) {
    public InventoryCommitRequest(int quantity, String reservationId) {
        this(quantity, reservationId, null, null);
    }
}
