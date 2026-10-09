package com.pml.catalog.web.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Result of an inventory reservation attempt.
 *
 * Indicates whether the reservation was successful.
 */
@Data
@Builder
public class InventoryReservationResult {

    /**
     * Whether the reservation was successful
     */
    private boolean success;

    /**
     * Error message if reservation failed
     */
    private String errorMessage;

    /**
     * Tier ID for reference
     */
    private String tierId;

    /**
     * Create a successful reservation result.
     */
    public static InventoryReservationResult success(String tierId) {
        return InventoryReservationResult.builder()
                .success(true)
                .tierId(tierId)
                .build();
    }

    /**
     * Create a failed reservation result.
     */
    public static InventoryReservationResult failure(String tierId, String errorMessage) {
        return InventoryReservationResult.builder()
                .success(false)
                .tierId(tierId)
                .errorMessage(errorMessage)
                .build();
    }
}
