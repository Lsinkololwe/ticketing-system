package com.pml.catalog.service;

import com.pml.catalog.dto.CursorPaginationInput;
import com.pml.catalog.dto.EventCategoryConnection;
import com.pml.catalog.dto.PageableInput;
import com.pml.catalog.dto.PagedResult;
import com.pml.catalog.domain.model.EventCategory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * EventCategory Service interface with cursor-based and admin pagination support.
 */
public interface EventCategoryService {

    // ==========================================
    // Single EventCategory Operations
    // ==========================================

    Mono<EventCategory> findById(String id);

    /**
     * Find multiple categories by IDs (for batch loading)
     */
    Flux<EventCategory> findByIds(List<String> ids);

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

    Flux<EventCategory> findAllCategories();

    Flux<EventCategory> findActiveCategories();

    Flux<EventCategory> searchCategories(String query);

    Flux<EventCategory> findPopularCategories(int limit);

    // ==========================================
    // Cursor-based Pagination (for mobile infinite scroll)
    // ==========================================

    Mono<EventCategoryConnection> findCategoriesCursor(CursorPaginationInput pagination);

    Mono<EventCategoryConnection> findActiveCategoriesCursor(CursorPaginationInput pagination);

    Mono<EventCategoryConnection> searchCategoriesCursor(String query, CursorPaginationInput pagination);

    // ==========================================
    // Admin Pagination (for dashboard tables)
    // ==========================================

    Mono<PagedResult<EventCategory>> findCategoriesAdmin(PageableInput pageable);
}
