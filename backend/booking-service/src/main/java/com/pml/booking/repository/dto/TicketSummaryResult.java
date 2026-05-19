package com.pml.booking.repository.dto;

import java.math.BigDecimal;

/**
 * DTO for ticket aggregation results.
 */
public record TicketSummaryResult(
        long totalTickets,
        long pendingPaymentTickets,
        long purchasedTickets,
        long confirmedTickets,
        long validatedTickets,
        long usedTickets,
        long expiredTickets,
        long cancelledTickets,
        long refundedTickets,
        BigDecimal totalRevenue
) {
    public static TicketSummaryResult empty() {
        return new TicketSummaryResult(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO
        );
    }
}
