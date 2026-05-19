package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.PaymentAttempt;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Mutation response type for PaymentAttempt operations.
 *
 * @param success Whether the operation succeeded
 * @param message Human-readable result message
 * @param data The PaymentAttempt data (if successful)
 * @param errors List of error messages
 * @param metadata Additional metadata
 *
 * @since 1.0.0
 */
public record PaymentAttemptMutationResponse(
        boolean success,
        String message,
        PaymentAttempt data,
        List<String> errors,
        Map<String, Object> metadata
) {

    /**
     * Creates a success response with the payment attempt data.
     *
     * @param message Success message
     * @param paymentAttempt The payment attempt data
     * @return Success response
     */
    public static PaymentAttemptMutationResponse success(String message, PaymentAttempt paymentAttempt) {
        return new PaymentAttemptMutationResponse(
                true,
                message,
                paymentAttempt,
                Collections.emptyList(),
                null
        );
    }

    /**
     * Creates a success response with metadata.
     *
     * @param message Success message
     * @param paymentAttempt The payment attempt data
     * @param metadata Additional metadata
     * @return Success response with metadata
     */
    public static PaymentAttemptMutationResponse success(
            String message,
            PaymentAttempt paymentAttempt,
            Map<String, Object> metadata
    ) {
        return new PaymentAttemptMutationResponse(
                true,
                message,
                paymentAttempt,
                Collections.emptyList(),
                metadata
        );
    }

    /**
     * Creates an error response with a single error message.
     *
     * @param errorMessage The error message
     * @return Error response
     */
    public static PaymentAttemptMutationResponse error(String errorMessage) {
        return new PaymentAttemptMutationResponse(
                false,
                errorMessage,
                null,
                List.of(errorMessage),
                null
        );
    }

    /**
     * Creates an error response with multiple error messages.
     *
     * @param errors List of error messages
     * @return Error response
     */
    public static PaymentAttemptMutationResponse errors(List<String> errors) {
        return new PaymentAttemptMutationResponse(
                false,
                errors.isEmpty() ? "An error occurred" : errors.get(0),
                null,
                errors,
                null
        );
    }

    /**
     * Creates an error response with metadata (for debugging).
     *
     * @param errorMessage The error message
     * @param metadata Additional metadata about the error
     * @return Error response with metadata
     */
    public static PaymentAttemptMutationResponse error(String errorMessage, Map<String, Object> metadata) {
        return new PaymentAttemptMutationResponse(
                false,
                errorMessage,
                null,
                List.of(errorMessage),
                metadata
        );
    }
}
