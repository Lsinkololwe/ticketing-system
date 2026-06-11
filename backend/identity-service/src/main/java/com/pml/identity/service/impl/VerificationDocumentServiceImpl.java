package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.VerificationDocumentRepository;
import com.pml.identity.service.VerificationDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

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
    private final StreamBridge streamBridge;

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
                                        .uploadedAt(Instant.now())
                                        .build();

                                return documentRepository.save(document);
                            })
                            .doOnSuccess(saved -> {
                                log.info("Document uploaded: {} for organization: {}", saved.getId(), organizationId);
                                checkAndUpdateOrganizationStatus(organizationId);
                            });
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
                    document.setVerifiedAt(Instant.now());
                    document.setVerifiedById(verifiedById);
                    document.setRejectionReason(null);

                    return documentRepository.save(document)
                            .doOnSuccess(approved -> {
                                log.info("Document approved: {}", approved.getId());
                                checkAndUpdateOrganizationStatus(document.getOrganizationId());
                                sendDocumentApprovedNotification(approved);
                            });
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
                    document.setVerifiedAt(Instant.now());
                    document.setVerifiedById(rejectedById);
                    document.setRejectionReason(reason);

                    return documentRepository.save(document)
                            .doOnSuccess(rejected -> {
                                log.info("Document rejected: {}", rejected.getId());
                                sendDocumentRejectedNotification(rejected);
                            });
                });
    }

    @Override
    public Mono<Void> delete(String documentId) {
        log.info("Deleting document: {}", documentId);
        return documentRepository.deleteById(documentId);
    }

    @Override
    public Mono<Void> deleteByOrganization(String organizationId) {
        log.info("Deleting all documents for organization: {}", organizationId);
        return documentRepository.deleteByOrganizationId(organizationId);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if all required documents are approved and update organization status
     */
    private void checkAndUpdateOrganizationStatus(String organizationId) {
        countApprovedByOrganization(organizationId)
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
                .subscribe(
                        result -> log.info("Updated organization documents verification status"),
                        error -> log.warn("Failed to update organization status: {}", error.getMessage())
                );
    }

    private void sendDocumentApprovedNotification(VerificationDocument document) {
        try {
            record DocumentApprovedEvent(
                    String documentId,
                    String organizationId,
                    String documentType
            ) {}

            DocumentApprovedEvent event = new DocumentApprovedEvent(
                    document.getId(),
                    document.getOrganizationId(),
                    document.getDocumentType()
            );

            streamBridge.send("notificationOutput-out-0", event);
            log.info("Sent document approved notification for: {}", document.getId());
        } catch (Exception e) {
            log.error("Failed to send document approved notification: {}", e.getMessage());
        }
    }

    private void sendDocumentRejectedNotification(VerificationDocument document) {
        try {
            record DocumentRejectedEvent(
                    String documentId,
                    String organizationId,
                    String documentType,
                    String reason
            ) {}

            DocumentRejectedEvent event = new DocumentRejectedEvent(
                    document.getId(),
                    document.getOrganizationId(),
                    document.getDocumentType(),
                    document.getRejectionReason()
            );

            streamBridge.send("notificationOutput-out-0", event);
            log.info("Sent document rejected notification for: {}", document.getId());
        } catch (Exception e) {
            log.error("Failed to send document rejected notification: {}", e.getMessage());
        }
    }
}
