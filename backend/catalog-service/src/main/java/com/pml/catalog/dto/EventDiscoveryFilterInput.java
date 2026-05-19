package com.pml.catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Input for event discovery queries with comprehensive filtering.
 * Matches the GraphQL schema EventDiscoveryFilterInput type.
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
        Boolean isFree,
        Boolean hasAvailableTickets,
        Boolean isAccessible,
        Boolean isVirtual
) {
}
