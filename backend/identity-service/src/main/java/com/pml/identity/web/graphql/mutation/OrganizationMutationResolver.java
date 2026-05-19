package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationInput;
import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationSettingsInput;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.domain.enums.OrganizationStatus;
import com.pml.identity.service.OrganizationMemberService;
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
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class OrganizationMutationResolver {

    private final OrganizationService organizationService;
    private final OrganizationMemberService memberService;

    private static final String ORG_EDIT_PERMISSION = "ORG_EDIT";
    private static final String ORG_MANAGE_SETTINGS_PERMISSION = "ORG_MANAGE_SETTINGS";

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
                                // notifyOnTicketSale is handled at event level, not organization
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
