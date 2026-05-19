package com.pml.catalog.repository;

import com.pml.catalog.domain.model.Province;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Province Repository with cursor-based and admin pagination support.
 */
@Repository
public interface ProvinceRepository extends ReactiveMongoRepository<Province, String> {

    // ==========================================
    // Cursor-based pagination (for mobile infinite scroll)
    // ==========================================

    // First page queries
    Flux<Province> findByIsActiveTrueOrderByNameAsc(Pageable pageable);

    @Query("{ 'isActive': true, 'name': { '$regex': ?0, '$options': 'i' } }")
    Flux<Province> searchProvincesFirstPage(String query, Pageable pageable);

    // After cursor queries
    @Query("{ 'isActive': true, '_id': { '$gt': ?0 } }")
    Flux<Province> findProvincesAfterCursor(String afterId, Pageable pageable);

    @Query("{ 'isActive': true, '_id': { '$gt': ?1 }, 'name': { '$regex': ?0, '$options': 'i' } }")
    Flux<Province> searchProvincesAfterCursor(String query, String afterId, Pageable pageable);

    // ==========================================
    // Admin pagination (for admin dashboard tables)
    // ==========================================

    Flux<Province> findAllBy(Pageable pageable);

    // Count queries
    Mono<Long> countByIsActiveTrue();

    // ==========================================
    // Flux-based Queries (for service layer)
    // ==========================================

    Flux<Province> findByIsActiveTrue();

    Flux<Province> findByCountryAndIsActiveTrue(String country);

    Flux<Province> findByNameContainingIgnoreCaseAndIsActiveTrue(String query);
}
