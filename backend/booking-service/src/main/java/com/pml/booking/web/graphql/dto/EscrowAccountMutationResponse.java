package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.EventEscrowAccount;

import java.util.List;
import java.util.Map;

/**
 * Standard mutation response for escrow account operations.
 */
public record EscrowAccountMutationResponse(
    boolean success,
    String message,
    EventEscrowAccount data,
    List<String> errors,
    Map<String, Object> metadata
) {
    /**
     * Factory method for successful operations.
     */
    public static EscrowAccountMutationResponse success(String message, EventEscrowAccount account) {
        return new EscrowAccountMutationResponse(true, message, account, List.of(), null);
    }

    /**
     * Factory method for failed operations.
     */
    public static EscrowAccountMutationResponse error(String message) {
        return new EscrowAccountMutationResponse(false, message, null, List.of(message), null);
    }

    /**
     * Factory method for failed operations with multiple errors.
     */
    public static EscrowAccountMutationResponse error(String message, List<String> errors) {
        return new EscrowAccountMutationResponse(false, message, null, errors, null);
    }
}
