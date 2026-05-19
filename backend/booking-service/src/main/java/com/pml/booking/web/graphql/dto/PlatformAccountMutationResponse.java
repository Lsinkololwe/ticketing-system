package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.PlatformAccount;

import java.util.List;
import java.util.Map;

/**
 * Standard mutation response for Platform Account operations.
 *
 * <p>Provides consistent response structure for all Platform Account mutations:
 * create, credit, and debit operations.</p>
 *
 * @since 1.0.0
 */
public record PlatformAccountMutationResponse(
    boolean success,
    String message,
    PlatformAccount data,
    List<String> errors,
    Map<String, Object> metadata
) {
    /**
     * Factory method for successful operations.
     *
     * @param message Success message
     * @param account The platform account
     * @return Success response
     */
    public static PlatformAccountMutationResponse success(String message, PlatformAccount account) {
        return new PlatformAccountMutationResponse(true, message, account, List.of(), null);
    }

    /**
     * Factory method for failed operations.
     *
     * @param message Error message
     * @return Error response
     */
    public static PlatformAccountMutationResponse error(String message) {
        return new PlatformAccountMutationResponse(false, message, null, List.of(message), null);
    }

    /**
     * Factory method for failed operations with multiple errors.
     *
     * @param message Summary error message
     * @param errors List of detailed error messages
     * @return Error response
     */
    public static PlatformAccountMutationResponse error(String message, List<String> errors) {
        return new PlatformAccountMutationResponse(false, message, null, errors, null);
    }
}
