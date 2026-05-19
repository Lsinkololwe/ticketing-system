package com.pml.booking.web.graphql.dto;

import java.util.List;

/**
 * Bulk Operation Response DTO
 *
 * Business Intent: Standard response for bulk admin operations like
 * bulkCancelTickets and bulkApproveRefunds. Provides counts for
 * processed and failed items along with error details.
 */
public record BulkOperationResponse(
        boolean success,
        String message,
        int processedCount,
        int failedCount,
        List<String> errors
) {
    /**
     * Factory method for fully successful bulk operations.
     */
    public static BulkOperationResponse success(String message, int processedCount) {
        return new BulkOperationResponse(true, message, processedCount, 0, List.of());
    }

    /**
     * Factory method for partially successful bulk operations.
     */
    public static BulkOperationResponse partial(String message, int processedCount, int failedCount, List<String> errors) {
        return new BulkOperationResponse(
                failedCount == 0,
                message,
                processedCount,
                failedCount,
                errors
        );
    }

    /**
     * Factory method for failed bulk operations.
     */
    public static BulkOperationResponse error(String message, List<String> errors) {
        return new BulkOperationResponse(false, message, 0, errors.size(), errors);
    }
}
