package com.pml.identity.web.graphql.query;

import com.pml.identity.security.IdentityTenantReads;
import java.util.Objects;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.shared.constants.DocumentStatus;
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
    private final IdentityTenantReads reads;
    private final OrganizationService organizationService;

    /**
     * One verification document, readable only from the organization that owns it.
     *
     * <h2>Why the lookup is scoped</h2>
     * {@code @PreAuthorize("isAuthenticated()")} alone is not enough: an unscoped
     * {@code findById(id)} lets any account that can sign in read any organizer's KYC document by
     * id — the document type ({@code ID_DOCUMENT}, {@code BUSINESS_LICENSE}, {@code TAX_CERT}), the
     * filename, the review status and rejection reason, and {@code documentUrl}, where the file
     * itself is stored. {@code reads.documentForCaller} answers only from the caller's own
     * organizations.
     *
     * <p>Its sibling three methods below, {@code myVerificationDocuments}, scopes to the caller's
     * own organizations too. A by-id lookup is the operation most easily left unguarded by someone
     * who assumes the guard is elsewhere, so the scope lives here, next to the lookup.</p>
     *
     * <h2>Refuses as unknown, not as forbidden</h2>
     * A document belonging to another organization answers {@code DOCUMENT_UNKNOWN}, exactly as an
     * id that was never issued does. Anything finer is an oracle: a caller holding a list of
     * candidate ids would learn which are real by watching the error change.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<VerificationDocument> verificationDocument(@InputArgument String id) {
        log.debug("GraphQL query: verificationDocument(id={})", id);
        Objects.requireNonNull(id, "Document ID is required");

        return reads.documentForCaller(id);
    }

    /**
     * Get my verification documents (for organizer).
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()") // application stage: any signed-in account, own application only (ORGANIZER is granted on approval)
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
    @PreAuthorize("isAuthenticated()") // application stage: any signed-in account, own application only (ORGANIZER is granted on approval)
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
    @PreAuthorize("isAuthenticated()") // application stage: any signed-in account, own application only (ORGANIZER is granted on approval)
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
    @PreAuthorize("isAuthenticated()") // application stage: any signed-in account, own application only (ORGANIZER is granted on approval)
    public Mono<Long> myApprovedDocumentCount() {
        return SecurityContextUtils.getCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myApprovedDocumentCount(userId={})", userId))
                .flatMap(userId -> organizationService.findByOwnerId(userId)
                        .flatMap(organization -> documentService.countApprovedByOrganization(organization.getId())))
                .defaultIfEmpty(0L);
    }
}
