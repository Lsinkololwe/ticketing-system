package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OwnershipTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * GraphQL Query Resolver for Ownership Transfer operations.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OwnershipTransferQueryResolver {

    private final OwnershipTransferService transferService;
    private final OrganizationMemberService memberService;

    /**
     * Get ownership transfer by ID.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> ownershipTransfer(@InputArgument String id) {
        log.debug("GraphQL query: ownershipTransfer(id={})", id);
        return transferService.findById(id);
    }

    /**
     * Get ownership transfer by token (for acceptance page).
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> ownershipTransferByToken(@InputArgument String token) {
        log.debug("GraphQL query: ownershipTransferByToken");
        return transferService.findByToken(token);
    }

    /**
     * Get pending transfer for organization.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> pendingOwnershipTransfer(
            @InputArgument String organizationId,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.empty();
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: pendingOwnershipTransfer(orgId={}, userId={})", organizationId, userId);

        // Only owner or admin can view pending transfers
        return memberService.hasPermission(userId, organizationId, "TRANSFER_OWNERSHIP")
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        // Check if user is the new owner
                        return transferService.findPendingByOrganization(organizationId)
                                .filter(t -> t.getNewOwnerId().equals(userId));
                    }
                    return transferService.findPendingByOrganization(organizationId);
                });
    }

    /**
     * Get all transfers for an organization.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<OwnershipTransferRequest> ownershipTransfers(
            @InputArgument String organizationId,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Flux.empty();
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: ownershipTransfers(orgId={})", organizationId);

        return memberService.hasPermission(userId, organizationId, "TRANSFER_OWNERSHIP")
                .flatMapMany(hasPermission -> {
                    if (!hasPermission) {
                        return Flux.error(new IllegalStateException("Permission denied"));
                    }
                    return transferService.findByOrganization(organizationId);
                });
    }

    /**
     * Get my pending ownership transfer requests (where I'm the new owner).
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<OwnershipTransferRequest> myPendingOwnershipTransfers(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Flux.empty();
        }

        String userId = jwt.getSubject();
        log.debug("GraphQL query: myPendingOwnershipTransfers(userId={})", userId);

        return transferService.findPendingByNewOwner(userId);
    }

    /**
     * Check if organization has pending transfer.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> hasPendingOwnershipTransfer(@InputArgument String organizationId) {
        log.debug("GraphQL query: hasPendingOwnershipTransfer(orgId={})", organizationId);
        return transferService.hasPendingTransfer(organizationId);
    }
}
