package com.pml.catalog.service;

import com.pml.catalog.web.graphql.dto.CreateTicketTierInput;
import com.pml.catalog.web.graphql.dto.UpdateTicketTierInput;
import com.pml.catalog.domain.model.TicketTier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Ticket Tier Service
 *
 * Service for managing ticket pricing tiers.
 *
 * Business Intent: Support sophisticated pricing strategies with multiple
 * ticket types, early bird pricing, and purchase limits.
 */
public interface TicketTierService {

    /**
     * Find ticket tier by ID
     *
     * @param id Tier ID
     * @return Ticket tier
     */
    Mono<TicketTier> findById(String id);

    /**
     * Find all tiers for an event
     *
     * @param eventId Event ID
     * @param includeHidden Whether to include hidden tiers
     * @return Flux of ticket tiers
     */
    Flux<TicketTier> findByEventId(String eventId, boolean includeHidden);

    /**
     * Create a new ticket tier
     *
     * @param eventId Event ID
     * @param input Tier creation input
     * @return Created ticket tier
     */
    Mono<TicketTier> createTier(String eventId, CreateTicketTierInput input);

    /**
     * Update an existing ticket tier
     *
     * @param tierId Tier ID
     * @param input Update input
     * @return Updated ticket tier
     */
    Mono<TicketTier> updateTier(String tierId, UpdateTicketTierInput input);

    /**
     * Delete a ticket tier
     *
     * @param tierId Tier ID
     * @return true if deleted
     */
    Mono<Boolean> deleteTier(String tierId);

    /**
     * Reorder ticket tiers
     *
     * @param eventId Event ID
     * @param tierIds List of tier IDs in desired order
     * @return Updated tiers in new order
     */
    Flux<TicketTier> reorderTiers(String eventId, List<String> tierIds);

    /**
     * Decrement available quantity (when ticket sold)
     *
     * @param tierId Tier ID
     * @param quantity Quantity to decrement
     * @return Updated tier
     */
    Mono<TicketTier> decrementAvailability(String tierId, int quantity);

    /**
     * Increment available quantity (when ticket refunded)
     *
     * @param tierId Tier ID
     * @param quantity Quantity to increment
     * @return Updated tier
     */
    Mono<TicketTier> incrementAvailability(String tierId, int quantity);

    /**
     * Activate a ticket tier (make it available for purchase)
     *
     * @param tierId Tier ID
     * @return Updated tier
     */
    Mono<TicketTier> activateTier(String tierId);

    /**
     * Deactivate a ticket tier (make it unavailable for purchase)
     *
     * @param tierId Tier ID
     * @return Updated tier
     */
    Mono<TicketTier> deactivateTier(String tierId);
}
