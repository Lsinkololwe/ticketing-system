package com.pml.booking.infrastructure.client.dto;

/**
 * Request to release reserved inventory.
 *
 * @param quantity Number of tickets to release
 * @param reservationId Original reservation identifier
 */
public record InventoryReleaseRequest(
        int quantity,
        String reservationId
) {}
