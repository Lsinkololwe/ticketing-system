package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.TicketReservation;

import java.util.List;

/**
 * Offset-based pagination result for ticket reservations.
 * Used for admin tables and dashboards.
 */
public record ReservationOffsetPage(
        List<TicketReservation> data,
        PaginationInfo pagination
) {
    public static ReservationOffsetPage empty() {
        return new ReservationOffsetPage(
                List.of(),
                new PaginationInfo(0, 20, 1, 0, false, false)
        );
    }
}
