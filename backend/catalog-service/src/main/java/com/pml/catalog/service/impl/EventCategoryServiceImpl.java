package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.repository.EventCategoryRepository;
import com.pml.catalog.service.EventCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * EventCategory Service Implementation with cursor-based and admin pagination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCategoryServiceImpl implements EventCategoryService {

    private final EventCategoryRepository eventCategoryRepository;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    // ==========================================
    // Single EventCategory Operations
    // ==========================================

    @Override
    public Mono<EventCategory> findById(String id) {
        return eventCategoryRepository.findById(id);
    }

    @Override
    public Mono<EventCategory> createCategory(EventCategory category) {
        log.info("Creating event category: {}", category.getName());
        category.setCreatedAt(clock.instant());
        category.setUpdatedAt(clock.instant());
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
                    existing.setUpdatedAt(clock.instant());
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
                    category.setUpdatedAt(clock.instant());
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
                    category.setUpdatedAt(clock.instant());
                    return eventCategoryRepository.save(category);
                })
                .doOnSuccess(c -> log.info("Event category deactivated: {}", c.getId()));
    }

    // ==========================================
    // Flux-based Queries (for pagination helper methods)
    // ==========================================

    @Override
    public Flux<EventCategory> findPopularCategories(int limit) {
        return eventCategoryRepository.findPopularCategories(limit);
    }
}
