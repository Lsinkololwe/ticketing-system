package com.pml.identity.service;

import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.model.VerificationDocument;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Verification Document Service Interface
 *
 * Manages KYB verification documents for organizations.
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
     * Find all documents for an organization
     */
    Flux<VerificationDocument> findByOrganization(String organizationId);

    /**
     * Find documents by status
     */
    Flux<VerificationDocument> findByOrganizationAndStatus(
            String organizationId,
            DocumentStatus status
    );

    /**
     * Find document by type
     */
    Mono<VerificationDocument> findByOrganizationAndType(
            String organizationId,
            String documentType
    );

    /**
     * Check if document type exists
     */
    Mono<Boolean> existsByOrganizationAndType(String organizationId, String documentType);

    /**
     * Count documents for organization
     */
    Mono<Long> countByOrganization(String organizationId);

    /**
     * Count approved documents
     */
    Mono<Long> countApprovedByOrganization(String organizationId);

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
            String organizationId,
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
     * Delete all documents for organization
     */
    Mono<Void> deleteByOrganization(String organizationId);
}
