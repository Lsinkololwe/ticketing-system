package com.pml.identity.repository;

import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.domain.enums.TransferStatus;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Ownership Transfer Repository
 */
@Repository
public interface OwnershipTransferRepository extends ReactiveMongoRepository<OwnershipTransferRequest, String> {

    /**
     * Find transfer by unique token
     */
    Mono<OwnershipTransferRequest> findByTransferToken(String transferToken);

    /**
     * Check if transfer token exists
     */
    Mono<Boolean> existsByTransferToken(String transferToken);

    /**
     * Find pending transfer for an organization
     */
    Mono<OwnershipTransferRequest> findByOrganizationIdAndStatus(String organizationId, TransferStatus status);

    /**
     * Find all transfers for an organization
     */
    Flux<OwnershipTransferRequest> findByOrganizationId(String organizationId);

    /**
     * Find all transfers initiated by a user
     */
    Flux<OwnershipTransferRequest> findByCurrentOwnerId(String currentOwnerId);

    /**
     * Find all transfers targeted to a user
     */
    Flux<OwnershipTransferRequest> findByNewOwnerId(String newOwnerId);

    /**
     * Find pending transfers targeted to a user
     */
    Flux<OwnershipTransferRequest> findByNewOwnerIdAndStatus(String newOwnerId, TransferStatus status);

    /**
     * Check if there's a pending transfer for an organization
     */
    Mono<Boolean> existsByOrganizationIdAndStatus(String organizationId, TransferStatus status);

    /**
     * Find expired transfers (for cleanup)
     */
    @Query("{ 'status': 'PENDING', 'expiresAt': { $lt: ?0 } }")
    Flux<OwnershipTransferRequest> findExpiredTransfers(Instant now);

    /**
     * Find pending transfers that have expired
     */
    Flux<OwnershipTransferRequest> findByStatusAndExpiresAtBefore(TransferStatus status, Instant expiresAt);
}
