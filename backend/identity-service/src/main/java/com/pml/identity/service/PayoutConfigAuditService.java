package com.pml.identity.service;

import com.pml.identity.domain.model.PayoutConfigAuditLog;
import com.pml.identity.domain.valueobject.PayoutBankDetails;
import com.pml.identity.domain.valueobject.MobileMoneyAccount;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Service for auditing payout configuration changes.
 *
 * PCI-DSS COMPLIANCE:
 * - Requirement 10: Track and monitor all access to network resources and cardholder data
 * - All changes to sensitive financial data must be logged
 * - Logs must include: user, timestamp, action, before/after state
 *
 * OWASP REQUIREMENTS:
 * - Log all security-relevant events
 * - Never log sensitive data (account numbers, passwords)
 * - Use structured logging for SIEM integration
 */
public interface PayoutConfigAuditService {

    /**
     * Log payout configuration update.
     *
     * @param organizationId organization ID
     * @param userId user who made the change
     * @param username username for display
     * @param action audit action type
     * @param ipAddress user's IP address
     * @param userAgent user's user agent
     * @param metadata additional metadata
     * @return saved audit log
     */
    Mono<PayoutConfigAuditLog> logPayoutConfigChange(
        String organizationId,
        String userId,
        String username,
        PayoutConfigAuditLog.AuditAction action,
        String ipAddress,
        String userAgent,
        Map<String, String> metadata
    );

    /**
     * Log bank account change with before/after details.
     *
     * SECURITY: Account numbers are MASKED in audit log (PCI-DSS).
     *
     * @param organizationId organization ID
     * @param userId user who made the change
     * @param username username for display
     * @param action audit action type
     * @param previousBankAccount previous bank account (null if adding)
     * @param newBankAccount new bank account (null if removing)
     * @param ipAddress user's IP address
     * @param userAgent user's user agent
     * @return saved audit log
     */
    Mono<PayoutConfigAuditLog> logBankAccountChange(
        String organizationId,
        String userId,
        String username,
        PayoutConfigAuditLog.AuditAction action,
        PayoutBankDetails previousBankAccount,
        PayoutBankDetails newBankAccount,
        String ipAddress,
        String userAgent
    );

    /**
     * Log mobile money account change with before/after details.
     *
     * SECURITY: Phone numbers are MASKED in audit log (privacy).
     *
     * @param organizationId organization ID
     * @param userId user who made the change
     * @param username username for display
     * @param action audit action type
     * @param previousMobileAccount previous mobile money account (null if adding)
     * @param newMobileAccount new mobile money account (null if removing)
     * @param ipAddress user's IP address
     * @param userAgent user's user agent
     * @return saved audit log
     */
    Mono<PayoutConfigAuditLog> logMobileMoneyAccountChange(
        String organizationId,
        String userId,
        String username,
        PayoutConfigAuditLog.AuditAction action,
        MobileMoneyAccount previousMobileAccount,
        MobileMoneyAccount newMobileAccount,
        String ipAddress,
        String userAgent
    );

    /**
     * Log verification status change (admin action).
     *
     * @param organizationId organization ID
     * @param adminUserId admin user who verified
     * @param adminUsername admin username
     * @param verified new verification status
     * @param ipAddress admin's IP address
     * @param userAgent admin's user agent
     * @return saved audit log
     */
    Mono<PayoutConfigAuditLog> logVerificationChange(
        String organizationId,
        String adminUserId,
        String adminUsername,
        boolean verified,
        String ipAddress,
        String userAgent
    );

    /**
     * Log failed operation (security incident).
     *
     * @param organizationId organization ID
     * @param userId user who attempted the operation
     * @param username username
     * @param action attempted action
     * @param errorMessage error message (NO sensitive data)
     * @param ipAddress user's IP address
     * @param userAgent user's user agent
     * @return saved audit log
     */
    Mono<PayoutConfigAuditLog> logFailedOperation(
        String organizationId,
        String userId,
        String username,
        PayoutConfigAuditLog.AuditAction action,
        String errorMessage,
        String ipAddress,
        String userAgent
    );

    /**
     * Get audit logs for an organization.
     *
     * @param organizationId organization ID
     * @param limit maximum number of logs to return
     * @return audit logs (most recent first)
     */
    Flux<PayoutConfigAuditLog> getAuditLogs(String organizationId, int limit);

    /**
     * Get audit logs within a time range (for compliance reporting).
     *
     * @param organizationId organization ID
     * @param startTime start of time range
     * @param endTime end of time range
     * @return audit logs
     */
    Flux<PayoutConfigAuditLog> getAuditLogsInRange(
        String organizationId,
        Instant startTime,
        Instant endTime
    );
}
