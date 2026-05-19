package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.TicketReservation;

/**
 * Edge wrapper for TicketReservation in cursor-based pagination.
 */
public record ReservationEdge(
        String cursor,
        TicketReservation node
) {
    public static ReservationEdge of(TicketReservation reservation) {
        return new ReservationEdge(reservation.getId(), reservation);
    }
}
