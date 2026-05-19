package com.pml.booking.infrastructure.client.dto;

/**
 * Request to restore sold inventory.
 *
 * @param quantity Number of tickets to restore
 * @param reason Reason for restoration (REFUND, CHARGEBACK)
 * @param referenceId Reference to the refund/chargeback record
 */
public record InventoryRestoreRequest(
        int quantity,
        String reason,
        String referenceId
) {}
