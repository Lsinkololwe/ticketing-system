package com.pml.booking.infrastructure.client.dto;

/**
 * Request to restore sold inventory.
 *
 * @param quantity Number of tickets to restore
 * @param reason Reason for restoration (REFUND, CHARGEBACK)
 */
public record InventoryRestoreRequest(
        int quantity,
        String reason
) {}
