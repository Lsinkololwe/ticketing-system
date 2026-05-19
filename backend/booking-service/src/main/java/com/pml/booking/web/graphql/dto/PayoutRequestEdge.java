package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.PayoutRequest;

/**
 * Edge wrapper for PayoutRequest in cursor-based pagination.
 */
public record PayoutRequestEdge(
        String cursor,
        PayoutRequest node
) {
    public static PayoutRequestEdge of(PayoutRequest payout) {
        return new PayoutRequestEdge(payout.getId(), payout);
    }
}
