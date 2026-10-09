package com.pml.identity.domain.model;

import com.pml.identity.persistence.IdentityCollections;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.shared.security.Permission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Organization Member Model
 *
 * Represents a user's membership in an organization with a specific role.
 *
 * ARCHITECTURE NOTES:
 * ==================
 * 1. Links User to Organization with a role
 * 2. Supports custom permissions that override role defaults
 * 3. Supports denied permissions that explicitly revoke access
 * 4. Synced with Keycloak groups for SSO integration
 *
 * PERMISSION RESOLUTION:
 * =====================
 * 1. Get base permissions from role
 * 2. Add custom permissions
 * 3. Remove denied permissions
 * 4. Event-level access can override (see EventAccessGrant)
 */
@Document(collection = IdentityCollections.ORGANIZATION_MEMBERS)
@TypeAlias("organization_members")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationMember {

    @Id
    private String id;

    /**
     * User ID of the member
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Organization ID
     */
    @NotBlank(message = "Organization ID is required")
    private String organizationId;

    /**
     * Role within the organization
     */
    @NotNull(message = "Role is required")
    private OrganizationRole role;

    /**
     * Permission codes added to the role's, e.g. {@code ["analytics:view"]} for a contributor
     */
    @Builder.Default
    private Set<String> customPermissions = new HashSet<>();

    /**
     * Permission codes withheld from this member even when the role carries them,
     * e.g. {@code ["event:delete"]} for an admin
     */
    @Builder.Default
    private Set<String> deniedPermissions = new HashSet<>();

    /**
     * Member status
     */
    @Builder.Default
    private MemberStatus status = MemberStatus.ACTIVE;

    /**
     * User ID of who invited this member (null for owner)
     */
    private String invitedById;

    /**
     * When the member joined
     */
    private Instant joinedAt;

    /**
     * When the member was last active
     */
    private Instant lastActiveAt;

    /**
     * When the membership became {@code REMOVED}.
     *
     * <p>Removal retains the record rather than deleting it, so this is what
     * distinguishes a membership that ended from one that never happened — and it is what a
     * re-invited member's new row is dated against.
     */
    /**
     * The Keycloak group mirror is behind this document.
     *
     * <p>Keycloak mirrors membership; it never owns it. So a group write that fails must not fail
     * the membership change — an organizer removing somebody cannot be blocked by a third party
     * being down, and that is the one operation you least want blocked. The change commits, this
     * flag is set, and the sweep repairs the mirror afterwards.
     *
     * <p>Without the flag a failed group write is a log line: the drift is real, invisible, and
     * unrepairable except by reconciling every member on the platform.
     *
     * <p>Indexed through {@code IdentityIndexInitializer} rather than by an
     * annotation here: {@code @Indexed} creates an index outside the registry, where nothing
     * checks that it exists on a live database.
     */
    private boolean mirrorPending;

    private Instant removedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * Check if member is active
     */
    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    /**
     * Check if member is the owner
     */
    public boolean isOwner() {
        return role == OrganizationRole.OWNER;
    }

    /**
     * What this member may do in an organization with {@code settings}: the role's permissions,
     * plus the member's custom permissions, minus the denied ones. A denial always wins, and a
     * stored code the catalogue does not know grants nothing. Membership status is not considered
     * here; callers check {@link #isActive()}.
     */
    public Set<Permission> permissions(OrganizationSettings settings) {
        EnumSet<Permission> effective = EnumSet.noneOf(Permission.class);
        if (role != null) {
            effective.addAll(role.permissions(settings));
        }
        effective.addAll(known(customPermissions));
        effective.removeAll(known(deniedPermissions));
        return effective;
    }

    public boolean hasPermission(Permission permission, OrganizationSettings settings) {
        return permission != null && permissions(settings).contains(permission);
    }

    private static Set<Permission> known(Set<String> codes) {
        EnumSet<Permission> known = EnumSet.noneOf(Permission.class);
        if (codes != null) {
            codes.forEach(code -> Permission.fromCode(code).ifPresent(known::add));
        }
        return known;
    }

    /**
     * Check if this member can modify another member
     */
    public boolean canModifyMember(OrganizationMember other) {
        if (!isActive()) return false;

        // Only OWNER and ADMIN can modify members
        if (role != OrganizationRole.OWNER && role != OrganizationRole.ADMIN) {
            return false;
        }

        // Cannot modify OWNER (except by OWNER themselves for ownership transfer)
        if (other.getRole() == OrganizationRole.OWNER && role != OrganizationRole.OWNER) {
            return false;
        }

        // ADMIN cannot modify other ADMINs
        if (role == OrganizationRole.ADMIN && other.getRole() == OrganizationRole.ADMIN) {
            return false;
        }

        return true;
    }
}
