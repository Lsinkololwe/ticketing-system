package com.pml.identity.service;

import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.domain.enums.TransferStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Ownership Transfer Service Interface
 *
 * Manages organization ownership transfer workflow.
 */
public interface OwnershipTransferService {

    // ─────────────────────────────────────────────────────────────────────
    // Read Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Find transfer by ID
     */
    Mono<OwnershipTransferRequest> findById(String id);

    /**
     * Find transfer by token
     */
    Mono<OwnershipTransferRequest> findByToken(String transferToken);

    /**
     * Find pending transfer for organization
     */
    Mono<OwnershipTransferRequest> findPendingByOrganization(String organizationId);

    /**
     * Find all transfers for an organization
     */
    Flux<OwnershipTransferRequest> findByOrganization(String organizationId);

    /**
     * Find pending transfers for new owner (user)
     */
    Flux<OwnershipTransferRequest> findPendingByNewOwner(String newOwnerId);

    /**
     * Check if organization has pending transfer
     */
    Mono<Boolean> hasPendingTransfer(String organizationId);

    // ─────────────────────────────────────────────────────────────────────
    // Write Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Initiate ownership transfer
     * @param organizationId Organization to transfer
     * @param currentOwnerId Current owner initiating the transfer
     * @param newOwnerId New owner who will receive ownership
     * @param reason Optional reason for transfer
     * @return Created transfer request
     */
    Mono<OwnershipTransferRequest> initiate(
            String organizationId,
            String currentOwnerId,
            String newOwnerId,
            String reason
    );

    /**
     * Cancel transfer (by current owner)
     */
    Mono<OwnershipTransferRequest> cancel(String organizationId, String currentOwnerId);

    /**
     * Accept transfer (by new owner)
     * @param transferToken Transfer token from email/notification
     * @param newOwnerId User accepting the transfer
     * @param confirmationCode 2FA code for verification
     * @return Completed transfer request
     */
    Mono<OwnershipTransferRequest> accept(
            String transferToken,
            String newOwnerId,
            String confirmationCode
    );

    /**
     * Decline transfer (by new owner)
     */
    Mono<OwnershipTransferRequest> decline(String transferToken, String newOwnerId);

    /**
     * Expire old transfers (scheduled task)
     */
    Mono<Long> expireOldTransfers();
}
