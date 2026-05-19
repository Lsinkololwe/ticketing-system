package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.repository.OrganizerProfileRepository;
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
 * Manages KYB verification documents for organizers including:
 * - Document upload
 * - Admin approval/rejection
 * - Status tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationDocumentServiceImpl implements VerificationDocumentService {

    private final VerificationDocumentRepository documentRepository;
    private final OrganizerProfileRepository organizerProfileRepository;
    private final StreamBridge streamBridge;

    // ========================================================================
    // READ OPERATIONS
    // ========================================================================

    @Override
    public Mono<VerificationDocument> findById(String id) {
        return documentRepository.findById(id);
    }

    @Override
    public Flux<VerificationDocument> findByOrganizerProfile(String organizerProfileId) {
        return documentRepository.findByOrganizerProfileId(organizerProfileId);
    }

    @Override
    public Flux<VerificationDocument> findByOrganizerProfileAndStatus(
            String organizerProfileId,
            DocumentStatus status) {
        return documentRepository.findByOrganizerProfileIdAndStatus(organizerProfileId, status);
    }

    @Override
    public Mono<VerificationDocument> findByOrganizerProfileAndType(
            String organizerProfileId,
            String documentType) {
        return documentRepository.findByOrganizerProfileIdAndDocumentType(organizerProfileId, documentType);
    }

    @Override
    public Mono<Boolean> existsByOrganizerProfileAndType(String organizerProfileId, String documentType) {
        return documentRepository.existsByOrganizerProfileIdAndDocumentType(organizerProfileId, documentType);
    }

    @Override
    public Mono<Long> countByOrganizerProfile(String organizerProfileId) {
        return documentRepository.countByOrganizerProfileId(organizerProfileId);
    }

    @Override
    public Mono<Long> countApprovedByOrganizerProfile(String organizerProfileId) {
        return documentRepository.countByOrganizerProfileIdAndStatus(organizerProfileId, DocumentStatus.APPROVED);
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
            String organizerProfileId,
            String documentType,
            String documentUrl,
            String fileName,
            Long fileSize,
            String mimeType) {
        log.info("Uploading document type: {} for organizer profile: {}", documentType, organizerProfileId);

        // Validate organizer profile exists
        return organizerProfileRepository.findById(organizerProfileId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Organizer profile not found: " + organizerProfileId)))
                .flatMap(profile -> {
                    // Check if document type already exists
                    return existsByOrganizerProfileAndType(organizerProfileId, documentType)
                            .flatMap(exists -> {
                                if (exists) {
                                    // Update existing document
                                    return findByOrganizerProfileAndType(organizerProfileId, documentType)
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
                                        .organizerProfileId(organizerProfileId)
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
                                log.info("Document uploaded: {} for organizer: {}", saved.getId(), organizerProfileId);
                                checkAndUpdateOrganizerStatus(organizerProfileId);
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
                                checkAndUpdateOrganizerStatus(document.getOrganizerProfileId());
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
    public Mono<Void> deleteByOrganizerProfile(String organizerProfileId) {
        log.info("Deleting all documents for organizer profile: {}", organizerProfileId);
        return documentRepository.deleteByOrganizerProfileId(organizerProfileId);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Check if all required documents are approved and update organizer profile status
     */
    private void checkAndUpdateOrganizerStatus(String organizerProfileId) {
        countApprovedByOrganizerProfile(organizerProfileId)
                .flatMap(approvedCount -> {
                    // Check if all required documents are approved (minimum 3 typically)
                    if (approvedCount >= 3) {
                        return organizerProfileRepository.findById(organizerProfileId)
                                .flatMap(profile -> {
                                    profile.setDocumentsVerified(true);
                                    return organizerProfileRepository.save(profile);
                                });
                    }
                    return Mono.empty();
                })
                .subscribe(
                        result -> log.info("Updated organizer profile documents verification status"),
                        error -> log.warn("Failed to update organizer profile status: {}", error.getMessage())
                );
    }

    private void sendDocumentApprovedNotification(VerificationDocument document) {
        try {
            record DocumentApprovedEvent(
                    String documentId,
                    String organizerProfileId,
                    String documentType
            ) {}

            DocumentApprovedEvent event = new DocumentApprovedEvent(
                    document.getId(),
                    document.getOrganizerProfileId(),
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
                    String organizerProfileId,
                    String documentType,
                    String reason
            ) {}

            DocumentRejectedEvent event = new DocumentRejectedEvent(
                    document.getId(),
                    document.getOrganizerProfileId(),
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
