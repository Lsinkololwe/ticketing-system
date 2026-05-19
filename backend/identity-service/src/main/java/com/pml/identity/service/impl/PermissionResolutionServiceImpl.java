package com.pml.identity.service.impl;

import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.EventRole;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.PermissionResolutionService;
import com.pml.shared.constants.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

/**
 * Permission Resolution Service Implementation
 *
 * Resolves effective permissions for users based on their roles and access grants.
 *
 * RESOLUTION ORDER:
 * 1. Platform role (super admin overrides all)
 * 2. Event-level access (if checking event permission)
 * 3. Organization role
 * 4. Custom permissions
 * 5. Denied permissions (explicit deny)
 * 6. Default: deny
 *
 * PERMISSION NAMING CONVENTION:
 * - Organization: ORG_* (e.g., ORG_VIEW_MEMBERS, ORG_MANAGE_SETTINGS)
 * - Event: EVENT_* (e.g., EVENT_EDIT, EVENT_DELETE, EVENT_VIEW_ANALYTICS)
 * - Financial: FIN_* (e.g., FIN_VIEW_REVENUE, FIN_REQUEST_PAYOUT)
 * - Member: MEMBER_* (e.g., MEMBER_INVITE, MEMBER_REMOVE)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionResolutionServiceImpl implements PermissionResolutionService {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final EventAccessGrantRepository eventAccessRepository;

    // ========================================================================
    // ORGANIZATION PERMISSION CHECKS
    // ========================================================================

    @Override
    public Mono<Boolean> hasOrganizationPermission(String userId, String organizationId, String permission) {
        // First check if user is a platform admin
        return isPlatformAdmin(userId)
                .flatMap(isAdmin -> {
                    if (isAdmin) {
                        return Mono.just(true);
                    }

                    // Check organization membership
                    return memberRepository.findByUserIdAndOrganizationId(userId, organizationId)
                            .map(member -> {
                                if (!member.isActive()) {
                                    return false;
                                }

                                // Check denied permissions first
                                if (member.getDeniedPermissions() != null &&
                                        member.getDeniedPermissions().contains(permission)) {
                                    return false;
                                }

                                // Check custom permissions
                                if (member.getCustomPermissions() != null &&
                                        member.getCustomPermissions().contains(permission)) {
                                    return true;
                                }

                                // Check role-based permissions
                                return getOrganizationRolePermissions(member.getRole()).contains(permission);
                            })
                            .defaultIfEmpty(false);
                });
    }

    @Override
    public Mono<Set<String>> getEffectiveOrganizationPermissions(String userId, String organizationId) {
        return isPlatformAdmin(userId)
                .flatMap(isAdmin -> {
                    if (isAdmin) {
                        return Mono.just(getAllPermissions());
                    }

                    return memberRepository.findByUserIdAndOrganizationId(userId, organizationId)
                            .map(member -> {
                                if (!member.isActive()) {
                                    return Set.<String>of();
                                }

                                Set<String> permissions = new HashSet<>(getOrganizationRolePermissions(member.getRole()));

                                // Add custom permissions
                                if (member.getCustomPermissions() != null) {
                                    permissions.addAll(member.getCustomPermissions());
                                }

                                // Remove denied permissions
                                if (member.getDeniedPermissions() != null) {
                                    permissions.removeAll(member.getDeniedPermissions());
                                }

                                return permissions;
                            })
                            .defaultIfEmpty(Set.of());
                });
    }

    @Override
    public Mono<OrganizationRole> getOrganizationRole(String userId, String organizationId) {
        return memberRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .filter(OrganizationMember::isActive)
                .map(OrganizationMember::getRole);
    }

    // ========================================================================
    // EVENT PERMISSION CHECKS
    // ========================================================================

    @Override
    public Mono<Boolean> hasEventPermission(String userId, String eventId, String organizationId, String permission) {
        // First check platform admin
        return isPlatformAdmin(userId)
                .flatMap(isAdmin -> {
                    if (isAdmin) {
                        return Mono.just(true);
                    }

                    // Check event-level access first (overrides organization)
                    return eventAccessRepository.findByUserIdAndEventId(userId, eventId)
                            .filter(EventAccessGrant::isValid)
                            .map(grant -> {
                                // Check custom permissions
                                if (grant.getCustomPermissions() != null &&
                                        grant.getCustomPermissions().contains(permission)) {
                                    return true;
                                }
                                // Check event role permissions
                                return getEventRolePermissions(grant.getEventRole()).contains(permission);
                            })
                            .switchIfEmpty(
                                    // Fall back to organization permissions
                                    hasOrganizationPermission(userId, organizationId, permission)
                            );
                });
    }

    @Override
    public Mono<Set<String>> getEffectiveEventPermissions(String userId, String eventId, String organizationId) {
        return isPlatformAdmin(userId)
                .flatMap(isAdmin -> {
                    if (isAdmin) {
                        return Mono.just(getAllPermissions());
                    }

                    // Check event-level access first
                    return eventAccessRepository.findByUserIdAndEventId(userId, eventId)
                            .filter(EventAccessGrant::isValid)
                            .map(grant -> {
                                Set<String> permissions = new HashSet<>(getEventRolePermissions(grant.getEventRole()));
                                if (grant.getCustomPermissions() != null) {
                                    permissions.addAll(grant.getCustomPermissions());
                                }
                                return permissions;
                            })
                            .switchIfEmpty(
                                    // Fall back to organization permissions
                                    getEffectiveOrganizationPermissions(userId, organizationId)
                            );
                });
    }

    @Override
    public Mono<EventRole> getEventRole(String userId, String eventId) {
        return eventAccessRepository.findByUserIdAndEventId(userId, eventId)
                .filter(EventAccessGrant::isValid)
                .map(EventAccessGrant::getEventRole);
    }

    // ========================================================================
    // PLATFORM PERMISSION CHECKS
    // ========================================================================

    @Override
    public Mono<Boolean> hasPlatformPermission(String userId, String permission) {
        return isPlatformAdmin(userId);
    }

    @Override
    public Mono<Boolean> isPlatformAdmin(String userId) {
        return userRepository.findById(userId)
                .map(user -> user.getUserType() == UserType.ADMIN)
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Boolean> isSuperAdmin(String userId) {
        // For now, same as platform admin. Can be extended for super admin role
        return isPlatformAdmin(userId);
    }

    // ========================================================================
    // COMBINED PERMISSION CHECKS
    // ========================================================================

    @Override
    public Mono<EffectivePermissions> getEffectivePermissions(String userId, String organizationId, String eventId) {
        return isPlatformAdmin(userId)
                .flatMap(isAdmin -> {
                    if (isAdmin) {
                        return Mono.just(new EffectivePermissions(
                                userId,
                                organizationId,
                                eventId,
                                getAllPermissions(),
                                null,
                                null,
                                "PLATFORM"
                        ));
                    }

                    // If eventId is provided, check event-level first
                    if (eventId != null && !eventId.isBlank()) {
                        return eventAccessRepository.findByUserIdAndEventId(userId, eventId)
                                .filter(EventAccessGrant::isValid)
                                .flatMap(grant -> {
                                    Set<String> permissions = new HashSet<>(getEventRolePermissions(grant.getEventRole()));
                                    if (grant.getCustomPermissions() != null) {
                                        permissions.addAll(grant.getCustomPermissions());
                                    }

                                    return getOrganizationRole(userId, organizationId)
                                            .map(orgRole -> new EffectivePermissions(
                                                    userId,
                                                    organizationId,
                                                    eventId,
                                                    permissions,
                                                    orgRole,
                                                    grant.getEventRole(),
                                                    "EVENT"
                                            ))
                                            .defaultIfEmpty(new EffectivePermissions(
                                                    userId,
                                                    organizationId,
                                                    eventId,
                                                    permissions,
                                                    null,
                                                    grant.getEventRole(),
                                                    "EVENT"
                                            ));
                                })
                                .switchIfEmpty(getOrganizationLevelPermissions(userId, organizationId, eventId));
                    }

                    // Organization level only
                    return getOrganizationLevelPermissions(userId, organizationId, eventId);
                });
    }

    private Mono<EffectivePermissions> getOrganizationLevelPermissions(String userId, String organizationId, String eventId) {
        return memberRepository.findByUserIdAndOrganizationId(userId, organizationId)
                .filter(OrganizationMember::isActive)
                .map(member -> {
                    Set<String> permissions = new HashSet<>(getOrganizationRolePermissions(member.getRole()));
                    if (member.getCustomPermissions() != null) {
                        permissions.addAll(member.getCustomPermissions());
                    }
                    if (member.getDeniedPermissions() != null) {
                        permissions.removeAll(member.getDeniedPermissions());
                    }

                    return new EffectivePermissions(
                            userId,
                            organizationId,
                            eventId,
                            permissions,
                            member.getRole(),
                            null,
                            "ORGANIZATION"
                    );
                })
                .defaultIfEmpty(new EffectivePermissions(
                        userId,
                        organizationId,
                        eventId,
                        Set.of(),
                        null,
                        null,
                        "NONE"
                ));
    }

    // ========================================================================
    // ROLE PERMISSION MAPPING
    // ========================================================================

    @Override
    public Set<String> getOrganizationRolePermissions(OrganizationRole role) {
        if (role == null) {
            return Set.of();
        }

        return switch (role) {
            case OWNER -> Set.of(
                    // All permissions
                    "ORG_VIEW", "ORG_EDIT", "ORG_DELETE", "ORG_MANAGE_SETTINGS",
                    "ORG_VIEW_MEMBERS", "ORG_MANAGE_MEMBERS",
                    "MEMBER_INVITE", "MEMBER_REMOVE", "MEMBER_EDIT_ROLE",
                    "EVENT_CREATE", "EVENT_VIEW", "EVENT_EDIT", "EVENT_DELETE", "EVENT_PUBLISH",
                    "EVENT_VIEW_ANALYTICS", "EVENT_VIEW_ATTENDEES", "EVENT_MANAGE_CHECK_IN",
                    "FIN_VIEW_REVENUE", "FIN_VIEW_TRANSACTIONS", "FIN_REQUEST_PAYOUT", "FIN_MANAGE_PAYOUT",
                    "TRANSFER_OWNERSHIP"
            );
            case ADMIN -> Set.of(
                    // Most permissions except ownership transfer
                    "ORG_VIEW", "ORG_EDIT", "ORG_MANAGE_SETTINGS",
                    "ORG_VIEW_MEMBERS", "ORG_MANAGE_MEMBERS",
                    "MEMBER_INVITE", "MEMBER_REMOVE", "MEMBER_EDIT_ROLE",
                    "EVENT_CREATE", "EVENT_VIEW", "EVENT_EDIT", "EVENT_DELETE", "EVENT_PUBLISH",
                    "EVENT_VIEW_ANALYTICS", "EVENT_VIEW_ATTENDEES", "EVENT_MANAGE_CHECK_IN",
                    "FIN_VIEW_REVENUE", "FIN_VIEW_TRANSACTIONS", "FIN_REQUEST_PAYOUT"
            );
            case MANAGER -> Set.of(
                    // Event management without financial
                    "ORG_VIEW",
                    "ORG_VIEW_MEMBERS",
                    "MEMBER_INVITE",
                    "EVENT_CREATE", "EVENT_VIEW", "EVENT_EDIT", "EVENT_PUBLISH",
                    "EVENT_VIEW_ANALYTICS", "EVENT_VIEW_ATTENDEES", "EVENT_MANAGE_CHECK_IN"
            );
            case MARKETER -> Set.of(
                    // Marketing focused
                    "ORG_VIEW",
                    "EVENT_VIEW", "EVENT_EDIT",
                    "EVENT_VIEW_ANALYTICS"
            );
            case CONTRIBUTOR -> Set.of(
                    // Limited event access
                    "ORG_VIEW",
                    "EVENT_VIEW"
            );
        };
    }

    @Override
    public Set<String> getEventRolePermissions(EventRole role) {
        if (role == null) {
            return Set.of();
        }

        return switch (role) {
            case EVENT_OWNER -> Set.of(
                    // Full event control
                    "EVENT_VIEW", "EVENT_EDIT", "EVENT_DELETE", "EVENT_PUBLISH",
                    "EVENT_VIEW_ANALYTICS", "EVENT_VIEW_ATTENDEES", "EVENT_MANAGE_CHECK_IN",
                    "EVENT_MANAGE_ACCESS", "EVENT_MANAGE_TICKETS"
            );
            case EVENT_ADMIN -> Set.of(
                    // Almost all event permissions
                    "EVENT_VIEW", "EVENT_EDIT", "EVENT_PUBLISH",
                    "EVENT_VIEW_ANALYTICS", "EVENT_VIEW_ATTENDEES", "EVENT_MANAGE_CHECK_IN",
                    "EVENT_MANAGE_ACCESS"
            );
            case EDITOR -> Set.of(
                    // Content editing
                    "EVENT_VIEW", "EVENT_EDIT"
            );
            case CHECK_IN -> Set.of(
                    // Check-in only
                    "EVENT_VIEW", "EVENT_VIEW_ATTENDEES", "EVENT_MANAGE_CHECK_IN"
            );
            case VIEWER -> Set.of(
                    // View only
                    "EVENT_VIEW"
            );
        };
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Get all possible permissions (for platform admins)
     */
    private Set<String> getAllPermissions() {
        Set<String> all = new HashSet<>();
        all.addAll(getOrganizationRolePermissions(OrganizationRole.OWNER));
        all.addAll(getEventRolePermissions(EventRole.EVENT_OWNER));
        all.add("PLATFORM_ADMIN");
        all.add("PLATFORM_MANAGE_USERS");
        all.add("PLATFORM_MANAGE_ORGANIZATIONS");
        all.add("PLATFORM_VIEW_ALL_ANALYTICS");
        return all;
    }
}
