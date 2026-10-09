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
     * Error message if operation failed
     */
    private String errorMessage;

    public static InventoryOperationResult success(String operation, String tierId) {
        return InventoryOperationResult.builder()
                .success(true)
                .operation(operation)
                .tierId(tierId)
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
