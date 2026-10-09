package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.organization.OrganizationApplicationInput;
import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationInput;
import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationSettingsInput;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingProcess;
import com.pml.shared.security.Permission;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/**
 * GraphQL Mutation Resolver for Organization operations.
 *
 * APPROVAL-BASED ONBOARDING:
 * ==========================
 * 1. User applies → Organization created (DRAFT)
 * 2. User fills details and submits → PENDING_REVIEW
 * 3. Admin approves, rejects or requests changes → ACTIVE/REJECTED/CHANGES_REQUESTED
 * 4. User can create draft events during approval process
 */
@Slf4j


@DgsComponent
@Validated
@RequiredArgsConstructor
public class OrganizationMutationResolver {

    private final OrganizationService organizationService;
    private final OrganizationOnboardingService onboardingService;
    private final OrganizationMemberService memberService;
    private final com.pml.identity.service.OrganizationAdminService adminService;

    /** Submission and the three review decisions run in the organization's workflow. */
    private final OrganizerOnboardingProcess onboardingProcess;

    // =========================================================================
    // ONBOARDING MUTATIONS (User)
    // =========================================================================

    /**
     * Apply to become an organizer.
     * Creates a new organization in DRAFT status.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("organizer.apply")
    public Mono<Organization> applyToBeOrganizer(
            @Valid @InputArgument OrganizationApplicationInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} applying to become organizer", userId))
                .flatMap(userId -> onboardingService.applyToBeOrganizer(userId, input));
    }

    /**
     * Update organization application details.
     * Only allowed when status is DRAFT or CHANGES_REQUESTED.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("organizer.updateApplication")
    public Mono<Organization> updateOrganizationApplication(
            @InputArgument String id,
            @Valid @InputArgument OrganizationApplicationInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} updating organization application: {}", userId, id))
                .flatMap(userId -> organizationService.findById(id)
                        .flatMap(org -> {
                            if (!org.getOwnerId().equals(userId)) {
                                return Mono.error(new IllegalStateException("Only the owner can update the application"));
                            }
                            return onboardingService.updateApplication(id, input);
                        }));
    }

    /**
     * Submit organization application for admin review.
     * Changes status from DRAFT/CHANGES_REQUESTED to PENDING_REVIEW.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("organizer.submitForReview")
    public Mono<Organization> submitOrganizationForReview(
            @InputArgument String id) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} submitting organization {} for review", userId, id))
                .flatMap(userId -> organizationService.findById(id)
                        .flatMap(org -> {
                            if (!org.getOwnerId().equals(userId)) {
                                return Mono.error(new IllegalStateException("Only the owner can submit for review"));
                            }
                            return onboardingProcess.submit(id, userId);
                        }));
    }

    /**
     * Get or create organization for the current user.
     * If user has no organization, creates one in DRAFT status.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("organizer.getOrCreateOrganization")
    public Mono<Organization> getOrCreateMyOrganization() {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} requesting organization (create if needed)", userId))
                .flatMap(onboardingService::getOrCreateOrganization);
    }

    /**
     * Upgrade an individual organization to a business organization.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("organizer.upgradeToBusiness")
    public Mono<Organization> upgradeToBusinessOrganization(
            @InputArgument String organizationId,
            @InputArgument String businessName) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} upgrading organization {} to business: {}", userId, organizationId, businessName))
                .flatMap(userId -> organizationService.findById(organizationId)
                        .flatMap(org -> {
                            if (!org.getOwnerId().equals(userId)) {
                                return Mono.error(new IllegalStateException("Only the owner can upgrade the organization"));
                            }
                            return onboardingService.upgradeToBusinessOrganization(organizationId, businessName);
                        }));
    }

    // =========================================================================
    // ADMIN MUTATIONS (Approval Workflow)
    // =========================================================================

    /**
     * Approve an organization application (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.approveOrganization")
    public Mono<Organization> approveOrganization(
            @InputArgument String id,
            @InputArgument Double commissionRate) {
        // The rate is validated before anything starts: a refused rate must not leave a
        // half-approved organization behind. It is written first so the organization never sells
        // at the platform default for the moment between approval and the override.
        Mono<Void> rate = commissionRate == null ? Mono.empty()
                : Mono.fromRunnable(() -> com.pml.identity.service.OrganizationRules.fractionOf(commissionRate))
                        .then(SecurityContextUtils.getCurrentUserId().defaultIfEmpty("system")
                                .flatMap(adminId -> adminService.setCommissionRate(
                                        id, commissionRate, "set at approval", adminId)))
                        .then();
        return SecurityContextUtils.getCurrentUserId()
                .defaultIfEmpty("system")
                .doOnNext(adminId -> log.info("Admin {} approving organization: {}", adminId, id))
                .flatMap(adminId -> rate.then(Mono.defer(() -> onboardingProcess.approve(id, adminId))));
    }

    /**
     * Request changes to an organization application (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.requestOrganizationChanges")
    public Mono<Organization> requestOrganizationChanges(
            @InputArgument String id,
            @InputArgument String reason) {
        return SecurityContextUtils.getCurrentUserId()
                .defaultIfEmpty("system")
                .doOnNext(adminId -> log.info("Admin {} requesting changes for organization {}: {}", adminId, id, reason))
                .flatMap(adminId -> onboardingProcess.requestChanges(id, reason, adminId));
    }

    /**
     * Reject an organization application (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.rejectOrganization")
    public Mono<Organization> rejectOrganization(
            @InputArgument String id,
            @InputArgument String reason) {
        return SecurityContextUtils.getCurrentUserId()
                .defaultIfEmpty("system")
                .doOnNext(adminId -> log.info("Admin {} rejecting organization {}: {}", adminId, id, reason))
                .flatMap(adminId -> onboardingProcess.reject(id, reason, adminId));
    }

    // =========================================================================
    // ORGANIZATION MANAGEMENT
    // =========================================================================

    /**
     * Update the organization's name, description and images. Requires {@code organization:edit}.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("organizer.updateOrganization")
    public Mono<Organization> updateOrganization(
            @InputArgument String id,
            @Valid @InputArgument UpdateOrganizationInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} updating organization: {}", userId, id))
                .flatMap(userId -> memberService.requirePermission(userId, id, Permission.ORGANIZATION_EDIT)
                        .then(Mono.defer(() -> adminService.updateProfile(id, input))));
    }

    /**
     * Update the organization's settings. Requires {@code organization:edit}; changing either of
     * the two money switches — managers seeing financial figures, admins requesting payouts —
     * also requires {@code organization:billing}, which only the owner holds, so an admin cannot
     * widen their own access.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    @FailClosedOnRevocation("organizer.updateOrganizationSettings")
    public Mono<Organization> updateOrganizationSettings(
            @InputArgument String id,
            @Valid @InputArgument UpdateOrganizationSettingsInput input) {
        boolean changesMoneySwitches = input.managersCanViewFinancials() != null || input.adminsCanRequestPayouts() != null;
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("User {} updating organization settings: {}", userId, id))
                .flatMap(userId -> memberService.requirePermission(userId, id, Permission.ORGANIZATION_EDIT)
                        .then(Mono.defer(() -> changesMoneySwitches
                                ? memberService.requirePermission(userId, id, Permission.ORGANIZATION_BILLING)
                                : Mono.empty()))
                        .then(Mono.defer(() -> organizationService.findById(id)))
                        .flatMap(org -> organizationService.updateSettings(id, applied(org.getSettings(), input))));
    }

    /** {@code current} with every non-null field of {@code input} written over it. */
    static OrganizationSettings applied(OrganizationSettings current, UpdateOrganizationSettingsInput input) {
        OrganizationSettings settings = current != null ? current : new OrganizationSettings();
        if (input.defaultEventVisibility() != null) settings.setDefaultEventVisibility(input.defaultEventVisibility());
        if (input.requireEventApproval() != null) settings.setRequireEventApproval(input.requireEventApproval());
        if (input.allowMembersToInvite() != null) settings.setAllowMembersToInvite(input.allowMembersToInvite());
        if (input.inviteRequiresApproval() != null) settings.setInviteRequiresApproval(input.inviteRequiresApproval());
        if (input.maxTeamMembers() != null) settings.setMaxTeamMembers(input.maxTeamMembers());
        if (input.managersCanViewFinancials() != null) settings.setManagersCanViewFinancials(input.managersCanViewFinancials());
        if (input.adminsCanRequestPayouts() != null) settings.setAdminsCanRequestPayouts(input.adminsCanRequestPayouts());
        if (input.notifyOwnerOnMemberJoin() != null) settings.setNotifyOwnerOnMemberJoin(input.notifyOwnerOnMemberJoin());
        if (input.notifyOwnerOnEventCreated() != null) settings.setNotifyOwnerOnEventCreated(input.notifyOwnerOnEventCreated());
        if (input.notifyOwnerOnPayoutRequest() != null) settings.setNotifyOwnerOnPayoutRequest(input.notifyOwnerOnPayoutRequest());
        return settings;
    }

    /**
     * Suspend organization (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.suspendOrganization")
    public Mono<Organization> suspendOrganization(
            @InputArgument String id,
            @InputArgument String reason) {
        if (reason == null || reason.isBlank()) {
            return Mono.error(new IllegalArgumentException("Suspension reason is required"));
        }

        return SecurityContextUtils.getCurrentUserId()
                .defaultIfEmpty("system")
                .doOnNext(adminId -> log.info("Admin {} suspending organization: {} - Reason: {}", adminId, id, reason))
                .flatMap(adminId -> organizationService.suspend(id, reason)
                        .flatMap(suspended -> adminService.auditSuspension(id, reason, adminId, true)
                                .thenReturn(suspended)));
    }

    /**
     * Unsuspend organization (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.unsuspendOrganization")
    public Mono<Organization> unsuspendOrganization(
            @InputArgument String id) {
        return SecurityContextUtils.getCurrentUserId()
                .defaultIfEmpty("system")
                .doOnNext(adminId -> log.info("Admin {} unsuspending organization: {}", adminId, id))
                .flatMap(adminId -> organizationService.unsuspend(id)
                        .flatMap(restored -> adminService.auditSuspension(id, null, adminId, false)
                                .thenReturn(restored)));
    }

    /**
     * Update organization status (admin only).
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @FailClosedOnRevocation("admin.updateOrganizationStatus")
    public Mono<Organization> updateOrganizationStatus(
            @InputArgument String id,
            @InputArgument OrganizationStatus status) {
        return SecurityContextUtils.getCurrentUserId()
                .defaultIfEmpty("system")
                .doOnNext(adminId -> log.info("Admin {} updating organization {} status to: {}", adminId, id, status))
                .flatMap(adminId -> organizationService.updateStatus(id, status));
    }
}
