package com.pml.catalog.repository;

import com.pml.catalog.domain.model.Location;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Location Repository with cursor-based pagination support.
 *
 * Locations are simple address data for events, not managed entities.
 * This repository provides lookup and cursor pagination for mobile infinite scroll.
 */
@Repository
public interface LocationRepository extends ReactiveMongoRepository<Location, String> {

    // ==========================================
    // Cursor-based pagination (for mobile infinite scroll)
    // ==========================================

    // First page queries
    Flux<Location> findByIsActiveTrueOrderByIdAsc(Pageable pageable);

    @Query("{ 'cityName': { '$regex': ?0, '$options': 'i' }, 'isActive': true }")
    Flux<Location> findByCityFirstPage(String city, Pageable pageable);

    @Query("{ 'country': { '$regex': ?0, '$options': 'i' }, 'isActive': true }")
    Flux<Location> findByCountryFirstPage(String country, Pageable pageable);

    @Query("{ 'isActive': true, '$or': [ { 'name': { '$regex': ?0, '$options': 'i' } }, { 'address': { '$regex': ?0, '$options': 'i' } }, { 'cityName': { '$regex': ?0, '$options': 'i' } } ] }")
    Flux<Location> searchLocationsFirstPage(String query, Pageable pageable);

    // After cursor queries
    @Query("{ 'isActive': true, '_id': { '$gt': ?0 } }")
    Flux<Location> findLocationsAfterCursor(String afterId, Pageable pageable);

    @Query("{ 'cityName': { '$regex': ?0, '$options': 'i' }, 'isActive': true, '_id': { '$gt': ?1 } }")
    Flux<Location> findByCityAfterCursor(String city, String afterId, Pageable pageable);

    @Query("{ 'country': { '$regex': ?0, '$options': 'i' }, 'isActive': true, '_id': { '$gt': ?1 } }")
    Flux<Location> findByCountryAfterCursor(String country, String afterId, Pageable pageable);

    @Query("{ 'isActive': true, '_id': { '$gt': ?1 }, '$or': [ { 'name': { '$regex': ?0, '$options': 'i' } }, { 'address': { '$regex': ?0, '$options': 'i' } }, { 'cityName': { '$regex': ?0, '$options': 'i' } } ] }")
    Flux<Location> searchLocationsAfterCursor(String query, String afterId, Pageable pageable);
}
