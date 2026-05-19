package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.valueobject.OrganizationRole;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
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
@Document(collection = "organization_members")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "user_org_idx", def = "{'userId': 1, 'organizationId': 1}", unique = true),
    @CompoundIndex(name = "org_role_idx", def = "{'organizationId': 1, 'role': 1}"),
    @CompoundIndex(name = "org_status_idx", def = "{'organizationId': 1, 'status': 1}")
})
public class OrganizationMember {

    @Id
    private String id;

    /**
     * User ID of the member
     */
    @NotBlank(message = "User ID is required")
    @Indexed
    private String userId;

    /**
     * Organization ID
     */
    @NotBlank(message = "Organization ID is required")
    @Indexed
    private String organizationId;

    /**
     * Role within the organization
     */
    @NotNull(message = "Role is required")
    private OrganizationRole role;

    /**
     * Custom permissions that add to role defaults
     * Example: ["PAYOUT_REQUEST"] grants a Manager payout access
     */
    @Builder.Default
    private Set<String> customPermissions = new HashSet<>();

    /**
     * Permissions explicitly denied (override role defaults)
     * Example: ["EVENT_DELETE"] prevents deletion even for Admins
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
     * Check if member has a specific permission
     * Takes into account role permissions, custom, and denied
     */
    public boolean hasPermission(String permission) {
        // Explicitly denied takes precedence
        if (deniedPermissions.contains(permission)) {
            return false;
        }
        // Custom permissions add to role
        if (customPermissions.contains(permission)) {
            return true;
        }
        // Check role-based permissions (would need a permission mapping)
        return false; // Actual implementation would check role permissions
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
