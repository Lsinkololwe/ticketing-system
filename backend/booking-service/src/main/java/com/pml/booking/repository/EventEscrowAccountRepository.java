package com.pml.booking.repository;

import com.pml.shared.constants.EscrowStatus;
import com.pml.booking.domain.model.EventEscrowAccount;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * Repository for EventEscrowAccount entities.
 *
 * Provides reactive access to per-event escrow accounts stored in MongoDB.
 * Each event has one escrow account that holds organizer funds until payout.
 */
@Repository
public interface EventEscrowAccountRepository extends ReactiveMongoRepository<EventEscrowAccount, String> {

    /**
     * Find escrow account by account number.
     */
    /**
     * An escrow account by id, restricted to the caller's organizations.
     *
     * <p>The boundary is the {@code IN} clause. {@code escrowTransactions(escrowAccountId, page)}
     * is open to the ORGANIZER role, and the account id arrives from the client — so without
     * this an organizer reads any event's money movements by id, which is every ticket sale,
     * refund and payout of a competitor's festival.
     */
    Mono<EventEscrowAccount> findByIdAndOrganizationIdIn(
            String id, java.util.Collection<String> organizationIds);

    Mono<EventEscrowAccount> findByAccountNumber(String accountNumber);

    /**
     * Find escrow account for a specific event.
     * Each event has exactly one escrow account.
     */
    Mono<EventEscrowAccount> findByEventId(String eventId);

    /**
     * Every escrow account belonging to an organization.
     *
     * <p>{@code organizationId} is the tenant key, not
     * {@code organizerId}. The difference is not cosmetic: an organizer is a
     * person and an organization is the account. Scoping money by the person who
     * happened to create the escrow means a finance colleague added to the team
     * sees an empty
     * dashboard and cannot be told why.
     *
     * <p>Filtering here is tenant SCOPING and is not, by itself, authorization.
     * Whether the caller may act for this organization is a separate question,
     * answered by {@code TenantAccessGuard} — a query filter cannot tell you
     * whether the person asking belongs to the tenant they named.
     */
    Flux<EventEscrowAccount> findByOrganizationId(String organizationId);

    /** Escrow accounts for an organization in a given state. */
    Flux<EventEscrowAccount> findByOrganizationIdAndStatus(String organizationId, EscrowStatus status);

    /**
     * @deprecated Escrow is scoped by organization. Retained only for
     *     reporting paths that genuinely mean "what did this PERSON create",
     *     never for access decisions.
     */
    @Deprecated
    Flux<EventEscrowAccount> findByOrganizerId(String organizerId);

    /** @deprecated see {@link #findByOrganizerId(String)}. */
    @Deprecated
    Flux<EventEscrowAccount> findByOrganizerIdAndStatus(String organizerId, EscrowStatus status);

    /**
     * Find all escrow accounts with a specific status.
     */
    Flux<EventEscrowAccount> findByStatus(EscrowStatus status);

    /**
     * Find payout-eligible accounts for an organizer.
     * These are accounts where organizer can request payout.
     */
    default Flux<EventEscrowAccount> findPayoutEligibleByOrganizerId(String organizerId) {
        return findByOrganizerIdAndStatus(organizerId, EscrowStatus.PAYOUT_ELIGIBLE);
    }

    /**
     * Check if escrow account exists for an event.
     */
    Mono<Boolean> existsByEventId(String eventId);

    /**
     * Find all escrow accounts excluding certain statuses.
     * Used for reconciliation to exclude CLOSED/CANCELLED accounts.
     *
     * @param statuses List of statuses to exclude
     * @return Flux of matching escrow accounts
     */
    Flux<EventEscrowAccount> findByStatusNotIn(java.util.Collection<EscrowStatus> statuses);

    /**
     * Escrow accounts belonging to any of the caller's organizations.
     *
     * <p>Contrast the {@code escrowAccounts} query, which is
     * {@code hasAnyRole('ADMIN','FINANCE')} over {@code findAll()} — every account on the
     * platform. That operation is the finance team's platform-wide view and is not a substitute
     * for this one: an organizer must only ever see their own organizations' accounts.
     *
     * @param organizationIds the caller's active memberships; empty matches nothing
     */
    Flux<EventEscrowAccount> findByOrganizationIdIn(Collection<String> organizationIds);
}