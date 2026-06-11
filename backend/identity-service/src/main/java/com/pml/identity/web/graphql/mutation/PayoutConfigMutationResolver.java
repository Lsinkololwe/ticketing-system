package com.pml.identity.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.domain.enums.MobileMoneyProvider;
import com.pml.identity.domain.enums.PayoutMethod;
import com.pml.identity.domain.enums.PayoutSchedule;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.PayoutConfigAuditLog;
import com.pml.identity.domain.valueobject.MobileMoneyAccount;
import com.pml.identity.domain.valueobject.PayoutBankDetails;
import com.pml.identity.domain.valueobject.PayoutConfig;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.security.FieldEncryptionService;
import com.pml.identity.service.AuthorizationService;
import com.pml.identity.service.PayoutConfigAuditService;
import com.pml.identity.validation.FinancialDataValidator;
import com.pml.shared.constants.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * GraphQL mutation resolver for payout configuration.
 *
 * SECURITY FEATURES:
 * - Owner-only access (enforced via AuthorizationService)
 * - Field-level encryption for sensitive data (AES-256-GCM)
 * - OWASP-compliant input validation
 * - PCI-DSS compliant audit logging
 * - IP address and user agent tracking
 *
 * AUTHORIZATION:
 * - Only organization OWNER can modify payout configuration
 * - Only ADMIN/FINANCE can verify payout accounts
 *
 * COMPLIANCE:
 * - PCI-DSS Requirement 3.4: Encryption at rest
 * - PCI-DSS Requirement 10: Audit logging
 * - OWASP Input Validation
 * - GDPR: Data minimization and privacy
 */
@DgsComponent
@RequiredArgsConstructor
@Slf4j
public class PayoutConfigMutationResolver {

    private final OrganizationRepository organizationRepository;
    private final AuthorizationService authorizationService;
    private final FieldEncryptionService fieldEncryptionService;
    private final PayoutConfigAuditService auditService;

    // =========================================================================
    // UPDATE PAYOUT CONFIGURATION
    // =========================================================================

    /**
     * Update payout configuration (method, schedule, minimum amount).
     *
     * @param organizationId organization ID
     * @param input payout config update input
     * @param exchange server web exchange (for IP address)
     * @return updated organization
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('ROLE_ORGANIZER', 'ROLE_ADMIN')")
    public Mono<Organization> updatePayoutConfig(
        @InputArgument String organizationId,
        @InputArgument Map<String, Object> input,
        ServerWebExchange exchange
    ) {
        return ReactiveSecurityContextHolder.getContext()
            .map(securityContext -> securityContext.getAuthentication())
            .flatMap(auth -> {
                String userId = auth.getName();
                String username = getUsernameFromAuth(auth);
                String ipAddress = getClientIpAddress(exchange);
                String userAgent = getUserAgent(exchange);

                // Authorization check: Only OWNER can modify payout config
                return authorizationService.isOrganizationOwner(userId, organizationId)
                    .flatMap(isOwner -> {
                        if (!isOwner) {
                            log.warn("Unauthorized payout config update attempt: user={}, org={}, ip={}",
                                username, organizationId, ipAddress);
                            return auditService.logFailedOperation(
                                organizationId, userId, username,
                                PayoutConfigAuditLog.AuditAction.PAYOUT_CONFIG_ACCESS_ATTEMPTED,
                                "Unauthorized: Only organization owner can modify payout configuration",
                                ipAddress, userAgent
                            ).then(Mono.error(new SecurityException(
                                "Only organization owner can modify payout configuration"
                            )));
                        }

                        // Load organization
                        return organizationRepository.findById(organizationId)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "Organization not found"
                            )))
                            .flatMap(organization -> {
                                // Get or create payout config
                                PayoutConfig payoutConfig = organization.getPayoutConfig();
                                if (payoutConfig == null) {
                                    payoutConfig = PayoutConfig.builder().build();
                                }

                                // Update fields
                                boolean changed = false;

                                if (input.containsKey("preferredMethod")) {
                                    PayoutMethod newMethod = PayoutMethod.valueOf(
                                        (String) input.get("preferredMethod")
                                    );
                                    if (payoutConfig.getPreferredMethod() != newMethod) {
                                        payoutConfig.setPreferredMethod(newMethod);
                                        changed = true;
                                    }
                                }

                                if (input.containsKey("schedule")) {
                                    PayoutSchedule newSchedule = PayoutSchedule.valueOf(
                                        (String) input.get("schedule")
                                    );
                                    if (payoutConfig.getSchedule() != newSchedule) {
                                        payoutConfig.setSchedule(newSchedule);
                                        changed = true;
                                    }
                                }

                                if (input.containsKey("minimumPayoutAmount")) {
                                    Double newMinimum = ((Number) input.get("minimumPayoutAmount")).doubleValue();

                                    // Validate minimum amount
                                    if (!FinancialDataValidator.isValidPayoutAmount(newMinimum, 100.0)) {
                                        return Mono.error(new IllegalArgumentException(
                                            "Minimum payout amount must be at least 100.0 ZMW"
                                        ));
                                    }

                                    if (!payoutConfig.getMinimumPayoutAmount().equals(newMinimum)) {
                                        payoutConfig.setMinimumPayoutAmount(newMinimum);
                                        changed = true;
                                    }
                                }

                                if (!changed) {
                                    return Mono.just(organization);
                                }

                                // Save updated organization
                                organization.setPayoutConfig(payoutConfig);
                                return organizationRepository.save(organization)
                                    .flatMap(savedOrg -> {
                                        // Audit log
                                        return auditService.logPayoutConfigChange(
                                            organizationId, userId, username,
                                            PayoutConfigAuditLog.AuditAction.PAYOUT_CONFIG_UPDATED,
                                            ipAddress, userAgent, null
                                        ).thenReturn(savedOrg);
                                    });
                            });
                    });
            });
    }

    // =========================================================================
    // SET BANK ACCOUNT
    // =========================================================================

    /**
     * Set bank account for payouts.
     * SECURITY: Account numbers are encrypted before storage.
     *
     * @param organizationId organization ID
     * @param input bank account input
     * @param exchange server web exchange
     * @return updated organization
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('ROLE_ORGANIZER', 'ROLE_ADMIN')")
    public Mono<Organization> setBankAccount(
        @InputArgument String organizationId,
        @InputArgument Map<String, Object> input,
        ServerWebExchange exchange
    ) {
        return ReactiveSecurityContextHolder.getContext()
            .map(securityContext -> securityContext.getAuthentication())
            .flatMap(auth -> {
                String userId = auth.getName();
                String username = getUsernameFromAuth(auth);
                String ipAddress = getClientIpAddress(exchange);
                String userAgent = getUserAgent(exchange);

                // Authorization check
                return authorizationService.isOrganizationOwner(userId, organizationId)
                    .flatMap(isOwner -> {
                        if (!isOwner) {
                            log.warn("Unauthorized bank account update attempt: user={}, org={}, ip={}",
                                username, organizationId, ipAddress);
                            return auditService.logFailedOperation(
                                organizationId, userId, username,
                                PayoutConfigAuditLog.AuditAction.BANK_ACCOUNT_ADDED,
                                "Unauthorized: Only organization owner can set bank account",
                                ipAddress, userAgent
                            ).then(Mono.error(new SecurityException(
                                "Only organization owner can set bank account"
                            )));
                        }

                        // Validate inputs
                        String bankName = (String) input.get("bankName");
                        String bankCode = (String) input.get("bankCode");
                        String accountNumber = (String) input.get("accountNumber");
                        String accountHolderName = (String) input.get("accountHolderName");

                        // SECURITY: Validate account number
                        if (!FinancialDataValidator.isValidZambianAccountNumber(accountNumber)) {
                            return Mono.error(new IllegalArgumentException(
                                "Invalid bank account number format (must be 10-16 digits)"
                            ));
                        }

                        // SECURITY: Validate SWIFT code
                        if (!FinancialDataValidator.isValidSwiftCode(bankCode)) {
                            return Mono.error(new IllegalArgumentException(
                                "Invalid SWIFT/BIC code format (must be 8 or 11 alphanumeric characters)"
                            ));
                        }

                        // SECURITY: Validate account holder name
                        if (!FinancialDataValidator.isValidAccountHolderName(accountHolderName)) {
                            return Mono.error(new IllegalArgumentException(
                                "Invalid account holder name (letters, spaces, hyphens, apostrophes only, 2-100 characters)"
                            ));
                        }

                        // Encrypt account number
                        return fieldEncryptionService.encrypt(accountNumber)
                            .flatMap(encryptedAccountNumber -> {
                                // Load organization
                                return organizationRepository.findById(organizationId)
                                    .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                        "Organization not found"
                                    )))
                                    .flatMap(organization -> {
                                        // Get existing payout config
                                        PayoutConfig payoutConfig = organization.getPayoutConfig();
                                        if (payoutConfig == null) {
                                            payoutConfig = PayoutConfig.builder()
                                                .preferredMethod(PayoutMethod.BANK_TRANSFER)
                                                .build();
                                        }

                                        PayoutBankDetails previousBankAccount = payoutConfig.getBankAccount();

                                        // Create new bank account
                                        PayoutBankDetails bankAccount = PayoutBankDetails.builder()
                                            .bankName(bankName)
                                            .bankCode(FinancialDataValidator.sanitizeAlphanumeric(bankCode).toUpperCase())
                                            .branchName((String) input.get("branchName"))
                                            .branchCode((String) input.get("branchCode"))
                                            .accountNumber(encryptedAccountNumber) // ENCRYPTED
                                            .accountHolderName(accountHolderName.trim())
                                            .accountType((String) input.getOrDefault("accountType", "BUSINESS"))
                                            .verified(false) // Must be verified by admin
                                            .build();

                                        payoutConfig.setBankAccount(bankAccount);
                                        payoutConfig.setPreferredMethod(PayoutMethod.BANK_TRANSFER);
                                        payoutConfig.setVerified(false); // Reset verification

                                        organization.setPayoutConfig(payoutConfig);

                                        // Save
                                        return organizationRepository.save(organization)
                                            .flatMap(savedOrg -> {
                                                // Audit log (with MASKED account numbers)
                                                return auditService.logBankAccountChange(
                                                    organizationId, userId, username,
                                                    previousBankAccount == null ?
                                                        PayoutConfigAuditLog.AuditAction.BANK_ACCOUNT_ADDED :
                                                        PayoutConfigAuditLog.AuditAction.BANK_ACCOUNT_UPDATED,
                                                    previousBankAccount,
                                                    createMaskedBankAccount(bankAccount, accountNumber),
                                                    ipAddress, userAgent
                                                ).thenReturn(savedOrg);
                                            });
                                    });
                            });
                    });
            });
    }

    // =========================================================================
    // SET MOBILE MONEY ACCOUNT
    // =========================================================================

    /**
     * Set mobile money account for payouts.
     * SECURITY: Phone numbers are validated and masked.
     *
     * @param organizationId organization ID
     * @param input mobile money account input
     * @param exchange server web exchange
     * @return updated organization
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('ROLE_ORGANIZER', 'ROLE_ADMIN')")
    public Mono<Organization> setMobileMoneyAccount(
        @InputArgument String organizationId,
        @InputArgument Map<String, Object> input,
        ServerWebExchange exchange
    ) {
        return ReactiveSecurityContextHolder.getContext()
            .map(securityContext -> securityContext.getAuthentication())
            .flatMap(auth -> {
                String userId = auth.getName();
                String username = getUsernameFromAuth(auth);
                String ipAddress = getClientIpAddress(exchange);
                String userAgent = getUserAgent(exchange);

                // Authorization check
                return authorizationService.isOrganizationOwner(userId, organizationId)
                    .flatMap(isOwner -> {
                        if (!isOwner) {
                            log.warn("Unauthorized mobile money account update attempt: user={}, org={}, ip={}",
                                username, organizationId, ipAddress);
                            return auditService.logFailedOperation(
                                organizationId, userId, username,
                                PayoutConfigAuditLog.AuditAction.MOBILE_MONEY_ACCOUNT_ADDED,
                                "Unauthorized: Only organization owner can set mobile money account",
                                ipAddress, userAgent
                            ).then(Mono.error(new SecurityException(
                                "Only organization owner can set mobile money account"
                            )));
                        }

                        // Validate inputs
                        String phoneNumber = (String) input.get("phoneNumber");
                        String accountHolderName = (String) input.get("accountHolderName");
                        MobileMoneyProvider provider = MobileMoneyProvider.valueOf(
                            (String) input.get("provider")
                        );

                        // SECURITY: Normalize and validate phone number
                        String normalizedPhone = FinancialDataValidator.normalizeToE164(phoneNumber);
                        if (normalizedPhone == null) {
                            return Mono.error(new IllegalArgumentException(
                                "Invalid phone number format (must be E.164 format, e.g., +260971234567)"
                            ));
                        }

                        // SECURITY: Validate account holder name
                        if (!FinancialDataValidator.isValidAccountHolderName(accountHolderName)) {
                            return Mono.error(new IllegalArgumentException(
                                "Invalid account holder name (letters, spaces, hyphens, apostrophes only, 2-100 characters)"
                            ));
                        }

                        // Load organization
                        return organizationRepository.findById(organizationId)
                            .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "Organization not found"
                            )))
                            .flatMap(organization -> {
                                // Get existing payout config
                                PayoutConfig payoutConfig = organization.getPayoutConfig();
                                if (payoutConfig == null) {
                                    payoutConfig = PayoutConfig.builder()
                                        .preferredMethod(PayoutMethod.MOBILE_MONEY)
                                        .build();
                                }

                                MobileMoneyAccount previousMobileAccount = payoutConfig.getMobileMoneyAccount();

                                // Create new mobile money account
                                MobileMoneyAccount mobileAccount = MobileMoneyAccount.builder()
                                    .provider(provider)
                                    .phoneNumber(normalizedPhone)
                                    .accountHolderName(accountHolderName.trim())
                                    .verified(false) // Must be verified by admin
                                    .build();

                                payoutConfig.setMobileMoneyAccount(mobileAccount);
                                payoutConfig.setPreferredMethod(PayoutMethod.MOBILE_MONEY);
                                payoutConfig.setVerified(false); // Reset verification

                                organization.setPayoutConfig(payoutConfig);

                                // Save
                                return organizationRepository.save(organization)
                                    .flatMap(savedOrg -> {
                                        // Audit log (with MASKED phone numbers)
                                        return auditService.logMobileMoneyAccountChange(
                                            organizationId, userId, username,
                                            previousMobileAccount == null ?
                                                PayoutConfigAuditLog.AuditAction.MOBILE_MONEY_ACCOUNT_ADDED :
                                                PayoutConfigAuditLog.AuditAction.MOBILE_MONEY_ACCOUNT_UPDATED,
                                            previousMobileAccount,
                                            mobileAccount,
                                            ipAddress, userAgent
                                        ).thenReturn(savedOrg);
                                    });
                            });
                    });
            });
    }

    // =========================================================================
    // VERIFY PAYOUT ACCOUNT (ADMIN ONLY)
    // =========================================================================

    /**
     * Verify payout account (admin only).
     *
     * @param organizationId organization ID
     * @param verified verification status
     * @param exchange server web exchange
     * @return updated organization
     */
    @DgsMutation
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_FINANCE', 'ROLE_SUPER_ADMIN')")
    public Mono<Organization> verifyPayoutAccount(
        @InputArgument String organizationId,
        @InputArgument Boolean verified,
        ServerWebExchange exchange
    ) {
        return ReactiveSecurityContextHolder.getContext()
            .map(securityContext -> securityContext.getAuthentication())
            .flatMap(auth -> {
                String adminUserId = auth.getName();
                String adminUsername = getUsernameFromAuth(auth);
                String ipAddress = getClientIpAddress(exchange);
                String userAgent = getUserAgent(exchange);

                return organizationRepository.findById(organizationId)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Organization not found"
                    )))
                    .flatMap(organization -> {
                        PayoutConfig payoutConfig = organization.getPayoutConfig();
                        if (payoutConfig == null || !payoutConfig.isConfigured()) {
                            return Mono.error(new IllegalArgumentException(
                                "Payout account not configured"
                            ));
                        }

                        // Update verification status
                        payoutConfig.setVerified(verified);

                        // Also update bank account or mobile account verification
                        if (payoutConfig.getBankAccount() != null) {
                            payoutConfig.getBankAccount().setVerified(verified);
                        }
                        if (payoutConfig.getMobileMoneyAccount() != null) {
                            payoutConfig.getMobileMoneyAccount().setVerified(verified);
                        }

                        organization.setPayoutConfig(payoutConfig);
                        organization.setPayoutAccountVerified(verified);

                        return organizationRepository.save(organization)
                            .flatMap(savedOrg -> {
                                // Audit log
                                return auditService.logVerificationChange(
                                    organizationId, adminUserId, adminUsername,
                                    verified, ipAddress, userAgent
                                ).thenReturn(savedOrg);
                            });
                    });
            });
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private String getUsernameFromAuth(Authentication auth) {
        // Try to get preferred_username from JWT claims
        if (auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null) {
                return preferredUsername;
            }
        }
        return auth.getName();
    }

    private String getClientIpAddress(ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return "unknown";
        }
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    private String getUserAgent(ServerWebExchange exchange) {
        if (exchange == null || exchange.getRequest() == null) {
            return "unknown";
        }
        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }

    /**
     * Create a masked copy of bank account for audit logging.
     * SECURITY: Never log plaintext account numbers.
     */
    private PayoutBankDetails createMaskedBankAccount(PayoutBankDetails encrypted, String plaintext) {
        return PayoutBankDetails.builder()
            .bankName(encrypted.getBankName())
            .bankCode(encrypted.getBankCode())
            .branchName(encrypted.getBranchName())
            .branchCode(encrypted.getBranchCode())
            .accountNumber(FinancialDataValidator.maskAccountNumber(plaintext)) // MASKED
            .accountHolderName(encrypted.getAccountHolderName())
            .accountType(encrypted.getAccountType())
            .verified(encrypted.isVerified())
            .build();
    }
}
