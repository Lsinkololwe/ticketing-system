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
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

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
    // Platform Role & Status
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Platform-level user type (determines system-wide capabilities)
     */
    @Builder.Default
    private UserType userType = UserType.CUSTOMER;

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
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Check if user is an administrator.
     *
     * @return true if userType is ADMIN or SUPER_ADMIN
     */
    public boolean isAdministrator() {
        return userType != null && userType.isAdmin();
    }

    /**
     * Check if user is an event organizer.
     *
     * @return true if userType is ORGANIZER
     */
    public boolean isEventOrganizer() {
        return userType == UserType.ORGANIZER;
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
