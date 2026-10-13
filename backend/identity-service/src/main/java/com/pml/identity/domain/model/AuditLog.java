package com.pml.identity.domain.model;

import com.pml.identity.persistence.IdentityCollections;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Audit Log for Security Events
 *
 * Implements OWASP logging guidelines for authentication and authorization events:
 * - Records all role changes and privilege escalations
 * - Captures user context (who performed the action)
 * - Includes timestamp for forensic analysis
 * - Stores outcome (success/failure) and error details
 * - Sanitizes error messages to prevent information leakage
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Logging_Vocabulary_Cheat_Sheet.html">OWASP Logging Cheat Sheet</a>
 */
@Document(collection = IdentityCollections.AUDIT_LOGS)
@TypeAlias("audit_logs")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    private String id;

    /**
     * Audit action type
     */
    private AuditAction action;

    /**
     * User ID who is the subject of the action (e.g., user whose role was changed)
     */
    private String userId;

    /**
     * User ID who performed the action (e.g., admin who granted the role)
     */
    private String performedBy;

    /**
     * Resource ID (e.g., organization ID for role grants)
     */
    private String resourceId;

    /**
     * Resource type (e.g., "Organization", "User", "Role")
     */
    private String resourceType;

    /**
     * Action status (SUCCESS, FAILURE, PENDING)
     */
    private AuditStatus status;

    /**
     * Client IP address (if available)
     */
    private String clientIp;

    /**
     * User agent string (if available)
     */
    private String userAgent;

    /**
     * Timestamp when the action occurred. For a platform-wide reach delivered by another service this
     * is the time the reach happened there, which may be earlier than {@link #receivedAt} when
     * delivery had to wait for identity.
     */
    private Instant timestamp;

    /**
     * When identity stored the row, from identity's own clock. Always set on a platform-wide reach, so
     * that a late delivery is visible as the gap between this and {@link #timestamp}, and so that the
     * time a sender claims is always read beside the time identity actually received it.
     */
    private Instant receivedAt;

    /**
     * The permanent identifier of a platform-wide reach, assigned where it happened. A second delivery
     * of the same reach carries the same value and is recognised by it; the collection holds at most
     * one row per value. Set only on a platform-wide reach.
     */
    private String recordId;

    /**
     * The platform-wide authority the actor held when they reached across organizers
     * (for example {@code ROLE_FINANCE}); set only on a platform-wide reach.
     */
    private String actorRole;

    /** The service that made the reach, for example {@code booking-service}; set only on a platform-wide reach. */
    private String service;

    /** The fixed reason the call site declared; never request data. Set only on a platform-wide reach. */
    private String reason;

    /** A fixed operation name where the call site has one. Set only on a platform-wide reach. */
    private String operation;

    /**
     * Additional context/metadata about the action
     * Examples:
     * - oldRole: CUSTOMER
     * - newRole: ORGANIZER
     * - organizationId: 12345
     * - reason: Organization approved
     */
    private Map<String, String> metadata;

    /**
     * Sanitized error message (if action failed)
     * IMPORTANT: Must NOT contain sensitive information like passwords, tokens, or internal stack traces
     */
    private String errorMessage;

    /**
     * Error code for categorization (if action failed)
     */
    private String errorCode;

    /**
     * Audit action types following OWASP logging vocabulary
     */
    public enum AuditAction {
        // Authentication events
        USER_LOGIN("authn_login_success"),
        USER_LOGIN_FAILED("authn_login_fail"),
        USER_LOGOUT("authn_logout_success"),

        // Authorization events
        ROLE_GRANT("authz_change:grant_role"),
        ROLE_REVOKE("authz_change:revoke_role"),
        PRIVILEGE_ESCALATION("authz_escalation"),
        /**
         * A super administrator issued a staff member a new one-time password: who, and for whom. The row
         * never carries the password.
         */
        STAFF_PASSWORD_RESET("authn_password_reset_issued"),
        ACCESS_DENIED("authz_fail"),

        // User management events
        USER_CREATED("user_created"),
        USER_UPDATED("user_updated"),
        USER_DELETED("user_deleted"),
        USER_SUSPENDED("user_suspended"),
        USER_ACTIVATED("user_activated"),

        // Organization events
        ORGANIZATION_APPROVED("organization_approved"),
        ORGANIZATION_REJECTED("organization_rejected"),
        ORGANIZATION_COMMISSION_CHANGED("organization_commission_changed"),
        ORGANIZATION_SUSPENDED("organization_suspended"),
        ORGANIZATION_UNSUSPENDED("organization_unsuspended"),
        ORGANIZATION_DELETION_REQUESTED("organization_deletion_requested"),
        ORGANIZATION_DELETION_CANCELLED("organization_deletion_cancelled"),
        PAYOUT_ACCOUNT_REJECTED("payout_account_rejected"),
        PAYOUT_ACCOUNT_SUSPENDED("payout_account_suspended"),
        PAYOUT_ACCOUNT_REINSTATED("payout_account_reinstated"),

        // Account lifecycle requested by the person or an administrator
        ACCOUNT_DELETION_REQUESTED("account_deletion_requested"),
        ACCOUNT_DELETION_CANCELLED("account_deletion_cancelled"),
        SESSION_REVOKED("session_revoked"),
        /** A token, session or every token of a user was revoked; the trigger is in the metadata. */
        ACCESS_REVOKED("access_revoked"),

        // Platform operations
        SYSTEM_ALERT_ACKNOWLEDGED("system_alert_acknowledged"),
        ANNOUNCEMENT_PUBLISHED("announcement_published"),

        /**
         * A member of platform staff acted across organizers or buyers: who, in which role, in
         * which service, why and doing what. Carries no request data and no personal data.
         */
        TENANT_BOUNDARY_CROSSED("authz_platform_wide"),

        // Sync events
        KEYCLOAK_SYNC_SUCCESS("keycloak_sync_success"),
        KEYCLOAK_SYNC_FAILURE("keycloak_sync_failure");

        private final String code;

        AuditAction(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * Audit status
     */
    public enum AuditStatus {
        SUCCESS,
        FAILURE,
        PENDING,
        PARTIAL_SUCCESS
    }

    /**
     * Create a success audit log entry
     */
    public static AuditLog success(AuditAction action, String userId, String performedBy,
                                   Instant now) {
        return AuditLog.builder()
                .action(action)
                .userId(userId)
                .performedBy(performedBy)
                .status(AuditStatus.SUCCESS)
                .timestamp(now)
                .build();
    }

    /**
     * Create a failure audit log entry with sanitized error message
     */
    public static AuditLog failure(AuditAction action, String userId, String performedBy,
                                   String errorMessage, String errorCode, Instant now) {
        return AuditLog.builder()
                .action(action)
                .userId(userId)
                .performedBy(performedBy)
                .status(AuditStatus.FAILURE)
                .timestamp(now)
                .errorMessage(sanitizeErrorMessage(errorMessage))
                .errorCode(errorCode)
                .build();
    }

    /**
     * Sanitize error messages to prevent information leakage
     * Removes stack traces, SQL queries, file paths, and other sensitive details
     */
    private static String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return null;
        }

        // Truncate very long error messages
        if (errorMessage.length() > 500) {
            errorMessage = errorMessage.substring(0, 500) + "...";
        }

        // Remove common sensitive patterns
        return errorMessage
                .replaceAll("(?i)password[=:]\\s*\\S+", "password=***")
                .replaceAll("(?i)token[=:]\\s*\\S+", "token=***")
                .replaceAll("(?i)secret[=:]\\s*\\S+", "secret=***")
                .replaceAll("(?i)api[_-]?key[=:]\\s*\\S+", "apikey=***")
                .replaceAll("at \\S+\\.java:\\d+", "[stack trace removed]")
                .replaceAll("Caused by:.*", "[caused by removed]")
                .trim();
    }
}
