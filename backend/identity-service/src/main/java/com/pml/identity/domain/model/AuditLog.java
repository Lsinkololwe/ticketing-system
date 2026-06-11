package com.pml.identity.domain.model;

import com.pml.identity.domain.base.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
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
@Document(collection = "audit_logs")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "user_action_idx", def = "{'userId': 1, 'action': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "action_timestamp_idx", def = "{'action': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "status_timestamp_idx", def = "{'status': 1, 'timestamp': -1}")
})
public class AuditLog implements Identifiable<String> {

    @Id
    private String id;

    /**
     * Audit action type
     */
    @Indexed
    private AuditAction action;

    /**
     * User ID who is the subject of the action (e.g., user whose role was changed)
     */
    @Indexed
    private String userId;

    /**
     * User ID who performed the action (e.g., admin who granted the role)
     */
    @Indexed
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
    @Indexed
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
     * Timestamp when the action occurred
     */
    @Indexed
    private Instant timestamp;

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
    public static AuditLog success(AuditAction action, String userId, String performedBy) {
        return AuditLog.builder()
                .action(action)
                .userId(userId)
                .performedBy(performedBy)
                .status(AuditStatus.SUCCESS)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a failure audit log entry with sanitized error message
     */
    public static AuditLog failure(AuditAction action, String userId, String performedBy, String errorMessage, String errorCode) {
        return AuditLog.builder()
                .action(action)
                .userId(userId)
                .performedBy(performedBy)
                .status(AuditStatus.FAILURE)
                .timestamp(Instant.now())
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
