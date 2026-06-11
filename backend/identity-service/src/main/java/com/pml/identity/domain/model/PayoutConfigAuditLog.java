package com.pml.identity.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * Audit log for payout configuration changes.
 *
 * PCI-DSS REQUIREMENT 10:
 * - Track and monitor all access to network resources and cardholder data
 * - Log all changes to sensitive account data
 * - Record: User ID, event type, timestamp, success/failure, origination
 *
 * OWASP LOGGING GUIDELINES:
 * - Log security-relevant events (authentication, authorization, data modification)
 * - Include: Who (user), What (action), When (timestamp), Where (IP address)
 * - NEVER log sensitive data (account numbers, passwords)
 * - Use structured logging for SIEM integration
 *
 * RETENTION:
 * - PCI-DSS requires at least 1 year of audit logs
 * - Recommend 3-7 years for financial audit trails
 */
@Document(collection = "payout_config_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "org_timestamp_idx", def = "{'organizationId': 1, 'timestamp': -1}")
@CompoundIndex(name = "user_timestamp_idx", def = "{'userId': 1, 'timestamp': -1}")
public class PayoutConfigAuditLog {

    @Id
    private String id;

    /**
     * Organization whose payout config was modified
     */
    @Indexed
    private String organizationId;

    /**
     * User who made the change
     */
    @Indexed
    private String userId;

    /**
     * Username for display (cached from User)
     */
    private String username;

    /**
     * Action type
     */
    @Indexed
    private AuditAction action;

    /**
     * Timestamp of the change
     */
    @Indexed
    private Instant timestamp;

    /**
     * IP address of the user (for security tracking)
     */
    private String ipAddress;

    /**
     * User agent (browser/device info)
     */
    private String userAgent;

    /**
     * Previous payout method (before change)
     */
    private String previousPayoutMethod;

    /**
     * New payout method (after change)
     */
    private String newPayoutMethod;

    /**
     * Previous account details (MASKED - never store plaintext)
     * Example: {"accountNumber": "****1234", "bankName": "Zanaco"}
     */
    private Map<String, Object> previousAccountDetails;

    /**
     * New account details (MASKED - never store plaintext)
     * Example: {"accountNumber": "****5678", "bankName": "FNB"}
     */
    private Map<String, Object> newAccountDetails;

    /**
     * Whether verification status changed
     */
    private Boolean verificationChanged;

    /**
     * Previous verification status
     */
    private Boolean previousVerified;

    /**
     * New verification status
     */
    private Boolean newVerified;

    /**
     * Additional metadata (optional)
     * Can include: session ID, request ID, etc.
     */
    private Map<String, String> metadata;

    /**
     * Whether operation succeeded
     */
    @Builder.Default
    private boolean success = true;

    /**
     * Error message if operation failed (NO sensitive data)
     */
    private String errorMessage;

    /**
     * Audit action types for payout configuration
     */
    public enum AuditAction {
        PAYOUT_CONFIG_UPDATED,          // General payout config update
        BANK_ACCOUNT_ADDED,             // Bank account added
        BANK_ACCOUNT_UPDATED,           // Bank account updated
        BANK_ACCOUNT_REMOVED,           // Bank account removed
        MOBILE_MONEY_ACCOUNT_ADDED,     // Mobile money account added
        MOBILE_MONEY_ACCOUNT_UPDATED,   // Mobile money account updated
        MOBILE_MONEY_ACCOUNT_REMOVED,   // Mobile money account removed
        PAYOUT_METHOD_CHANGED,          // Preferred payout method changed
        PAYOUT_SCHEDULE_CHANGED,        // Payout schedule changed
        MINIMUM_AMOUNT_CHANGED,         // Minimum payout amount changed
        COMMISSION_RATE_CHANGED,        // Commission rate changed (admin only)
        PAYOUT_ACCOUNT_VERIFIED,        // Admin verified payout account
        PAYOUT_ACCOUNT_UNVERIFIED,      // Admin unverified payout account
        PAYOUT_CONFIG_ACCESS_ATTEMPTED, // Unauthorized access attempt
        PAYOUT_CONFIG_DECRYPTION_FAILED // Decryption failure (security incident)
    }
}
