package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.DocumentStatus;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.VerificationDocumentService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final OrganizationService organizationService;

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
            @InputArgument DocumentStatus status) {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myVerificationDocuments(userId={}, status={})", userId, status))
                .flatMapMany(userId -> organizationService.findByOwnerId(userId)
                        .flatMapMany(organization -> {
                            if (status != null) {
                                return documentService.findByOrganizationAndStatus(organization.getId(), status);
                            }
                            return documentService.findByOrganization(organization.getId());
                        }));
    }

    /**
     * Get documents for organization (admin only).
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Flux<VerificationDocument> verificationDocuments(
            @InputArgument String organizationId,
            @InputArgument DocumentStatus status) {
        log.debug("GraphQL query: verificationDocuments(organizationId={}, status={})", organizationId, status);

        if (status != null) {
            return documentService.findByOrganizationAndStatus(organizationId, status);
        }
        return documentService.findByOrganization(organizationId);
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
            @InputArgument String documentType) {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myVerificationDocumentByType(userId={}, type={})", userId, documentType))
                .flatMap(userId -> organizationService.findByOwnerId(userId)
                        .flatMap(organization -> documentService.findByOrganizationAndType(organization.getId(), documentType)));
    }

    /**
     * Count my documents.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<Long> myVerificationDocumentCount() {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myVerificationDocumentCount(userId={})", userId))
                .flatMap(userId -> organizationService.findByOwnerId(userId)
                        .flatMap(organization -> documentService.countByOrganization(organization.getId())))
                .defaultIfEmpty(0L);
    }

    /**
     * Count my approved documents.
     */
    @DgsQuery
    @PreAuthorize("hasRole('ORGANIZER')")
    public Mono<Long> myApprovedDocumentCount() {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myApprovedDocumentCount(userId={})", userId))
                .flatMap(userId -> organizationService.findByOwnerId(userId)
                        .flatMap(organization -> documentService.countApprovedByOrganization(organization.getId())))
                .defaultIfEmpty(0L);
    }
}
