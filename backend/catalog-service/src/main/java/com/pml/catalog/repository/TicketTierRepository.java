package com.pml.catalog.repository;

import com.pml.catalog.domain.model.TicketTier;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Ticket Tier Repository
 *
 * Reactive repository for managing ticket pricing tiers.
 *
 * Business Intent: Provide efficient queries for ticket tier management including
 * ordering, filtering by visibility, and code-based lookups.
 */
@Repository
public interface TicketTierRepository extends ReactiveMongoRepository<TicketTier, String> {

    /**
     * Find all tiers for an event, ordered by sort order
     *
     * @param eventId Event ID
     * @return Flux of ticket tiers
     */
    Flux<TicketTier> findByEventIdOrderBySortOrderAsc(String eventId);

    /**
     * Find active or inactive tiers for an event
     *
     * @param eventId Event ID
     * @param isActive Active status
     * @return Flux of ticket tiers
     */
    Flux<TicketTier> findByEventIdAndIsActiveOrderBySortOrderAsc(String eventId, boolean isActive);

    /**
     * Find hidden or visible tiers for an event
     *
     * @param eventId Event ID
     * @param isHidden Hidden status
     * @return Flux of ticket tiers
     */
    Flux<TicketTier> findByEventIdAndIsHiddenOrderBySortOrderAsc(String eventId, boolean isHidden);

    /**
     * Find tier by event ID and code
     *
     * @param eventId Event ID
     * @param code Tier code
     * @return Ticket tier if exists
     */
    Mono<TicketTier> findByEventIdAndCode(String eventId, String code);

    /**
     * Count tiers for an event
     *
     * @param eventId Event ID
     * @return Count of tiers
     */
    Mono<Long> countByEventId(String eventId);

    /**
     * Delete all tiers for an event
     *
     * @param eventId Event ID
     * @return Number of deleted tiers
     */
    Mono<Long> deleteByEventId(String eventId);
}
