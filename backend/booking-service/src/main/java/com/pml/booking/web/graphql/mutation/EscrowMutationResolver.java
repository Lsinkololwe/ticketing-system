package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.EventEscrowAccount.EscrowStatus;
import com.pml.booking.service.EscrowService;
import com.pml.booking.web.graphql.dto.CreateEscrowAccountInput;
import com.pml.booking.web.graphql.dto.EscrowAccountMutationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * GraphQL Mutation Resolver for Escrow Account Operations
 *
 * Business Intent: Handles escrow account lifecycle management.
 * - Internal: Auto-create escrow when event is published
 * - Admin: Manual status management, lock/unlock, close accounts
 *
 * Escrow Lifecycle:
 * CREATED -> ACTIVE -> LOCKED -> PAYOUT_ELIGIBLE -> CLOSED
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EscrowMutationResolver {

    private final EscrowService escrowService;

    /**
     * Create escrow account for an event.
     * Called internally when an event is published.
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE', 'ROLE_ADMIN')")
    public Mono<EscrowAccountMutationResponse> createEscrowAccount(@InputArgument CreateEscrowAccountInput input) {
        log.info("GraphQL mutation: createEscrowAccount for event: {}", input.eventId());

        return escrowService.createEscrowAccount(
                        input.eventId(),
                        input.eventTitle() != null ? input.eventTitle() : "Event " + input.eventId(),
                        input.organizerId(),
                        input.organizerName() != null ? input.organizerName() : "Organizer",
                        LocalDateTime.now().plusDays(30) // Default event date, should be updated by catalog service
                )
                .map(escrow -> EscrowAccountMutationResponse.success("Escrow account created successfully", escrow))
                .onErrorResume(e -> {
                    log.error("Failed to create escrow account: {}", e.getMessage());
                    return Mono.just(EscrowAccountMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Update escrow account status.
     * Admin operation for manual status management.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EscrowAccountMutationResponse> updateEscrowAccountStatus(
            @InputArgument String accountId,
            @InputArgument EscrowStatus status,
            @InputArgument String reason
    ) {
        log.info("GraphQL mutation: updateEscrowAccountStatus({}, {}, {})", accountId, status, reason);

        return escrowService.updateEscrowAccountStatus(accountId, status, reason)
                .map(escrow -> EscrowAccountMutationResponse.success(
                        "Escrow account status updated to " + status, escrow))
                .onErrorResume(e -> {
                    log.error("Failed to update escrow account status: {}", e.getMessage());
                    return Mono.just(EscrowAccountMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Lock escrow account until a specific date.
     * Admin operation for extending hold periods or dispute resolution.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EscrowAccountMutationResponse> lockEscrowAccount(
            @InputArgument String accountId,
            @InputArgument LocalDateTime lockUntil,
            @InputArgument String reason
    ) {
        log.info("GraphQL mutation: lockEscrowAccount({}, {}, {})", accountId, lockUntil, reason);

        return escrowService.lockEscrowAccount(accountId, lockUntil, reason)
                .map(escrow -> EscrowAccountMutationResponse.success(
                        "Escrow account locked until " + lockUntil, escrow))
                .onErrorResume(e -> {
                    log.error("Failed to lock escrow account: {}", e.getMessage());
                    return Mono.just(EscrowAccountMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Unlock escrow account early.
     * Admin operation for early payout eligibility.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EscrowAccountMutationResponse> unlockEscrowAccount(
            @InputArgument String accountId,
            @InputArgument String reason
    ) {
        log.info("GraphQL mutation: unlockEscrowAccount({}, {})", accountId, reason);

        return escrowService.unlockEscrowAccount(accountId, reason)
                .map(escrow -> EscrowAccountMutationResponse.success(
                        "Escrow account unlocked and is now payout eligible", escrow))
                .onErrorResume(e -> {
                    log.error("Failed to unlock escrow account: {}", e.getMessage());
                    return Mono.just(EscrowAccountMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Mark escrow account as payout eligible.
     * Admin operation for manual transition after hold period.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EscrowAccountMutationResponse> markPayoutEligible(@InputArgument String accountId) {
        log.info("GraphQL mutation: markPayoutEligible({})", accountId);

        return escrowService.findById(accountId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Escrow account not found")))
                .flatMap(escrow -> escrowService.markPayoutEligible(escrow.getEventId()))
                .map(escrow -> EscrowAccountMutationResponse.success(
                        "Escrow account is now payout eligible", escrow))
                .onErrorResume(e -> {
                    log.error("Failed to mark escrow account payout eligible: {}", e.getMessage());
                    return Mono.just(EscrowAccountMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Close escrow account.
     * Admin operation for accounts with zero balance.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EscrowAccountMutationResponse> closeEscrowAccount(
            @InputArgument String accountId,
            @InputArgument String reason
    ) {
        log.info("GraphQL mutation: closeEscrowAccount({}, {})", accountId, reason);

        return escrowService.closeEscrowAccount(accountId, reason)
                .map(escrow -> EscrowAccountMutationResponse.success(
                        "Escrow account closed successfully", escrow))
                .onErrorResume(e -> {
                    log.error("Failed to close escrow account: {}", e.getMessage());
                    return Mono.just(EscrowAccountMutationResponse.error(e.getMessage()));
                });
    }
}
