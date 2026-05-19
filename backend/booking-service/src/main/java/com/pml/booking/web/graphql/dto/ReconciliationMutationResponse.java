package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.ReconciliationRun;

import java.util.List;
import java.util.Map;

/**
 * Standard mutation response for Reconciliation operations.
 *
 * <p>Provides consistent response structure for all Reconciliation mutations:
 * start, resolve item, complete, and fail operations.</p>
 *
 * @since 1.0.0
 */
public record ReconciliationMutationResponse(
    boolean success,
    String message,
    ReconciliationRun data,
    List<String> errors,
    Map<String, Object> metadata
) {
    /**
     * Factory method for successful operations.
     *
     * @param message Success message
     * @param run The reconciliation run
     * @return Success response
     */
    public static ReconciliationMutationResponse success(String message, ReconciliationRun run) {
        return new ReconciliationMutationResponse(true, message, run, List.of(), null);
    }

    /**
     * Factory method for failed operations.
     *
     * @param message Error message
     * @return Error response
     */
    public static ReconciliationMutationResponse error(String message) {
        return new ReconciliationMutationResponse(false, message, null, List.of(message), null);
    }

    /**
     * Factory method for failed operations with multiple errors.
     *
     * @param message Summary error message
     * @param errors List of detailed error messages
     * @return Error response
     */
    public static ReconciliationMutationResponse error(String message, List<String> errors) {
        return new ReconciliationMutationResponse(false, message, null, errors, null);
    }
}
