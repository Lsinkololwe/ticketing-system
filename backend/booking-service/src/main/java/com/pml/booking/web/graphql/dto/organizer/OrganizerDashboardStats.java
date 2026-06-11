package com.pml.booking.web.graphql.dto.organizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Organizer dashboard statistics for the main dashboard view.
 *
 * Aggregates key metrics for an organizer's business overview:
 * - Revenue metrics (total, period change)
 * - Ticket sales (total sold, period change)
 * - Event counts (active events, ending this week)
 * - Attendee metrics (total checked in, period change)
 * - Payout status (pending, available balance)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizerDashboardStats {

    /**
     * Total revenue earned from all ticket sales
     */
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    /**
     * Percentage change in revenue compared to previous period
     */
    private Float revenueChange;

    /**
     * Currency for revenue amounts (default: ZMW)
     */
    @Builder.Default
    private String revenueCurrency = "ZMW";

    /**
     * Total number of tickets sold across all events
     */
    @Builder.Default
    private Integer totalTicketsSold = 0;

    /**
     * Percentage change in tickets sold compared to previous period
     */
    private Float ticketsSoldChange;

    /**
     * Number of currently active (published) events
     */
    @Builder.Default
    private Integer activeEvents = 0;

    /**
     * Change in event count from previous period
     */
    private Float eventsChange;

    /**
     * Number of events ending within the next 7 days
     */
    @Builder.Default
    private Integer eventsEndingThisWeek = 0;

    /**
     * Total number of attendees who have checked in across all events
     */
    @Builder.Default
    private Integer totalAttendees = 0;

    /**
     * Percentage change in attendee count compared to previous period
     */
    private Float attendeesChange;

    /**
     * Total amount in pending payout requests
     */
    @Builder.Default
    private BigDecimal pendingPayouts = BigDecimal.ZERO;

    /**
     * Available balance that can be withdrawn
     */
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    /**
     * Start of the statistics period
     */
    private LocalDateTime periodStart;

    /**
     * End of the statistics period
     */
    private LocalDateTime periodEnd;

    /**
     * Creates empty stats for organizers with no data
     */
    public static OrganizerDashboardStats empty() {
        return OrganizerDashboardStats.builder()
                .totalRevenue(BigDecimal.ZERO)
                .totalTicketsSold(0)
                .activeEvents(0)
                .eventsEndingThisWeek(0)
                .totalAttendees(0)
                .pendingPayouts(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .revenueCurrency("ZMW")
                .build();
    }
}
