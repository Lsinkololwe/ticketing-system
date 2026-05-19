package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.service.OrganizerProfileService;
import com.pml.identity.service.VerificationDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * GraphQL Query Resolver for Verification Document operations.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class VerificationDocumentQueryResolver {

    private final VerificationDocumentService documentService;
    private final OrganizerProfileService organizerProfileService;

    /**
     * Get document by ID.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<VerificationDocument> verificationDocument(@InputArgument String id) {
        log.debug("GraphQL query: verificationDocument(id={})", id);
        return documentService.findById(id);
    }

    /**
     * Get my verification documents (for organizer).
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Flux<VerificationDocument> myVerificationDocuments(
            @InputArgument DocumentStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Flux.empty();
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: myVerificationDocuments(userId={}, status={})", userId, status);

        return organizerProfileService.findByUserId(userId)
                .flatMapMany(profile -> {
                    if (status != null) {
                        return documentService.findByOrganizerProfileAndStatus(profile.getId(), status);
                    }
                    return documentService.findByOrganizerProfile(profile.getId());
                });
    }

    /**
     * Get documents for organizer profile (admin only).
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Flux<VerificationDocument> verificationDocuments(
            @InputArgument String organizerProfileId,
            @InputArgument DocumentStatus status) {
        log.debug("GraphQL query: verificationDocuments(profileId={}, status={})", organizerProfileId, status);

        if (status != null) {
            return documentService.findByOrganizerProfileAndStatus(organizerProfileId, status);
        }
        return documentService.findByOrganizerProfile(organizerProfileId);
    }

    /**
     * Get pending documents queue (admin only).
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Flux<VerificationDocument> pendingVerificationDocuments() {
        log.debug("GraphQL query: pendingVerificationDocuments");
        return documentService.findPendingDocuments();
    }

    /**
     * Get document by type for organizer.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<VerificationDocument> myVerificationDocumentByType(
            @InputArgument String documentType,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.empty();
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: myVerificationDocumentByType(userId={}, type={})", userId, documentType);

        return organizerProfileService.findByUserId(userId)
                .flatMap(profile -> documentService.findByOrganizerProfileAndType(profile.getId(), documentType));
    }

    /**
     * Count my documents.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<Long> myVerificationDocumentCount(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.just(0L);
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: myVerificationDocumentCount(userId={})", userId);

        return organizerProfileService.findByUserId(userId)
                .flatMap(profile -> documentService.countByOrganizerProfile(profile.getId()))
                .defaultIfEmpty(0L);
    }

    /**
     * Count my approved documents.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<Long> myApprovedDocumentCount(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.just(0L);
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: myApprovedDocumentCount(userId={})", userId);

        return organizerProfileService.findByUserId(userId)
                .flatMap(profile -> documentService.countApprovedByOrganizerProfile(profile.getId()))
                .defaultIfEmpty(0L);
    }
}
