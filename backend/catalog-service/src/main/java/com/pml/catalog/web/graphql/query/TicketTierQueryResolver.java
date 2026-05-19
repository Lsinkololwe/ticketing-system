package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.service.TicketTierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Ticket Tier Query Resolver
 *
 * GraphQL queries for ticket tier information.
 *
 * Business Intent: Provide public access to ticket pricing tiers with
 * optional visibility filtering.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class TicketTierQueryResolver {

    private final TicketTierService tierService;

    /**
     * Get a specific ticket tier by ID
     */
    @DgsQuery
    public Mono<TicketTier> ticketTier(@InputArgument String id) {
        log.debug("Fetching ticket tier {}", id);
        return tierService.findById(id);
    }

    /**
     * Get all ticket tiers for an event
     */
    @DgsQuery
    public Flux<TicketTier> eventTicketTiers(
            @InputArgument String eventId,
            @InputArgument Boolean includeHidden
    ) {
        boolean include = includeHidden != null && includeHidden;
        log.debug("Fetching ticket tiers for event {} (includeHidden: {})", eventId, include);
        return tierService.findByEventId(eventId, include);
    }

    /**
     * Get available (active, visible, with remaining capacity) ticket tiers for an event.
     * Used by mobile app for ticket purchase.
     */
    @DgsQuery
    public Flux<TicketTier> availableTicketTiers(@InputArgument String eventId) {
        log.debug("Fetching available ticket tiers for event {}", eventId);
        return tierService.findByEventId(eventId, false)
                .filter(tier -> tier.isActive() && tier.getAvailableQuantity() > 0);
    }
}
