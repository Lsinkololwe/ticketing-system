package com.pml.identity.repository;

import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.domain.enums.TransferStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
     * Find pending transfer for an organization
     */
    Mono<OwnershipTransferRequest> findByOrganizationIdAndStatus(String organizationId, TransferStatus status);

    /**
     * Find all transfers for an organization
     */
    Flux<OwnershipTransferRequest> findByOrganizationId(String organizationId);

    /**
     * Find pending transfers targeted to a user
     */
    Flux<OwnershipTransferRequest> findByNewOwnerIdAndStatus(String newOwnerId, TransferStatus status);

    /**
     * Check if there's a pending transfer for an organization
     */
    Mono<Boolean> existsByOrganizationIdAndStatus(String organizationId, TransferStatus status);
}
