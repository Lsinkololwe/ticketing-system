package com.pml.catalog.service.impl;

import com.pml.catalog.dto.*;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.repository.EventCategoryRepository;
import com.pml.catalog.service.EventCategoryService;
import com.pml.catalog.util.CursorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * EventCategory Service Implementation with cursor-based and admin pagination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCategoryServiceImpl implements EventCategoryService {

    private final EventCategoryRepository eventCategoryRepository;

    // ==========================================
    // Single EventCategory Operations
    // ==========================================

    @Override
    public Mono<EventCategory> findById(String id) {
        return eventCategoryRepository.findById(id);
    }

    @Override
    public Flux<EventCategory> findByIds(List<String> ids) {
        return eventCategoryRepository.findAllById(ids);
    }

    @Override
    public Mono<EventCategory> createCategory(EventCategory category) {
        log.info("Creating event category: {}", category.getName());
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        category.setActive(true);
        return eventCategoryRepository.save(category)
                .doOnSuccess(c -> log.info("EventCategory created: {}", c.getId()));
    }

    @Override
    public Mono<EventCategory> updateCategory(String id, EventCategory category) {
        return eventCategoryRepository.findById(id)
                .flatMap(existing -> {
                    existing.setName(category.getName());
                    existing.setDescription(category.getDescription());
                    existing.setIconUrl(category.getIconUrl());
                    existing.setColor(category.getColor());
                    existing.setDisplayOrder(category.getDisplayOrder());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return eventCategoryRepository.save(existing);
                });
    }

    @Override
    public Mono<Void> deleteCategory(String id) {
        return eventCategoryRepository.deleteById(id);
    }

    @Override
    public Mono<EventCategory> activateCategory(String id) {
        log.info("Activating event category: {}", id);
        return eventCategoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event category not found: " + id)))
                .flatMap(category -> {
                    category.setActive(true);
                    category.setUpdatedAt(LocalDateTime.now());
                    return eventCategoryRepository.save(category);
                })
                .doOnSuccess(c -> log.info("Event category activated: {}", c.getId()));
    }

    @Override
    public Mono<EventCategory> deactivateCategory(String id) {
        log.info("Deactivating event category: {}", id);
        return eventCategoryRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event category not found: " + id)))
                .flatMap(category -> {
                    category.setActive(false);
                    category.setUpdatedAt(LocalDateTime.now());
                    return eventCategoryRepository.save(category);
                })
                .doOnSuccess(c -> log.info("Event category deactivated: {}", c.getId()));
    }

    // ==========================================
    // Flux-based Queries (for pagination helper methods)
    // ==========================================

    @Override
    public Flux<EventCategory> findAllCategories() {
        return eventCategoryRepository.findAll();
    }

    @Override
    public Flux<EventCategory> findActiveCategories() {
        return eventCategoryRepository.findByIsActiveTrue();
    }

    @Override
    public Flux<EventCategory> searchCategories(String query) {
        return eventCategoryRepository.findByNameContainingIgnoreCaseAndIsActiveTrue(query);
    }

    @Override
    public Flux<EventCategory> findPopularCategories(int limit) {
        return eventCategoryRepository.findPopularCategories(limit);
    }

    // ==========================================
    // Cursor-based Pagination (for mobile infinite scroll)
    // ==========================================

    @Override
    public Mono<EventCategoryConnection> findCategoriesCursor(CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<EventCategory> categoriesFlux;
        if (afterId != null) {
            categoriesFlux = eventCategoryRepository.findAllAfterCursor(afterId, pageable);
        } else {
            categoriesFlux = eventCategoryRepository.findAllByOrderByIdAsc(pageable);
        }

        return buildConnection(categoriesFlux, limit, afterId != null);
    }

    @Override
    public Mono<EventCategoryConnection> findActiveCategoriesCursor(CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<EventCategory> categoriesFlux;
        if (afterId != null) {
            categoriesFlux = eventCategoryRepository.findCategoriesAfterCursor(afterId, pageable);
        } else {
            categoriesFlux = eventCategoryRepository.findByIsActiveTrueOrderByIdAsc(pageable);
        }

        return buildConnection(categoriesFlux, limit, afterId != null);
    }

    @Override
    public Mono<EventCategoryConnection> searchCategoriesCursor(String query, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<EventCategory> categoriesFlux;
        if (afterId != null) {
            categoriesFlux = eventCategoryRepository.searchCategoriesAfterCursor(query, afterId, pageable);
        } else {
            categoriesFlux = eventCategoryRepository.searchCategoriesFirstPage(query, pageable);
        }

        return buildConnection(categoriesFlux, limit, afterId != null);
    }

    // ==========================================
    // Admin Pagination (for dashboard tables)
    // ==========================================

    @Override
    public Mono<PagedResult<EventCategory>> findCategoriesAdmin(PageableInput pageable) {
        Pageable springPageable = pageable.toPageable();

        return Mono.zip(
                eventCategoryRepository.findAllBy(springPageable).collectList(),
                eventCategoryRepository.count()
        ).map(tuple -> PagedResult.of(
                tuple.getT1(),
                springPageable.getPageNumber(),
                springPageable.getPageSize(),
                tuple.getT2()
        ));
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private Mono<EventCategoryConnection> buildConnection(Flux<EventCategory> categoriesFlux, int limit, boolean hasPreviousPage) {
        return categoriesFlux.collectList().map(categories -> {
            boolean hasNextPage = categories.size() > limit;

            List<EventCategory> pageCategories = hasNextPage
                    ? categories.subList(0, limit)
                    : categories;

            List<EventCategoryEdge> edges = pageCategories.stream()
                    .map(EventCategoryEdge::from)
                    .toList();

            PageInfo pageInfo = PageInfo.builder()
                    .hasNextPage(hasNextPage)
                    .hasPreviousPage(hasPreviousPage)
                    .startCursor(edges.isEmpty() ? null : edges.get(0).getCursor())
                    .endCursor(edges.isEmpty() ? null : edges.get(edges.size() - 1).getCursor())
                    .build();

            return EventCategoryConnection.builder()
                    .edges(edges)
                    .pageInfo(pageInfo)
                    .build();
        });
    }
}
