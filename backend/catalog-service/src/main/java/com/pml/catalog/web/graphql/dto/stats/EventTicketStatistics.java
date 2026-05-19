package com.pml.catalog.web.graphql.dto.stats;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Statistics for an event's ticket sales.
 * Matches the GraphQL schema EventTicketStatistics type.
 */
public record EventTicketStatistics(
        String eventId,
        String eventTitle,
        Instant eventDate,
        int totalTicketsAvailable,
        int totalTicketsSold,
        int totalTicketsRefunded,
        BigDecimal totalCommissionEarned,
        BigDecimal totalGrossRevenue,
        List<TicketTierStats> tierStatistics,
        float overallSalesPercentage,
        String bestSellingTier
) {
}
