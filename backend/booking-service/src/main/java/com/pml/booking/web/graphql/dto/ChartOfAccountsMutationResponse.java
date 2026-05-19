package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.ChartOfAccountsEntry;

import java.util.List;
import java.util.Map;

/**
 * Standard mutation response for Chart of Accounts operations.
 *
 * <p>Provides consistent response structure for all Chart of Accounts mutations:
 * create, update, and deactivate operations.</p>
 *
 * @since 1.0.0
 */
public record ChartOfAccountsMutationResponse(
    boolean success,
    String message,
    ChartOfAccountsEntry data,
    List<String> errors,
    Map<String, Object> metadata
) {
    /**
     * Factory method for successful operations.
     *
     * @param message Success message
     * @param entry The created/updated Chart of Accounts entry
     * @return Success response
     */
    public static ChartOfAccountsMutationResponse success(String message, ChartOfAccountsEntry entry) {
        return new ChartOfAccountsMutationResponse(true, message, entry, List.of(), null);
    }

    /**
     * Factory method for failed operations.
     *
     * @param message Error message
     * @return Error response
     */
    public static ChartOfAccountsMutationResponse error(String message) {
        return new ChartOfAccountsMutationResponse(false, message, null, List.of(message), null);
    }

    /**
     * Factory method for failed operations with multiple errors.
     *
     * @param message Summary error message
     * @param errors List of detailed error messages
     * @return Error response
     */
    public static ChartOfAccountsMutationResponse error(String message, List<String> errors) {
        return new ChartOfAccountsMutationResponse(false, message, null, errors, null);
    }
}
