package com.pml.booking.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.pml.shared.constants.EscrowStatus;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.service.EscrowService;
import com.pml.booking.web.graphql.dto.CreateEscrowAccountInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

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
@FailClosedOnRevocation
@Validated
@RequiredArgsConstructor
public class EscrowMutationResolver {

    private final EscrowService escrowService;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    /**
     * Create escrow account for an event.
     * Called internally when an event is published.
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('SCOPE_internal-write', 'ROLE_INTERNAL_SERVICE', 'ROLE_ADMIN')")
    public Mono<EventEscrowAccount> createEscrowAccount(@Valid @InputArgument CreateEscrowAccountInput input) {
        log.info("GraphQL mutation: createEscrowAccount for event: {}", input.eventId());

        return escrowService.createEscrowAccount(
                        input.eventId(),
                        input.eventTitle() != null ? input.eventTitle() : "Event " + input.eventId(),
                        input.organizerId(),
                        input.organizerName() != null ? input.organizerName() : "Organizer",
                        clock.instant().plus(Duration.ofDays(30)) // Default event date, should be updated by catalog service
                );
    }

    /**
     * Update escrow account status.
     * Admin operation for manual status management.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventEscrowAccount> updateEscrowAccountStatus(
            @InputArgument String accountId,
            @InputArgument EscrowStatus status,
            @InputArgument String reason
    ) {
        log.info("GraphQL mutation: updateEscrowAccountStatus({}, {}, {})", accountId, status, reason);

        return escrowService.updateEscrowAccountStatus(accountId, status, reason);
    }

    /**
     * Lock escrow account until a specific date.
     * Admin operation for extending hold periods or dispute resolution.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventEscrowAccount> lockEscrowAccount(
            @InputArgument String accountId,
            @InputArgument Instant lockUntil,
            @InputArgument String reason
    ) {
        log.info("GraphQL mutation: lockEscrowAccount({}, {}, {})", accountId, lockUntil, reason);

        return escrowService.lockEscrowAccount(accountId, lockUntil, reason);
    }

    /**
     * Unlock escrow account early.
     * Admin operation for early payout eligibility.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventEscrowAccount> unlockEscrowAccount(
            @InputArgument String accountId,
            @InputArgument String reason
    ) {
        log.info("GraphQL mutation: unlockEscrowAccount({}, {})", accountId, reason);

        return escrowService.unlockEscrowAccount(accountId, reason);
    }

    /**
     * Mark escrow account as payout eligible.
     * Admin operation for manual transition after hold period.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventEscrowAccount> markPayoutEligible(@InputArgument String accountId) {
        log.info("GraphQL mutation: markPayoutEligible({})", accountId);

        return escrowService.findById(accountId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Escrow account not found")))
                .flatMap(escrow -> escrowService.markPayoutEligible(escrow.getEventId()));
    }

    /**
     * Close escrow account.
     * Admin operation for accounts with zero balance.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventEscrowAccount> closeEscrowAccount(
            @InputArgument String accountId,
            @InputArgument String reason
    ) {
        log.info("GraphQL mutation: closeEscrowAccount({}, {})", accountId, reason);

        return escrowService.closeEscrowAccount(accountId, reason);
    }
}
