package com.pml.booking.web.graphql.dto;

import java.util.List;

/**
 * Cursor-based pagination connection for Refund Requests.
 */
public record RefundRequestConnection(
        List<RefundRequestEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static RefundRequestConnection empty() {
        return new RefundRequestConnection(List.of(), PageInfo.empty(), 0);
    }
}
