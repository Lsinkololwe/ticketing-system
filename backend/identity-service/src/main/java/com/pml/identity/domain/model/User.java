package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.AccountStatus;

import com.pml.identity.domain.base.Auditable;
import com.pml.identity.domain.base.Identifiable;
import com.pml.shared.constants.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * User Model
 *
 * Represents a user in the system with different types and roles.
 *
 * <h2>Architecture Notes</h2>
 * <ul>
 *   <li>The {@code id} field IS the Keycloak user ID (sub claim) - no separate keycloakUserId needed</li>
 *   <li>Keycloak is the SINGLE SOURCE OF TRUTH for authentication, account status, and roles</li>
 *   <li>This collection stores profile data CACHED from Keycloak for GraphQL performance</li>
 *   <li>Business data (companyName, taxId) belongs in OrganizerProfile, NOT here</li>
 *   <li>All timestamps use {@link Instant} for timezone-agnostic storage</li>
 * </ul>
 *
 * <h2>What Keycloak Owns</h2>
 * <ul>
 *   <li>Authentication (password, credentials)</li>
 *   <li>Account status (enabled, locked, expired)</li>
 *   <li>Brute force protection (failed login attempts, lockout)</li>
 *   <li>Email/phone verification status (synced to Keycloak attributes)</li>
 *   <li>Roles (realm roles: CUSTOMER, ORGANIZER, ADMIN, etc.)</li>
 * </ul>
 *
 * <h2>What MongoDB Owns</h2>
 * <ul>
 *   <li>User profile data (name, email, phone) - CACHED from Keycloak</li>
 *   <li>Application preferences (locale, timezone)</li>
 *   <li>Statistics (totalTicketsPurchased, totalEventsAttended)</li>
 *   <li>Application-specific metadata (lastLoginAt)</li>
 * </ul>
 *
 * @see OrganizerProfile For business-specific data (companyName, taxId, etc.)
 * @see OrganizationMember For organization membership and roles
 */
@Document(collection = "users")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class User implements Identifiable<String>, Auditable {

    /**
     * User ID - MUST match Keycloak user ID (sub claim).
     * This links the MongoDB document to the Keycloak user.
     *
     * <p>IMPORTANT: This IS the Keycloak user ID. No separate keycloakUserId field needed.</p>
     */
    @Id
    private String id;

    // ─────────────────────────────────────────────────────────────────────
    // Core Identity (synced from Keycloak)
    // ─────────────────────────────────────────────────────────────────────

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Indexed(unique = true)
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Indexed(unique = true)
    private String email;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    /**
     * Primary phone number in E.164 format (e.g., +260971234567)
     */
    private String phoneNumber;

    // ─────────────────────────────────────────────────────────────────────
    // Platform Roles & Status
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Platform-level roles (determines system-wide capabilities).
     *
     * A user can have multiple roles. The CUSTOMER role is the base role
     * that all users have and cannot be removed.
     *
     * Example combinations:
     * - [CUSTOMER] - Regular user (default)
     * - [CUSTOMER, ORGANIZER] - Event organizer
     * - [CUSTOMER, ORGANIZER, SCANNER] - Organizer who also scans tickets
     * - [CUSTOMER, ADMIN] - Platform administrator
     * - [CUSTOMER, FINANCE] - Finance team member
     */
    @NotEmpty(message = "User must have at least one role")
    @Builder.Default
    private Set<UserType> roles = EnumSet.of(UserType.CUSTOMER);

    /**
     * Account status
     */
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    // ─────────────────────────────────────────────────────────────────────
    // Verification Status (synced from Keycloak)
    // ─────────────────────────────────────────────────────────────────────

    @Builder.Default
    private boolean phoneVerified = false;

    @Builder.Default
    private boolean emailVerified = false;

    /**
     * Whether identity is verified (KYC)
     */
    @Builder.Default
    private boolean identityVerified = false;

    // ─────────────────────────────────────────────────────────────────────
    // Account Flags
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Whether account is active (application-level soft delete).
     * NOTE: Account locking/disabling is handled by Keycloak.
     */
    @Builder.Default
    private boolean active = true;

    /**
     * Whether account is locked (synced from Keycloak)
     */
    @Builder.Default
    private boolean locked = false;

    // ─────────────────────────────────────────────────────────────────────
    // Profile Information (application-specific)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Profile picture URL
     */
    private String avatarUrl;

    /**
     * User bio/description
     */
    private String bio;

    /**
     * Date of birth
     */
    private LocalDate dateOfBirth;

    /**
     * Gender
     */
    private String gender;

    /**
     * Preferred locale (e.g., "en-ZM")
     */
    private String locale;

    /**
     * Preferred timezone (e.g., "Africa/Lusaka")
     */
    private String timezone;

    // ─────────────────────────────────────────────────────────────────────
    // Two-Factor Authentication
    // ─────────────────────────────────────────────────────────────────────

    @Builder.Default
    private boolean twoFactorEnabled = false;

    private String twoFactorMethod;

    // ─────────────────────────────────────────────────────────────────────
    // Organization Link (for quick access)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Primary organization ID (set when user becomes an organizer).
     * This is for quick access; full membership data is in OrganizationMember.
     */
    @Indexed(sparse = true)
    private String primaryOrganizationId;

    // ─────────────────────────────────────────────────────────────────────
    // Statistics (computed/aggregated)
    // ─────────────────────────────────────────────────────────────────────

    @Builder.Default
    private int totalTicketsPurchased = 0;

    @Builder.Default
    private int totalEventsAttended = 0;

    // ─────────────────────────────────────────────────────────────────────
    // Timestamps (using Instant for timezone-agnostic storage)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Last login timestamp
     */
    private Instant lastLoginAt;

    /**
     * Last activity timestamp
     */
    private Instant lastActiveAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // ─────────────────────────────────────────────────────────────────────
    // Audit Fields
    // ─────────────────────────────────────────────────────────────────────

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    // ─────────────────────────────────────────────────────────────────────
    // Computed Properties
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Get user's full name.
     *
     * @return firstName + lastName
     */
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return "";
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    /**
     * Get member since date (same as createdAt).
     *
     * @return When the user registered
     */
    public Instant getMemberSince() {
        return createdAt;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Role Management Methods
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if user has a specific role.
     *
     * @param role the role to check
     * @return true if user has the role
     */
    public boolean hasRole(UserType role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Check if user has any of the specified roles.
     *
     * @param rolesToCheck the roles to check
     * @return true if user has any of the specified roles
     */
    public boolean hasAnyRole(UserType... rolesToCheck) {
        if (roles == null || rolesToCheck == null) {
            return false;
        }
        return Arrays.stream(rolesToCheck).anyMatch(roles::contains);
    }

    /**
     * Check if user has all of the specified roles.
     *
     * @param rolesToCheck the roles to check
     * @return true if user has all of the specified roles
     */
    public boolean hasAllRoles(UserType... rolesToCheck) {
        if (roles == null || rolesToCheck == null) {
            return false;
        }
        return Arrays.stream(rolesToCheck).allMatch(roles::contains);
    }

    /**
     * Add a role to the user.
     *
     * @param role the role to add
     * @return true if the role was added, false if already present or invalid
     */
    public boolean addRole(UserType role) {
        if (role == null) {
            return false;
        }
        if (roles == null) {
            roles = EnumSet.of(UserType.CUSTOMER);
        }
        if (roles.contains(role)) {
            return false;
        }
        if (!UserType.canAddRole(roles, role)) {
            return false;
        }
        roles.add(role);
        return true;
    }

    /**
     * Remove a role from the user.
     * Note: CUSTOMER role cannot be removed.
     *
     * @param role the role to remove
     * @return true if the role was removed, false if not present or cannot be removed
     */
    public boolean removeRole(UserType role) {
        if (role == null || roles == null) {
            return false;
        }
        if (!UserType.canRemoveRole(roles, role)) {
            return false;
        }
        return roles.remove(role);
    }

    /**
     * Get the highest privilege role for this user.
     *
     * @return the highest privilege role
     */
    public UserType getHighestPrivilegeRole() {
        return UserType.getHighestPrivilegeRole(roles);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if user is an administrator.
     *
     * @return true if user has ADMIN or SUPER_ADMIN role
     */
    public boolean isAdministrator() {
        return hasAnyRole(UserType.ADMIN, UserType.SUPER_ADMIN);
    }

    /**
     * Check if user is an event organizer.
     *
     * @return true if user has ORGANIZER role
     */
    public boolean isEventOrganizer() {
        return hasRole(UserType.ORGANIZER);
    }

    /**
     * Check if user can create events.
     *
     * @return true if user has ORGANIZER, ADMIN, or SUPER_ADMIN role
     */
    public boolean canCreateEvents() {
        return hasAnyRole(UserType.ORGANIZER, UserType.ADMIN, UserType.SUPER_ADMIN);
    }

    /**
     * Check if user can scan tickets.
     *
     * @return true if user has SCANNER, ORGANIZER, ADMIN, or SUPER_ADMIN role
     */
    public boolean canScanTickets() {
        return hasAnyRole(UserType.SCANNER, UserType.ORGANIZER, UserType.ADMIN, UserType.SUPER_ADMIN);
    }

    /**
     * Check if user can process payouts.
     *
     * @return true if user has FINANCE or SUPER_ADMIN role
     */
    public boolean canProcessPayouts() {
        return hasAnyRole(UserType.FINANCE, UserType.SUPER_ADMIN);
    }

    /**
     * Check if phone is verified.
     *
     * @return true if phone is verified
     */
    public boolean isPhoneVerified() {
        return phoneVerified;
    }

    /**
     * Check if email is verified.
     *
     * @return true if email is verified
     */
    public boolean isEmailVerified() {
        return emailVerified;
    }

    /**
     * Check if user is active (not soft-deleted).
     *
     * @return true if active
     */
    public boolean isActive() {
        return active;
    }

}
