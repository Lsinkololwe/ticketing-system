package com.pml.identity.service.impl;

import com.pml.identity.domain.model.PayoutConfigAuditLog;
import com.pml.identity.domain.valueobject.MobileMoneyAccount;
import com.pml.identity.domain.valueobject.PayoutBankDetails;
import com.pml.identity.repository.PayoutConfigAuditLogRepository;
import com.pml.identity.service.PayoutConfigAuditService;
import com.pml.identity.validation.FinancialDataValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of payout configuration audit service.
 *
 * SECURITY:
 * - All sensitive data is MASKED before logging (PCI-DSS)
 * - Account numbers show only last 4 digits
 * - Phone numbers show prefix and last 4 digits
 * - No plaintext sensitive data in audit logs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutConfigAuditServiceImpl implements PayoutConfigAuditService {

    private final PayoutConfigAuditLogRepository auditLogRepository;

    @Override
    public Mono<PayoutConfigAuditLog> logPayoutConfigChange(
        String organizationId,
        String userId,
        String username,
        PayoutConfigAuditLog.AuditAction action,
        String ipAddress,
        String userAgent,
        Map<String, String> metadata
    ) {
        PayoutConfigAuditLog auditLog = PayoutConfigAuditLog.builder()
            .organizationId(organizationId)
            .userId(userId)
            .username(username)
            .action(action)
            .timestamp(Instant.now())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .metadata(metadata)
            .success(true)
            .build();

        return auditLogRepository.save(auditLog)
            .doOnSuccess(log -> this.log.info(
                "Audit log created: action={}, org={}, user={}, ip={}",
                action, organizationId, username, ipAddress
            ))
            .doOnError(error -> this.log.error(
                "Failed to create audit log: action={}, org={}, user={}",
                action, organizationId, username, error
            ));
    }

    @Override
    public Mono<PayoutConfigAuditLog> logBankAccountChange(
        String organizationId,
        String userId,
        String username,
        PayoutConfigAuditLog.AuditAction action,
        PayoutBankDetails previousBankAccount,
        PayoutBankDetails newBankAccount,
        String ipAddress,
        String userAgent
    ) {
        // SECURITY: Mask account numbers (PCI-DSS requirement)
        Map<String, Object> previousDetails = maskBankAccountDetails(previousBankAccount);
        Map<String, Object> newDetails = maskBankAccountDetails(newBankAccount);

        PayoutConfigAuditLog auditLog = PayoutConfigAuditLog.builder()
            .organizationId(organizationId)
            .userId(userId)
            .username(username)
            .action(action)
            .timestamp(Instant.now())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .previousPayoutMethod("BANK_TRANSFER")
            .newPayoutMethod("BANK_TRANSFER")
            .previousAccountDetails(previousDetails)
            .newAccountDetails(newDetails)
            .verificationChanged(
                previousBankAccount != null && newBankAccount != null &&
                previousBankAccount.isVerified() != newBankAccount.isVerified()
            )
            .previousVerified(previousBankAccount != null && previousBankAccount.isVerified())
            .newVerified(newBankAccount != null && newBankAccount.isVerified())
            .success(true)
            .build();

        return auditLogRepository.save(auditLog)
            .doOnSuccess(log -> this.log.info(
                "Bank account change logged: action={}, org={}, user={}, bank={}",
                action, organizationId, username,
                newBankAccount != null ? newBankAccount.getBankName() : "N/A"
            ));
    }

    @Override
    public Mono<PayoutConfigAuditLog> logMobileMoneyAccountChange(
        String organizationId,
        String userId,
        String username,
        PayoutConfigAuditLog.AuditAction action,
        MobileMoneyAccount previousMobileAccount,
        MobileMoneyAccount newMobileAccount,
        String ipAddress,
        String userAgent
    ) {
        // SECURITY: Mask phone numbers (privacy)
        Map<String, Object> previousDetails = maskMobileMoneyAccountDetails(previousMobileAccount);
        Map<String, Object> newDetails = maskMobileMoneyAccountDetails(newMobileAccount);

        PayoutConfigAuditLog auditLog = PayoutConfigAuditLog.builder()
            .organizationId(organizationId)
            .userId(userId)
            .username(username)
            .action(action)
            .timestamp(Instant.now())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .previousPayoutMethod("MOBILE_MONEY")
            .newPayoutMethod("MOBILE_MONEY")
            .previousAccountDetails(previousDetails)
            .newAccountDetails(newDetails)
            .verificationChanged(
                previousMobileAccount != null && newMobileAccount != null &&
                previousMobileAccount.isVerified() != newMobileAccount.isVerified()
            )
            .previousVerified(previousMobileAccount != null && previousMobileAccount.isVerified())
            .newVerified(newMobileAccount != null && newMobileAccount.isVerified())
            .success(true)
            .build();

        return auditLogRepository.save(auditLog)
            .doOnSuccess(log -> this.log.info(
                "Mobile money account change logged: action={}, org={}, user={}, provider={}",
                action, organizationId, username,
                newMobileAccount != null ? newMobileAccount.getProvider() : "N/A"
            ));
    }

    @Override
    public Mono<PayoutConfigAuditLog> logVerificationChange(
        String organizationId,
        String adminUserId,
        String adminUsername,
        boolean verified,
        String ipAddress,
        String userAgent
    ) {
        PayoutConfigAuditLog auditLog = PayoutConfigAuditLog.builder()
            .organizationId(organizationId)
            .userId(adminUserId)
            .username(adminUsername)
            .action(verified ?
                PayoutConfigAuditLog.AuditAction.PAYOUT_ACCOUNT_VERIFIED :
                PayoutConfigAuditLog.AuditAction.PAYOUT_ACCOUNT_UNVERIFIED)
            .timestamp(Instant.now())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .verificationChanged(true)
            .newVerified(verified)
            .success(true)
            .build();

        return auditLogRepository.save(auditLog)
            .doOnSuccess(log -> this.log.info(
                "Verification change logged: verified={}, org={}, admin={}",
                verified, organizationId, adminUsername
            ));
    }

    @Override
    public Mono<PayoutConfigAuditLog> logFailedOperation(
        String organizationId,
        String userId,
        String username,
        PayoutConfigAuditLog.AuditAction action,
        String errorMessage,
        String ipAddress,
        String userAgent
    ) {
        PayoutConfigAuditLog auditLog = PayoutConfigAuditLog.builder()
            .organizationId(organizationId)
            .userId(userId)
            .username(username)
            .action(action)
            .timestamp(Instant.now())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .success(false)
            .errorMessage(errorMessage)
            .build();

        return auditLogRepository.save(auditLog)
            .doOnSuccess(log -> this.log.warn(
                "Failed operation logged: action={}, org={}, user={}, error={}",
                action, organizationId, username, errorMessage
            ));
    }

    @Override
    public Flux<PayoutConfigAuditLog> getAuditLogs(String organizationId, int limit) {
        return auditLogRepository.findByOrganizationIdOrderByTimestampDesc(
            organizationId,
            PageRequest.of(0, limit)
        );
    }

    @Override
    public Flux<PayoutConfigAuditLog> getAuditLogsInRange(
        String organizationId,
        Instant startTime,
        Instant endTime
    ) {
        return auditLogRepository.findByOrganizationIdAndTimestampBetweenOrderByTimestampDesc(
            organizationId,
            startTime,
            endTime
        );
    }

    // =========================================================================
    // PRIVATE HELPER METHODS - MASKING SENSITIVE DATA
    // =========================================================================

    /**
     * Mask bank account details for audit logging.
     * SECURITY: Never store plaintext account numbers (PCI-DSS).
     *
     * @param bankAccount bank account details
     * @return masked details map
     */
    private Map<String, Object> maskBankAccountDetails(PayoutBankDetails bankAccount) {
        if (bankAccount == null) {
            return null;
        }

        Map<String, Object> details = new HashMap<>();
        details.put("bankName", bankAccount.getBankName());
        details.put("bankCode", bankAccount.getBankCode());
        details.put("branchName", bankAccount.getBranchName());
        details.put("accountHolderName", bankAccount.getAccountHolderName());
        details.put("accountType", bankAccount.getAccountType());

        // CRITICAL: Mask account number (show last 4 digits only)
        if (bankAccount.getAccountNumber() != null) {
            details.put("accountNumber",
                FinancialDataValidator.maskAccountNumber(bankAccount.getAccountNumber()));
        }

        return details;
    }

    /**
     * Mask mobile money account details for audit logging.
     * SECURITY: Phone numbers are masked for privacy.
     *
     * @param mobileAccount mobile money account details
     * @return masked details map
     */
    private Map<String, Object> maskMobileMoneyAccountDetails(MobileMoneyAccount mobileAccount) {
        if (mobileAccount == null) {
            return null;
        }

        Map<String, Object> details = new HashMap<>();
        details.put("provider", mobileAccount.getProvider() != null ?
            mobileAccount.getProvider().toString() : null);
        details.put("accountHolderName", mobileAccount.getAccountHolderName());

        // CRITICAL: Mask phone number (show prefix and last 4 digits)
        if (mobileAccount.getPhoneNumber() != null) {
            details.put("phoneNumber",
                FinancialDataValidator.maskPhoneNumber(mobileAccount.getPhoneNumber()));
        }

        return details;
    }
}
