package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.dto.*;
import com.pml.catalog.domain.model.Location;
import com.pml.catalog.service.LocationService;
import com.pml.catalog.util.CursorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Location queries.
 *
 * Locations are simple address data for events, not managed entities.
 * Provides lookup and cursor-based pagination for mobile infinite scroll.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class LocationQueryResolver {

    private final LocationService locationService;

    // ==========================================
    // Single Location Query
    // ==========================================

    @DgsQuery
    public Mono<Location> location(@InputArgument String id) {
        log.debug("GraphQL query: location(id={})", id);
        Objects.requireNonNull(id, "Location ID is required");
        return locationService.findById(id);
    }

    // ==========================================
    // Cursor-based Pagination Queries (Mobile Infinite Scroll)
    // ==========================================

    @DgsQuery
    public Mono<LocationConnection> locationsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: locationsCursorPagination");
        return buildCursorConnection(
                locationService.findAllLocations(),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<LocationConnection> locationsByCityCursorPagination(
            @InputArgument String city,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: locationsByCityCursorPagination(city={})", city);
        Objects.requireNonNull(city, "City is required");
        return buildCursorConnection(
                locationService.findLocationsByCity(city),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<LocationConnection> locationsByCountryCursorPagination(
            @InputArgument String country,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: locationsByCountryCursorPagination(country={})", country);
        Objects.requireNonNull(country, "Country is required");
        return buildCursorConnection(
                locationService.findLocationsByCountry(country),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<LocationConnection> searchLocationsCursorPagination(
            @InputArgument String query,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: searchLocationsCursorPagination(query={})", query);
        Objects.requireNonNull(query, "Search query is required");
        return buildCursorConnection(
                locationService.searchLocations(query),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    @DgsQuery
    public Mono<LocationConnection> locationsNearbyCursorPagination(
            @InputArgument NearbyLocationInput input,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: locationsNearbyCursorPagination");
        Objects.requireNonNull(input, "Nearby location input is required");
        return buildCursorConnection(
                locationService.findNearbyLocations(input.latitude(), input.longitude(), input.radiusKm()),
                pagination != null ? pagination : new CursorPaginationInput());
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * Build LocationConnection from a Flux of locations.
     */
    private Mono<LocationConnection> buildCursorConnection(Flux<Location> locationFlux, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();

        return locationFlux.collectList()
                .map(allLocations -> {
                    int totalCount = allLocations.size();

                    // Find starting position based on cursor
                    int startIndex = 0;
                    if (pagination.getAfter() != null && !pagination.getAfter().isBlank()) {
                        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
                        for (int i = 0; i < allLocations.size(); i++) {
                            if (allLocations.get(i).getId().equals(afterId)) {
                                startIndex = i + 1;
                                break;
                            }
                        }
                    }

                    // Get the page of locations
                    List<Location> pageLocations = allLocations.stream()
                            .skip(startIndex)
                            .limit(limit)
                            .toList();

                    if (pageLocations.isEmpty()) {
                        return LocationConnection.empty();
                    }

                    // Build edges
                    List<LocationEdge> edges = pageLocations.stream()
                            .map(LocationEdge::from)
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

                    return LocationConnection.builder()
                            .edges(edges)
                            .pageInfo(pageInfo)
                            .build();
                });
    }
}
