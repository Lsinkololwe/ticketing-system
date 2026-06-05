package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.organization.OrganizationApplicationInput;
import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationInput;
import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationSettingsInput;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.identity.service.OrganizationService;
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
 * GraphQL Mutation Resolver for Organization operations.
 *
 * APPROVAL-BASED ONBOARDING:
 * ==========================
 * 1. User applies → Organization created (DRAFT)
 * 2. User fills details and submits → PENDING_REVIEW
 * 3. Admin approves/rejects → APPROVED/CHANGES_REQUESTED/REJECTED
 * 4. User can create draft events during approval process
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OrganizationMutationResolver {

    private final OrganizationService organizationService;
    private final OrganizationOnboardingService onboardingService;
    private final OrganizationMemberService memberService;

    private static final String ORG_EDIT_PERMISSION = "ORG_EDIT";
    private static final String ORG_MANAGE_SETTINGS_PERMISSION = "ORG_MANAGE_SETTINGS";

    // =========================================================================
    // ONBOARDING MUTATIONS (User)
    // =========================================================================

    /**
     * Apply to become an organizer.
     * Creates a new organization in DRAFT status.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> applyToBeOrganizer(
            @InputArgument OrganizationApplicationInput input,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} applying to become organizer", userId);

        return onboardingService.applyToBeOrganizer(userId, input);
    }

    /**
     * Update organization application details.
     * Only allowed when status is DRAFT or CHANGES_REQUESTED.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> updateOrganizationApplication(
            @InputArgument String id,
            @InputArgument OrganizationApplicationInput input,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} updating organization application: {}", userId, id);

        // Verify ownership
        return organizationService.findById(id)
                .flatMap(org -> {
                    if (!org.getOwnerId().equals(userId)) {
                        return Mono.error(new IllegalStateException("Only the owner can update the application"));
                    }
                    return onboardingService.updateApplication(id, input);
                });
    }

    /**
     * Submit organization application for admin review.
     * Changes status from DRAFT/CHANGES_REQUESTED to PENDING_REVIEW.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> submitOrganizationForReview(
            @InputArgument String id,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} submitting organization {} for review", userId, id);

        // Verify ownership
        return organizationService.findById(id)
                .flatMap(org -> {
                    if (!org.getOwnerId().equals(userId)) {
                        return Mono.error(new IllegalStateException("Only the owner can submit for review"));
                    }
                    return onboardingService.submitForReview(id);
                });
    }

    /**
     * Get or create organization for the current user.
     * If user has no organization, creates one in DRAFT status.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> getOrCreateMyOrganization(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} requesting organization (create if needed)", userId);

        return onboardingService.getOrCreateOrganization(userId);
    }

    /**
     * Upgrade an individual organization to a business organization.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> upgradeToBusinessOrganization(
            @InputArgument String organizationId,
            @InputArgument String businessName,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} upgrading organization {} to business: {}", userId, organizationId, businessName);

        return organizationService.findById(organizationId)
                .flatMap(org -> {
                    if (!org.getOwnerId().equals(userId)) {
                        return Mono.error(new IllegalStateException("Only the owner can upgrade the organization"));
                    }
                    return onboardingService.upgradeToBusinessOrganization(organizationId, businessName);
                });
    }

    // =========================================================================
    // ADMIN MUTATIONS (Approval Workflow)
    // =========================================================================

    /**
     * Approve an organization application (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Organization> approveOrganization(
            @InputArgument String id,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} approving organization: {}", adminId, id);

        return onboardingService.approve(id, adminId);
    }

    /**
     * Request changes to an organization application (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Organization> requestOrganizationChanges(
            @InputArgument String id,
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} requesting changes for organization {}: {}", adminId, id, reason);

        return onboardingService.requestChanges(id, reason, adminId);
    }

    /**
     * Reject an organization application (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Organization> rejectOrganization(
            @InputArgument String id,
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} rejecting organization {}: {}", adminId, id, reason);

        return onboardingService.reject(id, reason, adminId);
    }

    // =========================================================================
    // ORGANIZATION MANAGEMENT
    // =========================================================================

    /**
     * Update organization details.
     * Requires ORG_EDIT permission.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> updateOrganization(
            @InputArgument String id,
            @InputArgument UpdateOrganizationInput input,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} updating organization: {}", userId, id);

        return memberService.hasPermission(userId, id, ORG_EDIT_PERMISSION)
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        return Mono.error(new IllegalStateException("Permission denied: " + ORG_EDIT_PERMISSION));
                    }

                    return organizationService.update(
                            id,
                            input.name(),
                            input.description(),
                            input.logoUrl(),
                            input.bannerUrl()
                    );
                });
    }

    /**
     * Update organization settings.
     * Requires ORG_MANAGE_SETTINGS permission.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Organization> updateOrganizationSettings(
            @InputArgument String id,
            @InputArgument UpdateOrganizationSettingsInput input,
            @AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return Mono.error(new IllegalStateException("Authentication required"));
        }

        String userId = jwt.getSubject();
        log.info("User {} updating organization settings: {}", userId, id);

        return memberService.hasPermission(userId, id, ORG_MANAGE_SETTINGS_PERMISSION)
                .flatMap(hasPermission -> {
                    if (!hasPermission) {
                        return Mono.error(new IllegalStateException("Permission denied: " + ORG_MANAGE_SETTINGS_PERMISSION));
                    }

                    return organizationService.findById(id)
                            .flatMap(org -> {
                                OrganizationSettings settings = org.getSettings();
                                if (settings == null) {
                                    settings = new OrganizationSettings();
                                }

                                if (input.allowMemberInvites() != null) {
                                    settings.setAllowMembersToInvite(input.allowMemberInvites());
                                }
                                if (input.requireApprovalForEvents() != null) {
                                    settings.setRequireEventApproval(input.requireApprovalForEvents());
                                }
                                if (input.notifyOnNewMember() != null) {
                                    settings.setNotifyOwnerOnMemberJoin(input.notifyOnNewMember());
                                }
                                if (input.defaultEventVisibility() != null) {
                                    settings.setDefaultEventVisibility(input.defaultEventVisibility());
                                }

                                return organizationService.updateSettings(id, settings);
                            });
                });
    }

    /**
     * Suspend organization (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Organization> suspendOrganization(
            @InputArgument String id,
            @InputArgument String reason,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} suspending organization: {} - Reason: {}", adminId, id, reason);

        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("Suspension reason is required"));
        }

        return organizationService.suspend(id, reason);
    }

    /**
     * Unsuspend organization (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Organization> unsuspendOrganization(
            @InputArgument String id,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} unsuspending organization: {}", adminId, id);

        return organizationService.unsuspend(id);
    }

    /**
     * Update organization status (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Organization> updateOrganizationStatus(
            @InputArgument String id,
            @InputArgument OrganizationStatus status,
            @AuthenticationPrincipal Jwt jwt) {
        String adminId = jwt != null ? jwt.getSubject() : "system";
        log.info("Admin {} updating organization {} status to: {}", adminId, id, status);

        return organizationService.updateStatus(id, status);
    }
}
