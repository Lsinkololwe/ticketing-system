package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.Location;
import com.pml.catalog.repository.LocationRepository;
import com.pml.catalog.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
}
