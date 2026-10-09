package com.pml.identity.web.graphql.mutation;

import org.springframework.validation.annotation.Validated;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.service.OrganizationAdminService;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.shared.security.Permission;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * Commission, payout-account review and deletion requests. Platform operations are restricted to
 * administrators (payout review also to FINANCE); deletion is the owner's, through the
 * {@code organization:delete} permission.
 */
@Slf4j
@DgsComponent
@Validated
@RequiredArgsConstructor
public class OrganizationAdminMutationResolver {

    private final OrganizationAdminService admin;
    private final OrganizationMemberService memberService;

    /** Sets this organization's own commission, replacing the platform default for it. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.setOrganizationCommissionRate")
    public Mono<Organization> setOrganizationCommissionRate(
            @InputArgument String organizationId,
            @InputArgument Double rate,
            @InputArgument String reason) {
        return actor().flatMap(adminId -> admin.setCommissionRate(organizationId, rate, reason, adminId));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.rejectPayoutAccount")
    public Mono<Organization> rejectPayoutAccount(@InputArgument String organizationId, @InputArgument String reason) {
        return actor().flatMap(adminId -> admin.rejectPayoutAccount(organizationId, reason, adminId));
    }

    /** The same operation under the name the admin bank-accounts table uses. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.rejectBankAccount")
    public Mono<Organization> rejectBankAccount(@InputArgument String organizationId, @InputArgument String reason) {
        return rejectPayoutAccount(organizationId, reason);
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.suspendBankAccount")
    public Mono<Organization> suspendBankAccount(@InputArgument String organizationId, @InputArgument String reason) {
        return actor().flatMap(adminId -> admin.suspendPayoutAccount(organizationId, reason, adminId));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.reinstateBankAccount")
    public Mono<Organization> reinstateBankAccount(@InputArgument String organizationId) {
        return actor().flatMap(adminId -> admin.reinstatePayoutAccount(organizationId, adminId));
    }

    /** The owner asks for the organization to be deleted; it stays recoverable for the grace period. */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("organizer.requestOrganizationDeletion")
    public Mono<Organization> requestOrganizationDeletion(
            @InputArgument String organizationId, @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> memberService.requirePermission(userId, organizationId, Permission.ORGANIZATION_DELETE)
                        .then(Mono.defer(() -> admin.requestDeletion(organizationId, userId, reason))));
    }

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("organizer.cancelOrganizationDeletion")
    public Mono<Organization> cancelOrganizationDeletion(@InputArgument String organizationId) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> memberService.requirePermission(userId, organizationId, Permission.ORGANIZATION_DELETE)
                        .then(Mono.defer(() -> admin.cancelDeletion(organizationId, userId))));
    }

    private static Mono<String> actor() {
        return SecurityContextUtils.getCurrentUserId().defaultIfEmpty("system");
    }
}
