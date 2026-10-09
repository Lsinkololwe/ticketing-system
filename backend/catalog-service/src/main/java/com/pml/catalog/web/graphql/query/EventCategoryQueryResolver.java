package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.*;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.service.EventCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

/**
 * GraphQL Query Resolver for EventCategory queries.
 * Supports both cursor-based pagination (mobile) and offset pagination (admin tables).
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventCategoryQueryResolver {

    private final EventCategoryService eventCategoryService;
    private final com.pml.catalog.service.ReferenceGeography referenceGeography;

    // ==========================================
    // Single EventCategory Query
    // ==========================================

    /**
     * Active event categories, unpaged: a bounded public list of at most 15 rows.
     *
     * <p>The operation is named `categories` while the type it returns is `EventCategory`, the
     * schema's existing type name.
     * Active-only, because a category nobody may pick is not part of the public taxonomy.
     */
    @DgsQuery
    public Flux<EventCategory> categories() {
        log.debug("GraphQL query: categories()");
        return referenceGeography.categories();
    }

    @DgsQuery
    public Mono<EventCategory> eventCategory(@InputArgument String id) {
        log.debug("GraphQL query: eventCategory(id={})", id);
        Objects.requireNonNull(id, "Event Category ID is required");
        return eventCategoryService.findById(id);
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
    public Flux<EventCategory> popularCategories(@InputArgument Integer limit) {
        log.debug("GraphQL query: popularCategories(limit={})", limit);
        int effectiveLimit = limit != null ? limit : 10;
        return eventCategoryService.findPopularCategories(effectiveLimit);
    }

    // ==========================================
    // Helper Methods
    // ==========================================

}
