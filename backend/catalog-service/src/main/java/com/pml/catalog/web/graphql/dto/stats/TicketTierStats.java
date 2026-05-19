package com.pml.catalog.web.graphql.dto.stats;

import java.math.BigDecimal;

/**
 * Statistics for a specific ticket tier.
 * Matches the GraphQL schema TicketTierStats type.
 */
public record TicketTierStats(
        String eventId,
        String tierId,
        String tierCode,
        String tierName,
        BigDecimal price,
        int totalQuantity,
        boolean isActive,
        int ticketsSold,
        int ticketsRefunded,
        BigDecimal grossRevenue,
        float salesPercentage
) {
}
