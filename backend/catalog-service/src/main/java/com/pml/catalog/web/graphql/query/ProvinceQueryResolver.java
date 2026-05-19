package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.dto.*;
import com.pml.catalog.domain.model.Province;
import com.pml.catalog.service.ProvinceService;
import com.pml.catalog.util.CursorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
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

    // ==========================================
    // Single Province Query
    // ==========================================

    @DgsQuery
    public Mono<Province> province(@InputArgument String id) {
        log.debug("GraphQL query: province(id={})", id);
        Objects.requireNonNull(id, "Province ID is required");
        return provinceService.findById(id);
    }

    // ==========================================
    // Cursor-based Pagination Queries (Mobile Infinite Scroll)
    // ==========================================

    @DgsQuery
    public Mono<ProvinceConnection> provincesCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: provincesCursorPagination");
        return buildCursorConnection(
                provinceService.findAllProvinces(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<ProvinceConnection> provincesByCountryCursorPagination(
            @InputArgument String country,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: provincesByCountryCursorPagination(country={})", country);
        Objects.requireNonNull(country, "Country is required");
        return buildCursorConnection(
                provinceService.findProvincesByCountry(country),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<ProvinceConnection> searchProvincesCursorPagination(
            @InputArgument String query,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: searchProvincesCursorPagination(query={})", query);
        Objects.requireNonNull(query, "Search query is required");
        return buildCursorConnection(
                provinceService.searchProvinces(query),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    // ==========================================
    // Offset-based Pagination Queries (Admin Tables)
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ProvinceOffsetPage> provincesOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: provincesOffsetPagination");
        return buildOffsetPage(
                provinceService.findAllProvinces(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "name", OffsetPaginationInput.SortDirection.ASC));
    }

    @DgsQuery
    public Mono<ProvinceOffsetPage> provincesByCountryOffsetPagination(
            @InputArgument String country,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: provincesByCountryOffsetPagination(country={})", country);
        Objects.requireNonNull(country, "Country is required");
        return buildOffsetPage(
                provinceService.findProvincesByCountry(country),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "name", OffsetPaginationInput.SortDirection.ASC));
    }

    @DgsQuery
    public Mono<ProvinceOffsetPage> searchProvincesOffsetPagination(
            @InputArgument String query,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: searchProvincesOffsetPagination(query={})", query);
        Objects.requireNonNull(query, "Search query is required");
        return buildOffsetPage(
                provinceService.searchProvinces(query),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "name", OffsetPaginationInput.SortDirection.ASC));
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * Build ProvinceOffsetPage from a Flux of provinces.
     */
    private Mono<ProvinceOffsetPage> buildOffsetPage(Flux<Province> provinceFlux, OffsetPaginationInput pagination) {
        int limit = pagination.getLimit();
        int offset = pagination.getOffset();

        return provinceFlux.collectList()
                .map(allProvinces -> {
                    int totalCount = allProvinces.size();
                    int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = pagination.page() > 0;

                    List<Province> paginatedProvinces = allProvinces.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    return new ProvinceOffsetPage(
                            paginatedProvinces,
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
     * Build ProvinceConnection from a Flux of provinces.
     */
    private Mono<ProvinceConnection> buildCursorConnection(Flux<Province> provinceFlux, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();

        return provinceFlux.collectList()
                .map(allProvinces -> {
                    int totalCount = allProvinces.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (pagination.getAfter() != null && !pagination.getAfter().isBlank()) {
                        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
                        for (int i = 0; i < allProvinces.size(); i++) {
                            if (allProvinces.get(i).getId().equals(afterId)) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of provinces
                    List<Province> pageProvinces = allProvinces.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageProvinces.isEmpty()) {
                        return ProvinceConnection.empty();
                    }

                    // Build edges
                    List<ProvinceEdge> edges = pageProvinces.stream()
                            .map(ProvinceEdge::from)
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

                    return ProvinceConnection.builder()
                            .edges(edges)
                            .pageInfo(pageInfo)
                            .build();
                });
    }
}
