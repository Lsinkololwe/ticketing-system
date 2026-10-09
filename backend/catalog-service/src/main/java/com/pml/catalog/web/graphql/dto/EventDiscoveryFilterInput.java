package com.pml.catalog.web.graphql.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The {@code EventDiscoveryFilterInput} GraphQL type. Only categoryId, cityId, the date range, the
 * price range and searchQuery are discovery filters; the other fields are bound so that a client
 * sending one is told so, rather than having it silently ignored.
 */
public record EventDiscoveryFilterInput(
        String searchQuery,
        String categoryId,
        List<String> categoryIds,
        String cityId,
        String cityName,
        String country,
        String provinceId,
        Instant startDate,
        Instant endDate,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean isFreeEvent,
        Boolean hasAvailableTickets,
        Boolean isAccessible,
        Boolean isVirtual,
        Boolean isFeatured,
        String organizerId
) {
}
