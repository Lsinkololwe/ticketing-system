package com.pml.booking.infrastructure.client.dto;

/**
 * Request to commit reserved inventory to sold.
 *
 * @param quantity Number of tickets to commit
 * @param reservationId Original reservation identifier
 */
public record InventoryCommitRequest(
        int quantity,
        String reservationId
) {}
