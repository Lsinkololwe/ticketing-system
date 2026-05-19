package com.pml.booking.web.graphql.dto.stats;

import com.pml.booking.domain.model.Ticket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Main container for ticket statistics.
 * Matches the TicketStats GraphQL type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStats {
    private int totalTickets;
    private int purchasedTickets;
    private int validatedTickets;
    private int usedTickets;
    private int refundedTickets;
    private int expiredTickets;
    private int cancelledTickets;
    private int pendingPaymentTickets;
    private List<TicketStatusStats> ticketsByStatus;
    private List<TicketCategoryStats> ticketsByCategory;
    private List<Ticket> recentTickets;
}
