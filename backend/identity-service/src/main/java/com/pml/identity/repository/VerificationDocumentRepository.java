package com.pml.identity.repository;

import java.util.Collection;
import com.pml.shared.constants.DocumentStatus;
import com.pml.identity.domain.model.VerificationDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Verification Document Repository
 */
@Repository
public interface VerificationDocumentRepository extends ReactiveMongoRepository<VerificationDocument, String> {

    /**
     * Find all documents for an organization
     */
    Flux<VerificationDocument> findByOrganizationId(String organizationId);

    /**
     * Find documents by organization and status
     */
    Flux<VerificationDocument> findByOrganizationIdAndStatus(String organizationId, DocumentStatus status);

    /**
     * Find document by organization and type
     */
    Mono<VerificationDocument> findByOrganizationIdAndDocumentType(String organizationId, String documentType);

    /**
     * Check if document exists for organization and type
     */
    Mono<Boolean> existsByOrganizationIdAndDocumentType(String organizationId, String documentType);

    /**
     * Count documents by organization
     */
    Mono<Long> countByOrganizationId(String organizationId);

    /**
     * Count approved documents by organization
     */
    Mono<Long> countByOrganizationIdAndStatus(String organizationId, DocumentStatus status);

    /**
     * Find documents by status
     */
    Flux<VerificationDocument> findByStatus(DocumentStatus status);

    Mono<VerificationDocument> findByIdAndOrganizationIdIn(String id, Collection<String> organizationIds);
}
