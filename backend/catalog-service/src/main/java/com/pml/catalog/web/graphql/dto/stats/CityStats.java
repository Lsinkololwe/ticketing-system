package com.pml.catalog.web.graphql.dto.stats;

import java.math.BigDecimal;

/**
 * Statistics for a city.
 * Matches the GraphQL schema CityStats type.
 */
public record CityStats(
        String cityId,
        String cityName,
        String country,
        int eventCount,
        int totalCapacity,
        BigDecimal averageTicketPrice
) {
}
