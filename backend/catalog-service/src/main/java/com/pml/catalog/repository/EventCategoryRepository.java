package com.pml.catalog.repository;

import com.pml.catalog.domain.model.EventCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Event Category Repository with cursor-based and admin pagination support.
 */
@Repository
public interface EventCategoryRepository extends ReactiveMongoRepository<EventCategory, String> {

    // ==========================================
    // Cursor-based pagination (for mobile infinite scroll)
    // ==========================================

    // First page queries
    Flux<EventCategory> findByIsActiveTrueOrderByIdAsc(Pageable pageable);

    @Query("{ 'isActive': true, 'name': { '$regex': ?0, '$options': 'i' } }")
    Flux<EventCategory> searchCategoriesFirstPage(String query, Pageable pageable);

    // After cursor queries
    @Query("{ 'isActive': true, '_id': { '$gt': ?0 } }")
    Flux<EventCategory> findCategoriesAfterCursor(String afterId, Pageable pageable);

    @Query("{ 'isActive': true, '_id': { '$gt': ?1 }, 'name': { '$regex': ?0, '$options': 'i' } }")
    Flux<EventCategory> searchCategoriesAfterCursor(String query, String afterId, Pageable pageable);

    // All categories cursor pagination
    Flux<EventCategory> findAllByOrderByIdAsc(Pageable pageable);

    @Query("{ '_id': { '$gt': ?0 } }")
    Flux<EventCategory> findAllAfterCursor(String afterId, Pageable pageable);

    // ==========================================
    // Admin pagination (for admin dashboard tables)
    // ==========================================

    Flux<EventCategory> findAllBy(Pageable pageable);

    // Count queries
    Mono<Long> countByIsActiveTrue();

    // ==========================================
    // Flux-based Queries (for service layer)
    // ==========================================

    Flux<EventCategory> findByIsActiveTrue();

    Flux<EventCategory> findByNameContainingIgnoreCaseAndIsActiveTrue(String query);

    @Query(value = "{ 'isActive': true }", sort = "{ 'eventCount': -1 }")
    Flux<EventCategory> findPopularCategories(int limit);
}
