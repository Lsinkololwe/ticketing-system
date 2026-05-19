package com.pml.catalog.repository;

import com.pml.catalog.domain.model.City;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * City Repository with cursor-based and admin pagination support.
 */
@Repository
public interface CityRepository extends ReactiveMongoRepository<City, String> {

    // ==========================================
    // Cursor-based pagination (for mobile infinite scroll)
    // ==========================================

    // First page queries
    Flux<City> findByIsActiveTrueOrderByNameAsc(Pageable pageable);

    @Query("{ 'provinceId': ?0, 'isActive': true }")
    Flux<City> findByProvinceFirstPage(String provinceId, Pageable pageable);

    @Query("{ 'isActive': true, 'name': { '$regex': ?0, '$options': 'i' } }")
    Flux<City> searchCitiesFirstPage(String query, Pageable pageable);

    // After cursor queries
    @Query("{ 'isActive': true, '_id': { '$gt': ?0 } }")
    Flux<City> findCitiesAfterCursor(String afterId, Pageable pageable);

    @Query("{ 'provinceId': ?0, 'isActive': true, '_id': { '$gt': ?1 } }")
    Flux<City> findByProvinceAfterCursor(String provinceId, String afterId, Pageable pageable);

    @Query("{ 'isActive': true, '_id': { '$gt': ?1 }, 'name': { '$regex': ?0, '$options': 'i' } }")
    Flux<City> searchCitiesAfterCursor(String query, String afterId, Pageable pageable);

    // ==========================================
    // Admin pagination (for admin dashboard tables)
    // ==========================================

    Flux<City> findAllBy(Pageable pageable);

    // Count queries
    Mono<Long> countByProvinceId(String provinceId);

    Mono<Long> countByIsActiveTrue();

    // ==========================================
    // Flux-based Queries (for service layer)
    // ==========================================

    Flux<City> findByIsActiveTrue();

    Flux<City> findByProvinceIdAndIsActiveTrue(String provinceId);

    Flux<City> findByCountryAndIsActiveTrue(String country);

    Flux<City> findByNameContainingIgnoreCaseAndIsActiveTrue(String query);

    @Query("{ 'isActive': true, 'eventCount': { '$gt': 0 } }")
    Flux<City> findCitiesWithEvents();
}
