package com.pml.catalog.service;

import com.pml.catalog.domain.model.EventCategory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * EventCategory Service interface with cursor-based and admin pagination support.
 */
public interface EventCategoryService {

    // ==========================================
    // Single EventCategory Operations
    // ==========================================

    Mono<EventCategory> findById(String id);

    Mono<EventCategory> createCategory(EventCategory category);

    Mono<EventCategory> updateCategory(String id, EventCategory category);

    Mono<Void> deleteCategory(String id);

    /**
     * Activate an event category.
     */
    Mono<EventCategory> activateCategory(String id);

    /**
     * Deactivate an event category.
     */
    Mono<EventCategory> deactivateCategory(String id);

    // ==========================================
    // Flux-based Queries (for pagination helper methods)
    // ==========================================

    Flux<EventCategory> findPopularCategories(int limit);
}
