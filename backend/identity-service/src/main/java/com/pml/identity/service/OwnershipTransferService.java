package com.pml.identity.service;

import com.pml.identity.domain.model.OwnershipTransferRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Ownership Transfer Service Interface
 *
 * <p>The reads GraphQL uses and the writes {@code OwnershipTransferWorkflow}'s
 * activities make. Every write is safe to repeat: a retried activity finds the transfer where it
 * was moving and returns it.
 */
public interface OwnershipTransferService {

    // ─────────────────────────────────────────────────────────────────────
    // Read Operations
    // ─────────────────────────────────────────────────────────────────────

    Mono<OwnershipTransferRequest> findById(String id);

    Mono<OwnershipTransferRequest> findByToken(String transferToken);

    Mono<OwnershipTransferRequest> findPendingByOrganization(String organizationId);

    Flux<OwnershipTransferRequest> findByOrganization(String organizationId);

    Flux<OwnershipTransferRequest> findPendingByNewOwner(String newOwnerId);

    Mono<Boolean> hasPendingTransfer(String organizationId);

    // ─────────────────────────────────────────────────────────────────────
    // Write Operations · called by workflow activities
    // ─────────────────────────────────────────────────────────────────────

    /** Validates the nomination and writes a {@code PENDING} transfer under {@code transferId}. */
    Mono<OwnershipTransferRequest> initiate(String transferId, String organizationId, String currentOwnerId,
                                            String newOwnerId, String reason);

    /** The nominee's confirmation: claim, demote, promote and the organization's owner, in one transaction. */
    Mono<OwnershipTransferRequest> complete(String transferId);

    Mono<OwnershipTransferRequest> decline(String transferId, String newOwnerId);

    Mono<OwnershipTransferRequest> cancel(String transferId, String currentOwnerId);

    Mono<OwnershipTransferRequest> expire(String transferId);

    /** Marks both parties' memberships for the group-mirror repair. */
    Mono<OwnershipTransferRequest> markMirrorPending(String transferId);
}
