package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.Ticket;

/**
 * Edge wrapper for Ticket in cursor-based pagination.
 */
public record TicketEdge(
        String cursor,
        Ticket node
) {
    public static TicketEdge of(Ticket ticket) {
        return new TicketEdge(ticket.getId(), ticket);
    }
}
