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
    // One counter per resting state of ET-TKT-002 R7. TRANSFERRED has none:
    // §4 returns a transferred ticket to ISSUED under its new owner.
    private int issuedTickets;
    private int validatedTickets;
    private int refundPendingTickets;
    private int refundedTickets;
    private int cancelledTickets;
    private int expiredTickets;
    private List<TicketStatusStats> ticketsByStatus;
    private List<TicketCategoryStats> ticketsByCategory;
    private List<Ticket> recentTickets;
}
