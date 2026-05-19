package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.RefundRequest;

import java.util.List;
import java.util.Map;

/**
 * Generic Refund Request Mutation Response DTO
 *
 * Business Intent: Standard response for admin refund operations.
 * Used by createAdminRefundRequest, cancelRefundRequest, etc.
 */
public record RefundRequestMutationResponse(
        boolean success,
        String message,
        RefundRequest data,
        List<String> errors,
        Map<String, Object> metadata
) {
    /**
     * Factory method for successful operations.
     */
    public static RefundRequestMutationResponse success(String message, RefundRequest refundRequest) {
        return new RefundRequestMutationResponse(true, message, refundRequest, List.of(), null);
    }

    /**
     * Factory method for failed operations.
     */
    public static RefundRequestMutationResponse error(String message) {
        return new RefundRequestMutationResponse(false, message, null, List.of(message), null);
    }

    /**
     * Factory method for failed operations with multiple errors.
     */
    public static RefundRequestMutationResponse error(String message, List<String> errors) {
        return new RefundRequestMutationResponse(false, message, null, errors, null);
    }
}
