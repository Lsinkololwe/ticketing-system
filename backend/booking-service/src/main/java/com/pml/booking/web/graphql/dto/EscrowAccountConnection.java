package com.pml.booking.web.graphql.dto;

import java.util.List;

/**
 * Cursor-based pagination connection for Escrow Accounts.
 */
public record EscrowAccountConnection(
        List<EscrowAccountEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static EscrowAccountConnection empty() {
        return new EscrowAccountConnection(List.of(), PageInfo.empty(), 0);
    }
}
