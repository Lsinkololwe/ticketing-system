package com.pml.booking.infrastructure.client.dto;

/**
 * Result of inventory operations from catalog-service.
 */
public record InventoryOperationResult(
        boolean success,
        String operation,
        String tierId,
        String errorMessage
) {
    public static InventoryOperationResult failure(String operation, String tierId, String errorMessage) {
        return new InventoryOperationResult(false, operation, tierId, errorMessage);
    }
}
