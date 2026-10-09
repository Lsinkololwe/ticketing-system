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

    /*
     * This interface does not move availability. InventoryServiceImpl owns every hold and sale:
     * each is a single findAndModify with an $expr guard and $inc, so the check and the write are
     * one atomic step. Reading availableQuantity into Java, comparing it and writing it back loses
     * writes under contention — the property that decides whether a venue oversells — so no
     * second path for adjusting inventory belongs here.
     */

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

    /**
     * The tier if the caller may see it: anyone sees an active, visible tier of a published event;
     * a member of the owning organization sees any of its tiers. Otherwise empty.
     */
    Mono<TicketTier> findVisibleToCaller(String tierId);

    /**
     * The event's tiers the caller may see. Hidden tiers, and the tiers of an event that is not
     * published, are listed only to a member of the owning organization; anyone else gets none.
     */
    Flux<TicketTier> findForCaller(String eventId, boolean includeHidden);

    /** Active tiers with seats left, for a published event only. */
    Flux<TicketTier> findAvailableForPurchase(String eventId);
}
