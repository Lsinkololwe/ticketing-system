package com.pml.identity.service;

import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.model.VerificationDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Verification Document Service Interface
 *
 * Manages KYB verification documents for organizers.
 */
public interface VerificationDocumentService {

    // ─────────────────────────────────────────────────────────────────────
    // Read Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Find document by ID
     */
    Mono<VerificationDocument> findById(String id);

    /**
     * Find all documents for an organizer profile
     */
    Flux<VerificationDocument> findByOrganizerProfile(String organizerProfileId);

    /**
     * Find documents by status
     */
    Flux<VerificationDocument> findByOrganizerProfileAndStatus(
            String organizerProfileId,
            DocumentStatus status
    );

    /**
     * Find document by type
     */
    Mono<VerificationDocument> findByOrganizerProfileAndType(
            String organizerProfileId,
            String documentType
    );

    /**
     * Check if document type exists
     */
    Mono<Boolean> existsByOrganizerProfileAndType(String organizerProfileId, String documentType);

    /**
     * Count documents for organizer profile
     */
    Mono<Long> countByOrganizerProfile(String organizerProfileId);

    /**
     * Count approved documents
     */
    Mono<Long> countApprovedByOrganizerProfile(String organizerProfileId);

    /**
     * Find pending documents (admin queue)
     */
    Flux<VerificationDocument> findPendingDocuments();

    // ─────────────────────────────────────────────────────────────────────
    // Write Operations
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Upload document
     */
    Mono<VerificationDocument> upload(
            String organizerProfileId,
            String documentType,
            String documentUrl,
            String fileName,
            Long fileSize,
            String mimeType
    );

    /**
     * Approve document (admin action)
     */
    Mono<VerificationDocument> approve(String documentId, String verifiedById);

    /**
     * Reject document (admin action)
     */
    Mono<VerificationDocument> reject(String documentId, String reason, String rejectedById);

    /**
     * Delete document
     */
    Mono<Void> delete(String documentId);

    /**
     * Delete all documents for organizer profile
     */
    Mono<Void> deleteByOrganizerProfile(String organizerProfileId);
}
