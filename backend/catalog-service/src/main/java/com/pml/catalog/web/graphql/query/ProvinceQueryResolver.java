package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.*;
import com.pml.catalog.domain.model.Province;
import com.pml.catalog.service.ProvinceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * GraphQL Query Resolver for Province queries.
 * Supports both cursor-based pagination (mobile) and offset pagination (admin tables).
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ProvinceQueryResolver {

    private final ProvinceService provinceService;
    private final com.pml.catalog.service.ReferenceGeography referenceGeography;

    // ==========================================
    // Single Province Query
    // ==========================================

    /**
     * Every province, unpaged: a bounded public list of at most 10 rows.
     *
     * <p>Zambia has ten provinces. `provincesOffsetPagination`, `provincesCursorPagination` and
     * their `by-country` and `search` variants page a set that fits in one screen; a client that
     * wants the provinces asks for this list instead. Pagination over ten rows is never observably
     * wrong, so the paged fields are easily mistaken for this operation.
     */
    @DgsQuery
    public Flux<Province> provinces() {
        log.debug("GraphQL query: provinces()");
        return referenceGeography.provinces();
    }

    @DgsQuery
    public Mono<Province> province(@InputArgument String id) {
        log.debug("GraphQL query: province(id={})", id);
        Objects.requireNonNull(id, "Province ID is required");
        return provinceService.findById(id);
    }

    // ==========================================
    // Cursor-based Pagination Queries (Mobile Infinite Scroll)
    // ==========================================

    // ==========================================
    // Offset-based Pagination Queries (Admin Tables)
    // ==========================================

    // ==========================================
    // Helper Methods
    // ==========================================

}
