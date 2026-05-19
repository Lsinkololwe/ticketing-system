package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.RefundRequest;

/**
 * Edge wrapper for RefundRequest in cursor-based pagination.
 */
public record RefundRequestEdge(
        String cursor,
        RefundRequest node
) {
    public static RefundRequestEdge of(RefundRequest refund) {
        return new RefundRequestEdge(refund.getId(), refund);
    }
}
