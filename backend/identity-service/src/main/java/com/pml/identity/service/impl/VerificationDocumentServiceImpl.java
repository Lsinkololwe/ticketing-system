package com.pml.identity.service.impl;

import com.pml.shared.constants.DocumentStatus;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.VerificationDocumentRepository;
import com.pml.identity.service.VerificationDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.pml.identity.workflow.notify.NotificationProcess;
import com.pml.identity.workflow.notify.NotificationRules;
import com.pml.identity.workflow.notify.NotificationWorkflow;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Verification Document Service Implementation
 *
 * Manages KYB verification documents for organizations including:
 * - Document upload
 * - Admin approval/rejection
 * - Status tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationDocumentServiceImpl implements VerificationDocumentService {

    private final VerificationDocumentRepository documentRepository;
    private final OrganizationRepository organizationRepository;
    /** Messages are requested after the write, and their failure stays theirs. */
    private final NotificationProcess notifications;

    /** The injected platform clock, so every timestamp below is freezable. */
    private final java.time.Clock clock;
    // ========================================================================
    // READ OPERATIONS
    // ========================================================================

    @Override
    public Mono<VerificationDocument> findById(String id) {
        return documentRepository.findById(id);
    }

    @Override
    public Flux<VerificationDocument> findByOrganization(String organizationId) {
        return documentRepository.findByOrganizationId(organizationId);
    }

    @Override
    public Flux<VerificationDocument> findByOrganizationAndStatus(
            String organizationId,
            DocumentStatus status) {
        return documentRepository.findByOrganizationIdAndStatus(organizationId, status);
    }

    @Override
    public Mono<VerificationDocument> findByOrganizationAndType(
            String organizationId,
            String documentType) {
        return documentRepository.findByOrganizationIdAndDocumentType(organizationId, documentType);
    }

    @Override
    public Mono<Boolean> existsByOrganizationAndType(String organizationId, String documentType) {
        return documentRepository.existsByOrganizationIdAndDocumentType(organizationId, documentType);
    }

    @Override
    public Mono<Long> countByOrganization(String organizationId) {
        return documentRepository.countByOrganizationId(organizationId);
    }

    @Override
    public Mono<Long> countApprovedByOrganization(String organizationId) {
        return documentRepository.countByOrganizationIdAndStatus(organizationId, DocumentStatus.APPROVED);
    }

    @Override
    public Flux<VerificationDocument> findPendingDocuments() {
        return documentRepository.findByStatus(DocumentStatus.PENDING);
    }

    // ========================================================================
    // WRITE OPERATIONS
    // ========================================================================

    @Override
    public Mono<VerificationDocument> upload(
            String organizationId,
            String documentType,
            String documentUrl,
            String fileName,
            Long fileSize,
            String mimeType) {
        log.info("Uploading document type: {} for organization: {}", documentType, organizationId);

        // Guard the arguments BEFORE touching the repository, and signal the
        // failure through the Mono.
        //
        // findById(null) throws IllegalArgumentException eagerly, at assembly
        // time — before any subscription. An eager throw from a method that
        // returns Mono escapes the caller's .onErrorResume entirely, so a
        // null organizationId surfaced as an unhandled exception rather than
        // as this service's error contract. Every reactive caller was written
        // on the assumption that it could not do that.
        if (organizationId == null || organizationId.isBlank()) {
            return Mono.error(new IllegalArgumentException("Organization ID is required"));
        }
        if (documentType == null || documentType.isBlank()) {
            return Mono.error(new IllegalArgumentException("Document type is required"));
        }

        // Validate organization exists
        return organizationRepository.findById(organizationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Organization not found: " + organizationId)))
                .flatMap(organization -> {
                    // Check if document type already exists
                    return existsByOrganizationAndType(organizationId, documentType)
                            .flatMap(exists -> {
                                if (exists) {
                                    // Update existing document
                                    return findByOrganizationAndType(organizationId, documentType)
                                            .flatMap(existing -> {
                                                existing.setDocumentUrl(documentUrl);
                                                existing.setFileName(fileName);
                                                existing.setFileSize(fileSize);
                                                existing.setMimeType(mimeType);
                                                existing.setStatus(DocumentStatus.PENDING);
                                                existing.setVerifiedAt(null);
                                                existing.setVerifiedById(null);
                                                existing.setRejectionReason(null);
                                                return documentRepository.save(existing);
                                            });
                                }

                                // Create new document
                                VerificationDocument document = VerificationDocument.builder()
                                        .organizationId(organizationId)
                                        .documentType(documentType)
                                        .documentUrl(documentUrl)
                                        .fileName(fileName)
                                        .fileSize(fileSize)
                                        .mimeType(mimeType)
                                        .status(DocumentStatus.PENDING)
                                        .uploadedAt(clock.instant())
                                        .build();

                                return documentRepository.save(document);
                            })
                            .doOnSuccess(saved -> log.info("Document uploaded: {} for organization: {}", saved.getId(), organizationId))
                            .flatMap(saved -> checkAndUpdateOrganizationStatus(organizationId).thenReturn(saved));
                });
    }

    @Override
    public Mono<VerificationDocument> approve(String documentId, String verifiedById) {
        log.info("Approving document: {} by admin: {}", documentId, verifiedById);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Document not found: " + documentId)))
                .flatMap(document -> {
                    if (document.getStatus() != DocumentStatus.PENDING) {
                        return Mono.error(new IllegalStateException("Document is not pending approval"));
                    }

                    document.setStatus(DocumentStatus.APPROVED);
                    document.setVerifiedAt(clock.instant());
                    document.setVerifiedById(verifiedById);
                    document.setRejectionReason(null);

                    return documentRepository.save(document)
                            .doOnSuccess(approved -> log.info("Document approved: {}", approved.getId()))
                            .flatMap(approved -> checkAndUpdateOrganizationStatus(document.getOrganizationId()).thenReturn(approved))
                            .flatMap(approved -> notifyDocument("document.approved", approved).thenReturn(approved));
                });
    }

    @Override
    public Mono<VerificationDocument> reject(String documentId, String reason, String rejectedById) {
        log.info("Rejecting document: {} - Reason: {}", documentId, reason);

        return documentRepository.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Document not found: " + documentId)))
                .flatMap(document -> {
                    if (document.getStatus() != DocumentStatus.PENDING) {
                        return Mono.error(new IllegalStateException("Document is not pending approval"));
                    }

                    document.setStatus(DocumentStatus.REJECTED);
                    document.setVerifiedAt(clock.instant());
                    document.setVerifiedById(rejectedById);
                    document.setRejectionReason(reason);

                    return documentRepository.save(document)
                            .doOnSuccess(rejected -> log.info("Document rejected: {}", rejected.getId()))
                            .flatMap(rejected -> notifyDocument("document.rejected", rejected).thenReturn(rejected));
                });
    }

    @Override
    public Mono<Void> delete(String documentId) {
        log.info("Deleting document: {}", documentId);
        return documentRepository.deleteById(documentId);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Marks the organization's documents verified once three are approved. Part of the caller's
     * chain, so the response waits for it; a failure is logged and does not undo the document write.
     */
    private Mono<Void> checkAndUpdateOrganizationStatus(String organizationId) {
        return countApprovedByOrganization(organizationId)
                .flatMap(approvedCount -> {
                    // Check if all required documents are approved (minimum 3 typically)
                    if (approvedCount >= 3) {
                        return organizationRepository.findById(organizationId)
                                .flatMap(organization -> {
                                    organization.setDocumentsVerified(true);
                                    return organizationRepository.save(organization);
                                });
                    }
                    return Mono.empty();
                })
                .doOnNext(result -> log.info("Updated organization documents verification status"))
                .onErrorResume(error -> {
                    log.warn("Failed to update organization status: {}", error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    /** The organization's owner is told of each review; the destination is resolved at send time. */
    private Mono<Void> notifyDocument(String templateKey, VerificationDocument document) {
        return notifications.request(new NotificationWorkflow.Request(
                NotificationRules.key(templateKey, document.getId()),
                templateKey, null, NotificationRules.VERIFICATION_DOCUMENT, document.getId()));
    }
}
