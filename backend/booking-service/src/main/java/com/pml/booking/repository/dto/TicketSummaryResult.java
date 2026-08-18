package com.pml.booking.repository.dto;

import java.math.BigDecimal;

/**
 * DTO for ticket aggregation results.
 *
 * <p>One counter per resting state of ET-TKT-002 R7. {@code TRANSFERRED} has none
 * because no ticket rests there — §4 returns a transferred ticket to
 * {@code ISSUED} under its new owner.
 */
public record TicketSummaryResult(
        long totalTickets,
        long issuedTickets,
        long validatedTickets,
        long refundPendingTickets,
        long refundedTickets,
        long cancelledTickets,
        long expiredTickets,
        BigDecimal totalRevenue
) {
    public static TicketSummaryResult empty() {
        return new TicketSummaryResult(
                0L, 0L, 0L, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO
        );
    }
}
