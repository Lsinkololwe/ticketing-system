package com.pml.identity.repository;

import com.pml.identity.domain.enums.DocumentStatus;
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
     * Find all documents for an organizer profile
     */
    Flux<VerificationDocument> findByOrganizerProfileId(String organizerProfileId);

    /**
     * Find documents by organizer profile and status
     */
    Flux<VerificationDocument> findByOrganizerProfileIdAndStatus(String organizerProfileId, DocumentStatus status);

    /**
     * Find document by organizer profile and type
     */
    Mono<VerificationDocument> findByOrganizerProfileIdAndDocumentType(String organizerProfileId, String documentType);

    /**
     * Check if document exists for organizer profile and type
     */
    Mono<Boolean> existsByOrganizerProfileIdAndDocumentType(String organizerProfileId, String documentType);

    /**
     * Count documents by organizer profile
     */
    Mono<Long> countByOrganizerProfileId(String organizerProfileId);

    /**
     * Count approved documents by organizer profile
     */
    Mono<Long> countByOrganizerProfileIdAndStatus(String organizerProfileId, DocumentStatus status);

    /**
     * Delete all documents for an organizer profile
     */
    Mono<Void> deleteByOrganizerProfileId(String organizerProfileId);

    /**
     * Find all pending documents (for admin review queue)
     */
    Flux<VerificationDocument> findByStatusOrderByUploadedAtAsc(DocumentStatus status);

    /**
     * Find documents by status
     */
    Flux<VerificationDocument> findByStatus(DocumentStatus status);
}
