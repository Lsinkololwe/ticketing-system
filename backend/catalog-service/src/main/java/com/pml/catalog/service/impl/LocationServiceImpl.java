package com.pml.catalog.service.impl;

import com.pml.catalog.dto.*;
import com.pml.catalog.domain.model.Location;
import com.pml.catalog.repository.LocationRepository;
import com.pml.catalog.service.LocationService;
import com.pml.catalog.util.CursorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Location Service Implementation.
 *
 * Locations are simple address data for events, not managed entities.
 * This service provides lookup and cursor-based pagination for displaying location info.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final LocationRepository locationRepository;

    // ==========================================
    // Single Location Operations
    // ==========================================

    @Override
    public Mono<Location> findById(String id) {
        return locationRepository.findById(id);
    }

    // ==========================================
    // Flux-based Queries (for pagination helper methods)
    // ==========================================

    @Override
    public Flux<Location> findAllLocations() {
        return locationRepository.findAll();
    }

    @Override
    public Flux<Location> findLocationsByCity(String city) {
        return locationRepository.findByCityFirstPage(city, PageRequest.of(0, Integer.MAX_VALUE));
    }

    @Override
    public Flux<Location> findLocationsByCountry(String country) {
        return locationRepository.findByCountryFirstPage(country, PageRequest.of(0, Integer.MAX_VALUE));
    }

    @Override
    public Flux<Location> searchLocations(String query) {
        return locationRepository.searchLocationsFirstPage(query, PageRequest.of(0, Integer.MAX_VALUE));
    }

    @Override
    public Flux<Location> findNearbyLocations(Double latitude, Double longitude, Double radiusKm) {
        // For now, return all locations. In a real implementation, this would use
        // MongoDB's geospatial queries with $near or $geoWithin operators.
        log.debug("Finding locations near ({}, {}) within {} km", latitude, longitude, radiusKm);
        return locationRepository.findAll();
    }

    // ==========================================
    // Cursor-based Pagination (for mobile infinite scroll)
    // ==========================================

    @Override
    public Mono<LocationConnection> findLocationsCursor(CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Location> locationsFlux;
        if (afterId != null) {
            locationsFlux = locationRepository.findLocationsAfterCursor(afterId, pageable);
        } else {
            locationsFlux = locationRepository.findByIsActiveTrueOrderByIdAsc(pageable);
        }

        return buildConnection(locationsFlux, limit, afterId != null);
    }

    @Override
    public Mono<LocationConnection> findLocationsByCityCursor(String city, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Location> locationsFlux;
        if (afterId != null) {
            locationsFlux = locationRepository.findByCityAfterCursor(city, afterId, pageable);
        } else {
            locationsFlux = locationRepository.findByCityFirstPage(city, pageable);
        }

        return buildConnection(locationsFlux, limit, afterId != null);
    }

    @Override
    public Mono<LocationConnection> findLocationsByCountryCursor(String country, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Location> locationsFlux;
        if (afterId != null) {
            locationsFlux = locationRepository.findByCountryAfterCursor(country, afterId, pageable);
        } else {
            locationsFlux = locationRepository.findByCountryFirstPage(country, pageable);
        }

        return buildConnection(locationsFlux, limit, afterId != null);
    }

    @Override
    public Mono<LocationConnection> searchLocationsCursor(String query, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Location> locationsFlux;
        if (afterId != null) {
            locationsFlux = locationRepository.searchLocationsAfterCursor(query, afterId, pageable);
        } else {
            locationsFlux = locationRepository.searchLocationsFirstPage(query, pageable);
        }

        return buildConnection(locationsFlux, limit, afterId != null);
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private Mono<LocationConnection> buildConnection(Flux<Location> locationsFlux, int limit, boolean hasPreviousPage) {
        return locationsFlux.collectList().map(locations -> {
            boolean hasNextPage = locations.size() > limit;

            List<Location> pageLocations = hasNextPage
                    ? locations.subList(0, limit)
                    : locations;

            List<LocationEdge> edges = pageLocations.stream()
                    .map(LocationEdge::from)
                    .toList();

            PageInfo pageInfo = PageInfo.builder()
                    .hasNextPage(hasNextPage)
                    .hasPreviousPage(hasPreviousPage)
                    .startCursor(edges.isEmpty() ? null : edges.get(0).getCursor())
                    .endCursor(edges.isEmpty() ? null : edges.get(edges.size() - 1).getCursor())
                    .build();

            return LocationConnection.builder()
                    .edges(edges)
                    .pageInfo(pageInfo)
                    .build();
        });
    }
}
