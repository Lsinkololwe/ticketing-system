package com.pml.booking.web.graphql.dto;

import java.util.List;

/**
 * Cursor-based pagination connection for Tickets.
 * Follows Relay Connection Specification.
 */
public record TicketConnection(
        List<TicketEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static TicketConnection empty() {
        return new TicketConnection(List.of(), PageInfo.empty(), 0);
    }
}
