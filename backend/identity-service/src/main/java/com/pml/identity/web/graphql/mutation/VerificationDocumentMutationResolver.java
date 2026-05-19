package com.pml.identity.web.graphql.mutation;

import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.service.OrganizerProfileService;
import com.pml.identity.service.VerificationDocumentService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

/**
 * GraphQL Mutation Resolver for Verification Document operations.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class VerificationDocumentMutationResolver {

    private final VerificationDocumentService documentService;
    private final OrganizerProfileService organizerProfileService;

    /**
     * Upload verification document.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<VerificationDocument> uploadVerificationDocument(
            @InputArgument String documentType,
            @InputArgument String documentUrl,
            @InputArgument String fileName,
            @InputArgument Long fileSize,
            @InputArgument String mimeType,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} uploading verification document type: {}", userId, documentType);

        return organizerProfileService.findByUserId(userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Organizer profile not found")))
                .flatMap(profile -> documentService.upload(
                        profile.getId(),
                        documentType,
                        documentUrl,
                        fileName,
                        fileSize,
                        mimeType
                ));
    }

    /**
     * Approve verification document (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<VerificationDocument> approveVerificationDocument(
            @InputArgument String documentId,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} approving document: {}", adminId, documentId);

        return documentService.approve(documentId, adminId);
    }

    /**
     * Reject verification document (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<VerificationDocument> rejectVerificationDocument(
            @InputArgument String documentId,
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} rejecting document: {} - Reason: {}", adminId, documentId, reason);

        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("Rejection reason is required"));
        }

        return documentService.reject(documentId, reason, adminId);
    }

    /**
     * Delete verification document.
     */
    @DgsMutation
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<Boolean> deleteVerificationDocument(
            @InputArgument String documentId,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} deleting document: {}", userId, documentId);

        // Verify document belongs to user's organizer profile
        return documentService.findById(documentId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Document not found")))
                .flatMap(doc -> organizerProfileService.findByUserId(userId)
                        .flatMap(profile -> {
                            if (!doc.getOrganizerProfileId().equals(profile.getId())) {
                                return Mono.error(new IllegalStateException("Document does not belong to your profile"));
                            }
                            return documentService.delete(documentId).thenReturn(true);
                        }));
    }
}
