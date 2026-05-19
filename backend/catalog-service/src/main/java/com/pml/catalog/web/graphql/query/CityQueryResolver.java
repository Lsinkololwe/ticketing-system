package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.dto.*;
import com.pml.catalog.domain.model.City;
import com.pml.catalog.service.CityService;
import com.pml.catalog.util.CursorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
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

    // ==========================================
    // Single City Query
    // ==========================================

    @DgsQuery
    public Mono<City> city(@InputArgument String id) {
        log.debug("GraphQL query: city(id={})", id);
        Objects.requireNonNull(id, "City ID is required");
        return cityService.findById(id);
    }

    // ==========================================
    // Cursor-based Pagination Queries (Mobile Infinite Scroll)
    // ==========================================

    @DgsQuery
    public Mono<CityConnection> citiesCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: citiesCursorPagination");
        return buildCursorConnection(
                cityService.findAllCities(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<CityConnection> citiesByProvinceCursorPagination(
            @InputArgument String provinceId,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: citiesByProvinceCursorPagination(provinceId={})", provinceId);
        Objects.requireNonNull(provinceId, "Province ID is required");
        return buildCursorConnection(
                cityService.findCitiesByProvince(provinceId),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<CityConnection> citiesByCountryCursorPagination(
            @InputArgument String country,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: citiesByCountryCursorPagination(country={})", country);
        Objects.requireNonNull(country, "Country is required");
        return buildCursorConnection(
                cityService.findCitiesByCountry(country),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<CityConnection> searchCitiesCursorPagination(
            @InputArgument String query,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: searchCitiesCursorPagination(query={})", query);
        Objects.requireNonNull(query, "Search query is required");
        return buildCursorConnection(
                cityService.searchCities(query),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    // ==========================================
    // Offset-based Pagination Queries (Admin Tables)
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<CityOffsetPage> citiesOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: citiesOffsetPagination");
        return buildOffsetPage(
                cityService.findAllCities(),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "name", OffsetPaginationInput.SortDirection.ASC));
    }

    @DgsQuery
    public Mono<CityOffsetPage> citiesByProvinceOffsetPagination(
            @InputArgument String provinceId,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: citiesByProvinceOffsetPagination(provinceId={})", provinceId);
        Objects.requireNonNull(provinceId, "Province ID is required");
        return buildOffsetPage(
                cityService.findCitiesByProvince(provinceId),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "name", OffsetPaginationInput.SortDirection.ASC));
    }

    @DgsQuery
    public Mono<CityOffsetPage> citiesByCountryOffsetPagination(
            @InputArgument String country,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: citiesByCountryOffsetPagination(country={})", country);
        Objects.requireNonNull(country, "Country is required");
        return buildOffsetPage(
                cityService.findCitiesByCountry(country),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "name", OffsetPaginationInput.SortDirection.ASC));
    }

    @DgsQuery
    public Mono<CityOffsetPage> searchCitiesOffsetPagination(
            @InputArgument String query,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: searchCitiesOffsetPagination(query={})", query);
        Objects.requireNonNull(query, "Search query is required");
        return buildOffsetPage(
                cityService.searchCities(query),
                pagination != null ? pagination : new OffsetPaginationInput(0, 20, "name", OffsetPaginationInput.SortDirection.ASC));
    }

    // ==========================================
    // Special Queries
    // ==========================================

    @DgsQuery
    public Flux<City> citiesWithEvents() {
        log.debug("GraphQL query: citiesWithEvents");
        return cityService.findCitiesWithEvents();
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * Build CityOffsetPage from a Flux of cities.
     */
    private Mono<CityOffsetPage> buildOffsetPage(Flux<City> cityFlux, OffsetPaginationInput pagination) {
        int limit = pagination.getLimit();
        int offset = pagination.getOffset();

        return cityFlux.collectList()
                .map(allCities -> {
                    int totalCount = allCities.size();
                    int totalPages = totalCount == 0 ? 0 : (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = pagination.page() > 0;

                    List<City> paginatedCities = allCities.stream()
                            .skip(offset)
                            .limit(limit)
                            .toList();

                    return new CityOffsetPage(
                            paginatedCities,
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
     * Build CityConnection from a Flux of cities.
     */
    private Mono<CityConnection> buildCursorConnection(Flux<City> cityFlux, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();

        return cityFlux.collectList()
                .map(allCities -> {
                    int totalCount = allCities.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (pagination.getAfter() != null && !pagination.getAfter().isBlank()) {
                        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
                        for (int i = 0; i < allCities.size(); i++) {
                            if (allCities.get(i).getId().equals(afterId)) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of cities
                    List<City> pageCities = allCities.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageCities.isEmpty()) {
                        return CityConnection.empty();
                    }

                    // Build edges
                    List<CityEdge> edges = pageCities.stream()
                            .map(CityEdge::from)
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

                    return CityConnection.builder()
                            .edges(edges)
                            .pageInfo(pageInfo)
                            .build();
                });
    }
}
