package com.pml.booking.web.graphql.dto;

import java.util.List;
import java.util.Map;

/**
 * Generic Delete Mutation Response DTO
 *
 * Business Intent: Standard response for delete operations.
 * Used by deletePromoCode, deleteBankAccount, etc.
 */
public record DeleteMutationResponse(
        boolean success,
        String message,
        List<String> errors,
        Map<String, Object> metadata
) {
    /**
     * Factory method for successful deletions.
     */
    public static DeleteMutationResponse success(String message) {
        return new DeleteMutationResponse(true, message, List.of(), null);
    }

    /**
     * Factory method for successful deletions with metadata.
     */
    public static DeleteMutationResponse success(String message, Map<String, Object> metadata) {
        return new DeleteMutationResponse(true, message, List.of(), metadata);
    }

    /**
     * Factory method for failed deletions.
     */
    public static DeleteMutationResponse error(String message) {
        return new DeleteMutationResponse(false, message, List.of(message), null);
    }

    /**
     * Factory method for failed deletions with multiple errors.
     */
    public static DeleteMutationResponse error(String message, List<String> errors) {
        return new DeleteMutationResponse(false, message, errors, null);
    }
}
