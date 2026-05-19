package com.pml.shared.dto.authorization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Authorization Result
 *
 * <p>Result of an authorization check. Indicates whether the user is authorized
 * and provides context about why access was granted or denied.</p>
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizationResult {

    /**
     * Whether the user is authorized.
     */
    private boolean authorized;

    /**
     * Reason for the authorization decision.
     * Human-readable explanation.
     */
    private String reason;

    /**
     * The source of authorization.
     * Examples: "OWNER", "ORGANIZATION_MEMBER", "EVENT_ACCESS_GRANT", "ADMIN"
     */
    private String authorizationSource;

    /**
     * The role that granted access (if from membership).
     */
    private String grantingRole;

    /**
     * Organization ID that the user is authorized through.
     */
    private String organizationId;

    /**
     * Organization slug for reference.
     */
    private String organizationSlug;

    /**
     * All permissions the user has on this resource.
     * Useful for frontend to know what actions are available.
     */
    private Set<String> grantedPermissions;

    /**
     * Create a successful authorization result.
     */
    public static AuthorizationResult authorized(String reason, String source, String role) {
        return AuthorizationResult.builder()
                .authorized(true)
                .reason(reason)
                .authorizationSource(source)
                .grantingRole(role)
                .build();
    }

    /**
     * Create a successful authorization result for an owner.
     */
    public static AuthorizationResult authorizedAsOwner(String organizationId) {
        return AuthorizationResult.builder()
                .authorized(true)
                .reason("User is the owner of the organization")
                .authorizationSource("OWNER")
                .grantingRole("OWNER")
                .organizationId(organizationId)
                .build();
    }

    /**
     * Create a successful authorization result for an organization member.
     */
    public static AuthorizationResult authorizedAsMember(String organizationId, String role, Set<String> permissions) {
        return AuthorizationResult.builder()
                .authorized(true)
                .reason("User is a member of the organization with role: " + role)
                .authorizationSource("ORGANIZATION_MEMBER")
                .grantingRole(role)
                .organizationId(organizationId)
                .grantedPermissions(permissions)
                .build();
    }

    /**
     * Create a successful authorization result for event access grant.
     */
    public static AuthorizationResult authorizedByEventGrant(String eventId, String eventRole) {
        return AuthorizationResult.builder()
                .authorized(true)
                .reason("User has event access grant with role: " + eventRole)
                .authorizationSource("EVENT_ACCESS_GRANT")
                .grantingRole(eventRole)
                .build();
    }

    /**
     * Create a successful authorization result for admin.
     */
    public static AuthorizationResult authorizedAsAdmin() {
        return AuthorizationResult.builder()
                .authorized(true)
                .reason("User is a platform administrator")
                .authorizationSource("ADMIN")
                .grantingRole("ADMIN")
                .build();
    }

    /**
     * Create a denied authorization result.
     */
    public static AuthorizationResult denied(String reason) {
        return AuthorizationResult.builder()
                .authorized(false)
                .reason(reason)
                .build();
    }

    /**
     * Create a denied result for non-member.
     */
    public static AuthorizationResult deniedNotMember() {
        return denied("User is not a member of the organization");
    }

    /**
     * Create a denied result for insufficient permissions.
     */
    public static AuthorizationResult deniedInsufficientPermissions(String requiredPermission, String currentRole) {
        return AuthorizationResult.builder()
                .authorized(false)
                .reason("Insufficient permissions. Required: " + requiredPermission + ", Current role: " + currentRole)
                .grantingRole(currentRole)
                .build();
    }

    /**
     * Create a denied result for not authenticated.
     */
    public static AuthorizationResult deniedNotAuthenticated() {
        return denied("User is not authenticated");
    }
}
