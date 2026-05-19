package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.CreateTicketTierInput;
import com.pml.catalog.web.graphql.dto.DeleteMutationResponse;
import com.pml.catalog.web.graphql.dto.TierMutationResponse;
import com.pml.catalog.web.graphql.dto.UpdateTicketTierInput;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.service.TicketTierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

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
@RequiredArgsConstructor
public class TicketTierMutationResolver {

    private final TicketTierService tierService;

    /**
     * Create a new ticket tier for an event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<TierMutationResponse> createTicketTier(
            @InputArgument String eventId,
            @InputArgument CreateTicketTierInput input
    ) {
        log.info("Creating ticket tier {} for event {}", input.code(), eventId);
        return tierService.createTier(eventId, input)
                .map(tier -> TierMutationResponse.success(tier, "Ticket tier created successfully"))
                .onErrorResume(e -> {
                    log.error("Create ticket tier failed: {}", e.getMessage());
                    return Mono.just(TierMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Update an existing ticket tier
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<TierMutationResponse> updateTicketTier(
            @InputArgument String tierId,
            @InputArgument UpdateTicketTierInput input
    ) {
        log.info("Updating ticket tier {}", tierId);
        return tierService.updateTier(tierId, input)
                .map(tier -> TierMutationResponse.success(tier, "Ticket tier updated successfully"))
                .onErrorResume(e -> {
                    log.error("Update ticket tier failed: {}", e.getMessage());
                    return Mono.just(TierMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Delete a ticket tier
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<DeleteMutationResponse> deleteTicketTier(@InputArgument String tierId) {
        log.info("Deleting ticket tier {}", tierId);
        return tierService.deleteTier(tierId)
                .map(deleted -> DeleteMutationResponse.success("Ticket tier deleted successfully"))
                .onErrorResume(e -> {
                    log.error("Delete ticket tier failed: {}", e.getMessage());
                    return Mono.just(DeleteMutationResponse.error(e.getMessage()));
                });
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
    public Mono<TierMutationResponse> activateTicketTier(@InputArgument String tierId) {
        log.info("Activating ticket tier: {}", tierId);

        return tierService.activateTier(tierId)
                .map(tier -> TierMutationResponse.success(
                        tier,
                        "Ticket tier activated successfully"
                ))
                .onErrorResume(e -> {
                    log.error("Activate ticket tier failed: {}", e.getMessage());
                    return Mono.just(TierMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Deactivate a ticket tier (make it unavailable for purchase)
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<TierMutationResponse> deactivateTicketTier(@InputArgument String tierId) {
        log.info("Deactivating ticket tier: {}", tierId);

        return tierService.deactivateTier(tierId)
                .map(tier -> TierMutationResponse.success(
                        tier,
                        "Ticket tier deactivated successfully"
                ))
                .onErrorResume(e -> {
                    log.error("Deactivate ticket tier failed: {}", e.getMessage());
                    return Mono.just(TierMutationResponse.error(e.getMessage()));
                });
    }
}
