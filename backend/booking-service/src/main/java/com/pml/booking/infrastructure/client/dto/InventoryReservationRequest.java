package com.pml.booking.infrastructure.client.dto;

/**
 * Request to reserve inventory for a ticket tier.
 *
 * @param quantity Number of tickets to reserve
 * @param reservationId Unique identifier for this reservation
 */
public record InventoryReservationRequest(
        int quantity,
        String reservationId
) {}
