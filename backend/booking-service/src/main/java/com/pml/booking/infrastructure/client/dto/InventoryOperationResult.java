package com.pml.booking.infrastructure.client.dto;

/**
 * Result of inventory operations from catalog-service.
 */
public record InventoryOperationResult(
        boolean success,
        String operation,
        String tierId,
        int quantityAffected,
        String errorMessage,
        int currentAvailable,
        int currentReserved,
        int currentSold
) {
    public static InventoryOperationResult failure(String operation, String tierId, String errorMessage) {
        return new InventoryOperationResult(false, operation, tierId, 0, errorMessage, 0, 0, 0);
    }
}
