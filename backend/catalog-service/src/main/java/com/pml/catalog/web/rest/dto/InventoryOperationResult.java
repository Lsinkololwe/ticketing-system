package com.pml.catalog.web.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Result of inventory operations (release, commit, restore).
 */
@Data
@Builder
public class InventoryOperationResult {

    /**
     * Whether the operation was successful
     */
    private boolean success;

    /**
     * Operation type performed
     */
    private String operation;

    /**
     * Tier ID for reference
     */
    private String tierId;

    /**
     * Quantity affected by the operation
     */
    private int quantityAffected;

    /**
     * Error message if operation failed
     */
    private String errorMessage;

    /**
     * Current available quantity after operation
     */
    private int currentAvailable;

    /**
     * Current reserved quantity after operation
     */
    private int currentReserved;

    /**
     * Current sold quantity after operation
     */
    private int currentSold;

    public static InventoryOperationResult success(String operation, String tierId, int quantityAffected,
                                                    int currentAvailable, int currentReserved, int currentSold) {
        return InventoryOperationResult.builder()
                .success(true)
                .operation(operation)
                .tierId(tierId)
                .quantityAffected(quantityAffected)
                .currentAvailable(currentAvailable)
                .currentReserved(currentReserved)
                .currentSold(currentSold)
                .build();
    }

    public static InventoryOperationResult failure(String operation, String tierId, String errorMessage) {
        return InventoryOperationResult.builder()
                .success(false)
                .operation(operation)
                .tierId(tierId)
                .errorMessage(errorMessage)
                .build();
    }
}
