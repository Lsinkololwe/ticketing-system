package com.pml.booking.infrastructure.client.dto;

/**
 * Result of an inventory reservation attempt from catalog-service.
 */
public record InventoryReservationResult(
        boolean success,
        int reservedQuantity,
        int remainingAvailable,
        String errorMessage,
        String tierId
) {
    public static InventoryReservationResult failure(String tierId, String errorMessage) {
        return new InventoryReservationResult(false, 0, 0, errorMessage, tierId);
    }
}
