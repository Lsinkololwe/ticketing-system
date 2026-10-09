package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.CreateTicketTierInput;
import com.pml.catalog.web.graphql.dto.UpdateTicketTierInput;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.service.TicketTierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/**
 * Ticket Tier Mutation Resolver
 *
 * GraphQL mutations for managing ticket pricing tiers.
 *
 * Business Intent: Allow event organizers and admins to create sophisticated
 * pricing strategies with multiple tiers.
 */
@Slf4j

@DgsComponent
@Validated
@RequiredArgsConstructor
public class TicketTierMutationResolver {

    private final TicketTierService tierService;

    /**
     * Create a new ticket tier for an event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<TicketTier> createTicketTier(
            @InputArgument String eventId,
            @Valid @InputArgument CreateTicketTierInput input
    ) {
        log.info("Creating ticket tier {} for event {}", input.code(), eventId);
        return tierService.createTier(eventId, input);
    }

    /**
     * Update an existing ticket tier
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<TicketTier> updateTicketTier(
            @InputArgument String tierId,
            @Valid @InputArgument UpdateTicketTierInput input
    ) {
        log.info("Updating ticket tier {}", tierId);
        return tierService.updateTier(tierId, input);
    }

    /**
     * Delete a ticket tier
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<String> deleteTicketTier(@InputArgument String tierId) {
        log.info("Deleting ticket tier {}", tierId);
        return tierService.deleteTier(tierId)
                .thenReturn(tierId);
    }

    /**
     * Reorder ticket tiers for an event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Flux<TicketTier> reorderTicketTiers(
            @InputArgument String eventId,
            @InputArgument List<String> tierIds
    ) {
        log.info("Reordering {} tiers for event {}", tierIds.size(), eventId);
        return tierService.reorderTiers(eventId, tierIds);
    }

    /**
     * Activate a ticket tier (make it available for purchase)
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<TicketTier> activateTicketTier(@InputArgument String tierId) {
        log.info("Activating ticket tier: {}", tierId);
        return tierService.activateTier(tierId);
    }

    /**
     * Deactivate a ticket tier (make it unavailable for purchase)
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<TicketTier> deactivateTicketTier(@InputArgument String tierId) {
        log.info("Deactivating ticket tier: {}", tierId);
        return tierService.deactivateTier(tierId);
    }
}
