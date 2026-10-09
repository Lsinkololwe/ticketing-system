package com.pml.catalog.repository;

import com.pml.catalog.domain.model.EventCategory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
/**
 * Event Category Repository with cursor-based and admin pagination support.
 */
@Repository
public interface EventCategoryRepository extends ReactiveMongoRepository<EventCategory, String> {

    // ==========================================
    // Admin pagination (for admin dashboard tables)
    // ==========================================

    Flux<EventCategory> findAllBy(Pageable pageable);

    // ==========================================
    // Flux-based Queries (for service layer)
    // ==========================================

    @Query(value = "{ 'isActive': true }", sort = "{ 'eventCount': -1 }")
    Flux<EventCategory> findPopularCategories(int limit);
}
