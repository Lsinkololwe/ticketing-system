package com.pml.booking.web.graphql.dto;

import java.util.List;

/**
 * Cursor-based pagination connection for Ticket Reservations.
 */
public record ReservationConnection(
        List<ReservationEdge> edges,
        PageInfo pageInfo,
        Integer totalCount
) {
    public static ReservationConnection empty() {
        return new ReservationConnection(List.of(), PageInfo.empty(), 0);
    }
}
