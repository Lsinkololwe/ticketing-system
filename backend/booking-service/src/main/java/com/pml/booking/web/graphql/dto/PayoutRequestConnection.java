package com.pml.booking.web.graphql.dto;

import java.util.List;

/**
 * Cursor-based pagination connection for Payout Requests.
 */
public record PayoutRequestConnection(
        List<PayoutRequestEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static PayoutRequestConnection empty() {
        return new PayoutRequestConnection(List.of(), PageInfo.empty(), 0);
    }
}
