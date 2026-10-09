package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.*;
import com.pml.catalog.domain.model.City;
import com.pml.catalog.service.CityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * GraphQL Query Resolver for City queries.
 * Supports both cursor-based pagination (mobile) and offset pagination (admin tables).
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class CityQueryResolver {

    private final CityService cityService;
    private final com.pml.catalog.service.ReferenceGeography referenceGeography;

    // ==========================================
    // Single City Query
    // ==========================================

    /**
     * Cities, optionally within one province, unpaged: a bounded public list of at most 200 rows.
     *
     * <p>The argument is optional so one field answers both "every city" and "the cities of this
     * province", which the paginated variants spell out separately.
     */
    @DgsQuery
    public Flux<City> cities(@InputArgument String provinceId) {
        log.debug("GraphQL query: cities(provinceId={})", provinceId);
        return referenceGeography.cities(provinceId);
    }

    @DgsQuery
    public Mono<City> city(@InputArgument String id) {
        log.debug("GraphQL query: city(id={})", id);
        Objects.requireNonNull(id, "City ID is required");
        return cityService.findById(id);
    }

    // ==========================================
    // Cursor-based Pagination Queries (Mobile Infinite Scroll)
    // ==========================================

    // ==========================================
    // Offset-based Pagination Queries (Admin Tables)
    // ==========================================

    // ==========================================
    // Special Queries
    // ==========================================

    @DgsQuery
    public Flux<City> citiesWithEvents() {
        log.debug("GraphQL query: citiesWithEvents");
        return referenceGeography.citiesWithEvents();
    }

    // ==========================================
    // Helper Methods
    // ==========================================

}
