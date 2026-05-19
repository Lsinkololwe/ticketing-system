package com.pml.catalog.service;

import com.pml.catalog.dto.CursorPaginationInput;
import com.pml.catalog.dto.LocationConnection;
import com.pml.catalog.domain.model.Location;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Location Service interface.
 *
 * Locations are simple address data for events, not managed entities.
 * This service provides lookup and pagination for displaying location info.
 */
public interface LocationService {

    // ==========================================
    // Single Location Operations
    // ==========================================

    Mono<Location> findById(String id);

    // ==========================================
    // Flux-based Queries (for pagination helper methods)
    // ==========================================

    Flux<Location> findAllLocations();

    Flux<Location> findLocationsByCity(String city);

    Flux<Location> findLocationsByCountry(String country);

    Flux<Location> searchLocations(String query);

    Flux<Location> findNearbyLocations(Double latitude, Double longitude, Double radiusKm);

    // ==========================================
    // Cursor-based Pagination (for mobile infinite scroll)
    // ==========================================

    Mono<LocationConnection> findLocationsCursor(CursorPaginationInput pagination);

    Mono<LocationConnection> findLocationsByCityCursor(String city, CursorPaginationInput pagination);

    Mono<LocationConnection> findLocationsByCountryCursor(String country, CursorPaginationInput pagination);

    Mono<LocationConnection> searchLocationsCursor(String query, CursorPaginationInput pagination);
}
