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
        int processedCount,
        int failedCount
) {
    /**
     * Factory method for fully successful bulk operations.
     */
    public static BulkOperationResponse success(String message, int processedCount) {
        return new BulkOperationResponse(processedCount, 0);
    }

    /**
     * Factory method for partially successful bulk operations.
     */
    public static BulkOperationResponse partial(String message, int processedCount, int failedCount, List<String> errors) {
        return new BulkOperationResponse(
                processedCount,
                failedCount
        );
    }

    /**
     * Factory method for failed bulk operations.
     */
    public static BulkOperationResponse error(String message, List<String> errors) {
        return new BulkOperationResponse(0, errors.size());
    }
}
