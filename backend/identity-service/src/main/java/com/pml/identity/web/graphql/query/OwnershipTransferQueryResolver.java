package com.pml.identity.web.graphql.query;

import com.pml.shared.security.Permission;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.OwnershipTransferRequest;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OwnershipTransferService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

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
     * One ownership transfer, if the caller is a party to it.
     * OWASP A01:2021 · CWE-639.
     *
     * <h2>Two parties, and nobody else</h2>
     * Ownership transfer is a two-party handshake that moves control of a
     * business's money. The parties are the current owner and the named recipient; the id arrives
     * from the client, and answering on it alone tells any signed-in caller who is handing which
     * organization to whom, and when it expires.
     *
     * <p>Platform administrators read it because a transfer that stalls is a support case — one
     * party has left the company, the recipient never received the notification — and resolving
     * that means being able to see it.
     *
     * <h2>Empty rather than a refusal</h2>
     * The field is nullable and answers {@code null} for an id that does not exist. A transfer
     * that is not the caller's answers the same, so the query cannot sort real transfer ids from
     * invented ones.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<OwnershipTransferRequest> ownershipTransfer(@InputArgument String id) {
        log.debug("GraphQL query: ownershipTransfer(id={})", id);
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(authentication -> transferService.findById(id)
                        .filter(transfer -> isPartyTo(authentication, transfer)));
    }

    /** The current owner, the named recipient, or a platform administrator. */
    private static boolean isPartyTo(Authentication authentication, OwnershipTransferRequest transfer) {
        boolean administrator = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(PLATFORM_ADMINISTRATORS::contains);
        String caller = authentication.getName();
        return administrator
                || caller.equals(transfer.getCurrentOwnerId())
                || caller.equals(transfer.getNewOwnerId());
    }

    /**
     * Roles that see a transfer they are not party to.
     *
     * <p>Deliberately not {@code ROLE_ORGANIZER}: every organizer holds that role, and holding it
     * says nothing about this organization. Deliberately not {@code ROLE_FINANCE} either — a
     * transfer is a control change rather than a money movement, and finance has no part in it.
     */
    private static final Set<String> PLATFORM_ADMINISTRATORS =
            Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");

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
    public Mono<OwnershipTransferRequest> pendingOwnershipTransfer(@InputArgument String organizationId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: pendingOwnershipTransfer(orgId={}, userId={})", organizationId, userId))
                .flatMap(userId -> memberService.hasPermission(userId, organizationId, Permission.ORGANIZATION_TRANSFER)
                        .flatMap(hasPermission -> {
                            if (!hasPermission) {
                                // Check if user is the new owner
                                return transferService.findPendingByOrganization(organizationId)
                                        .filter(t -> t.getNewOwnerId().equals(userId));
                            }
                            return transferService.findPendingByOrganization(organizationId);
                        }));
    }

    /**
     * Get all transfers for an organization.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<OwnershipTransferRequest> ownershipTransfers(@InputArgument String organizationId) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: ownershipTransfers(orgId={})", organizationId))
                .flatMapMany(userId -> memberService.requirePermission(userId, organizationId, Permission.ORGANIZATION_TRANSFER)
                        .thenMany(Flux.defer(() -> {
                            return transferService.findByOrganization(organizationId);
                        })));
    }

    /**
     * Get my pending ownership transfer requests (where I'm the new owner).
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Flux<OwnershipTransferRequest> myPendingOwnershipTransfers() {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.debug("GraphQL query: myPendingOwnershipTransfers(userId={})", userId))
                .flatMapMany(userId -> transferService.findPendingByNewOwner(userId));
    }

    /**
     * Check if organization has pending transfer.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> hasPendingOwnershipTransfer(@InputArgument String organizationId) {
        log.debug("GraphQL query: hasPendingOwnershipTransfer(orgId={})", organizationId);
        // Only the organization's own team may ask; anyone else gets false, as for an organization with none.
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> memberService.hasPermission(userId, organizationId, Permission.TEAM_VIEW))
                .flatMap(isTeam -> isTeam ? transferService.hasPendingTransfer(organizationId) : Mono.just(false));
    }
}
