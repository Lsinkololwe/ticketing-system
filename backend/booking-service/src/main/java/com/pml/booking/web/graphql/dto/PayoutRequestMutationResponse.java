package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.PayoutRequest;

import java.util.List;
import java.util.Map;

/**
 * Generic mutation response for payout request operations.
 * Matches the PayoutRequestMutationResponse GraphQL type.
 */
public record PayoutRequestMutationResponse(
    boolean success,
    String message,
    PayoutRequest data,
    List<String> errors,
    Map<String, Object> metadata
) {
    public static PayoutRequestMutationResponse success(PayoutRequest data, String message) {
        return new PayoutRequestMutationResponse(true, message, data, List.of(), null);
    }

    public static PayoutRequestMutationResponse error(String message, List<String> errors) {
        return new PayoutRequestMutationResponse(false, message, null, errors, null);
    }

    public static PayoutRequestMutationResponse error(String message) {
        return new PayoutRequestMutationResponse(false, message, null, List.of(message), null);
    }
}
