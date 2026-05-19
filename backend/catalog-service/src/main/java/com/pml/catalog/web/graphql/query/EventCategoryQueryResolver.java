package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.dto.*;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.service.EventCategoryService;
import com.pml.catalog.util.CursorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
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

    // ==========================================
    // Single EventCategory Query
    // ==========================================

    @DgsQuery
    public Mono<EventCategory> eventCategory(@InputArgument String id) {
        log.debug("GraphQL query: eventCategory(id={})", id);
        Objects.requireNonNull(id, "Event Category ID is required");
        return eventCategoryService.findById(id);
    }

    // ==========================================
    // Cursor-based Pagination Queries (Mobile Infinite Scroll)
    // ==========================================

    @DgsQuery
    public Mono<EventCategoryConnection> eventCategoriesCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: eventCategoriesCursorPagination");
        return buildCursorConnection(
                eventCategoryService.findAllCategories(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventCategoryConnection> activeEventCategoriesCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: activeEventCategoriesCursorPagination");
        return buildCursorConnection(
                eventCategoryService.findActiveCategories(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<EventCategoryConnection> searchEventCategoriesCursorPagination(
            @InputArgument String query,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: searchEventCategoriesCursorPagination(query={})", query);
        Objects.requireNonNull(query, "Search query is required");
        return buildCursorConnection(
                eventCategoryService.searchCategories(query),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    // ==========================================
    // Offset-based Pagination Queries (Admin Tables)
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventCategoryOffsetPage> eventCategoriesOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: eventCategoriesOffsetPagination");
        return buildOffsetPage(
                eventCategoryService.findAllCategories(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "name", OffsetPaginationInput.SortDirection.ASC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventCategoryOffsetPage> activeEventCategoriesOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: activeEventCategoriesOffsetPagination");
        return buildOffsetPage(
                eventCategoryService.findActiveCategories(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "name", OffsetPaginationInput.SortDirection.ASC));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventCategoryOffsetPage> searchEventCategoriesOffsetPagination(
            @InputArgument String query,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: searchEventCategoriesOffsetPagination(query={})", query);
        Objects.requireNonNull(query, "Search query is required");
        return buildOffsetPage(
                eventCategoryService.searchCategories(query),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "name", OffsetPaginationInput.SortDirection.ASC));
    }

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

    /**
     * Build EventCategoryOffsetPage from a Flux of categories.
     */
    private Mono<EventCategoryOffsetPage> buildOffsetPage(Flux<EventCategory> categoryFlux, OffsetPaginationInput pagination) {
        int limit = pagination.getLimit();
        int offset = pagination.getOffset();

        return categoryFlux.collectList()
                .map(allCategories -> {
                    int totalCount = allCategories.size();
                    int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = pagination.page() > 0;

                    List<EventCategory> paginatedCategories = allCategories.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    return new EventCategoryOffsetPage(
                            paginatedCategories,
                            pagination.page(),
                            limit,
                            totalCount,
                            totalPages,
                            hasNextPage,
                            hasPreviousPage
                    );
                });
    }

    /**
     * Build EventCategoryConnection from a Flux of categories.
     */
    private Mono<EventCategoryConnection> buildCursorConnection(Flux<EventCategory> categoryFlux, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();

        return categoryFlux.collectList()
                .map(allCategories -> {
                    int totalCount = allCategories.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (pagination.getAfter() != null && !pagination.getAfter().isBlank()) {
                        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
                        for (int i = 0; i < allCategories.size(); i++) {
                            if (allCategories.get(i).getId().equals(afterId)) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of categories
                    List<EventCategory> pageCategories = allCategories.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageCategories.isEmpty()) {
                        return EventCategoryConnection.empty();
                    }

                    // Build edges
                    List<EventCategoryEdge> edges = pageCategories.stream()
                            .map(EventCategoryEdge::from)
                            .toList();

                    // Build page info
                    boolean hasNextPage = (startIndex + limit) < totalCount;
                    boolean hasPreviousPage = startIndex > 0;
                    String startCursor = edges.get(0).getCursor();
                    String endCursor = edges.get(edges.size() - 1).getCursor();

                    PageInfo pageInfo = PageInfo.builder()
                            .hasNextPage(hasNextPage)
                            .hasPreviousPage(hasPreviousPage)
                            .startCursor(startCursor)
                            .endCursor(endCursor)
                            .build();

                    return EventCategoryConnection.builder()
                            .edges(edges)
                            .pageInfo(pageInfo)
                            .build();
                });
    }
}
