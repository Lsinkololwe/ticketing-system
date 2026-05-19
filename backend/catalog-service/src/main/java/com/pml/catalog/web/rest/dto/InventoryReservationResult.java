package com.pml.catalog.web.rest.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Result of an inventory reservation attempt.
 *
 * Indicates whether the reservation was successful and provides
 * current inventory state for transparency.
 */
@Data
@Builder
public class InventoryReservationResult {

    /**
     * Whether the reservation was successful
     */
    private boolean success;

    /**
     * Number of tickets reserved (0 if failed)
     */
    private int reservedQuantity;

    /**
     * Remaining available quantity after reservation
     */
    private int remainingAvailable;

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
    public static InventoryReservationResult success(String tierId, int reservedQuantity, int remainingAvailable) {
        return InventoryReservationResult.builder()
                .success(true)
                .tierId(tierId)
                .reservedQuantity(reservedQuantity)
                .remainingAvailable(remainingAvailable)
                .build();
    }

    /**
     * Create a failed reservation result.
     */
    public static InventoryReservationResult failure(String tierId, String errorMessage) {
        return InventoryReservationResult.builder()
                .success(false)
                .tierId(tierId)
                .reservedQuantity(0)
                .errorMessage(errorMessage)
                .build();
    }
}
