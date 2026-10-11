package com.pml.booking.service.impl;

import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.repository.BankAccountRepository;
import com.pml.booking.service.BankAccountService;
import com.pml.booking.web.graphql.dto.CreateBankAccountInput;
import com.pml.booking.web.graphql.dto.UpdateBankAccountInput;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementation of {@link BankAccountService}.
 *
 * <p>Centralizes all bank account business logic previously scattered across
 * BankAccountQueryResolver and BankAccountMutationResolver.</p>
 *
 * @author Booking Service Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final com.pml.booking.infrastructure.client.IdentityServiceClient identityServiceClient;
    private final com.pml.booking.security.BankAccountAccess access;

    @Override
    public Flux<BankAccount> findByOrganizerId(String organizerId) {
        log.debug("Finding bank accounts for organizer: {}", organizerId);
        return bankAccountRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Mono<BankAccount> findById(String id) {
        log.debug("Finding bank account by ID: {}", id);
        return bankAccountRepository.findById(id);
    }

    @Override
    public Flux<BankAccount> findByOrganizationId(String organizationId) {
        return bankAccountRepository.findByOrganizationIdAndStatusNot(organizationId, "DELETED");
    }

    @Override
    public Mono<BankAccount> findDefaultByOrganizationId(String organizationId) {
        return bankAccountRepository.findByOrganizationIdAndIsDefaultTrueAndStatusNot(organizationId, "DELETED");
    }

    @Override
    public Mono<BankAccount> findDefaultByOrganizerId(String organizerId) {
        log.debug("Finding default bank account for organizer: {}", organizerId);
        return bankAccountRepository.findByOrganizerIdAndIsDefaultTrue(organizerId);
    }

    @Override
    public Mono<BankAccount> create(CreateBankAccountInput input, String organizerId) {
        log.info("Creating bank account for organizer: {}", organizerId);

        // The document is owned by an organization as well as by the person who entered it, and the
        // validator requires both. The client names neither: the organization is the one this
        // caller owns, resolved from identity (the source of truth for membership).
        return owningOrganizationId(organizerId).flatMap(access::require).flatMap(organizationId -> {
            BankAccount bankAccount = BankAccount.builder()
                    .organizerId(organizerId)
                    .organizationId(organizationId)
                    .accountHolderName(input.accountHolderName())
                    .bankName(input.bankName())
                    .bankCode(input.bankCode())
                    .branchName(input.branchName())
                    .branchCode(input.branchCode())
                    .accountNumber(input.accountNumber())
                    .accountType(input.accountType())
                    .currency(input.currency() != null ? input.currency() : "ZMW")
                    .swiftCode(input.swiftCode())
                    .isDefault(input.isDefault() != null && input.isDefault())
                    .isVerified(false)
                    .status("ACTIVE")
                    .build();

            // If this is set as default, unset other default accounts first
            if (bankAccount.isDefault()) {
                return bankAccountRepository.findByOrganizationIdAndIsDefaultTrueAndStatusNot(organizationId, "DELETED")
                        .flatMap(existing -> {
                            BankAccount updated = existing.toBuilder().isDefault(false).build();
                            return bankAccountRepository.save(updated);
                        })
                        .then(bankAccountRepository.save(bankAccount))
                        .doOnSuccess(saved -> log.info("Bank account created with ID: {}", saved.getId()));
            }

            return bankAccountRepository.save(bankAccount)
                    .doOnSuccess(saved -> log.info("Bank account created with ID: {}", saved.getId()));
        });
    }

    /** The organization this caller owns (else the first one they actively belong to). */
    private Mono<String> owningOrganizationId(String userId) {
        return identityServiceClient.getUserOrganizations(userId)
                .flatMap(response -> {
                    var active = response.organizations().stream()
                            .filter(com.pml.booking.infrastructure.client.IdentityServiceClient.OrganizationMembershipInfo::isActive)
                            .toList();
                    return active.stream().filter(o -> "OWNER".equals(o.role())).findFirst()
                            .or(() -> active.stream().findFirst())
                            .map(o -> Mono.just(o.organizationId()))
                            .orElseGet(() -> Mono.error(new IllegalStateException(
                                    "No organization to attach the bank account to")));
                });
    }

    @Override
    public Mono<BankAccount> update(String id, UpdateBankAccountInput input) {
        log.info("Updating bank account: {}", id);

        // The resolver's tenantReads.bankAccountForCaller(id) already proved ownership before
        // calling this; defense in depth here too.
        return bankAccountForCaller(id)
                .flatMap(existing -> {
                    BankAccount.BankAccountBuilder builder = existing.toBuilder();

                    if (input.accountHolderName() != null) {
                        builder.accountHolderName(input.accountHolderName());
                    }
                    if (input.bankName() != null) {
                        builder.bankName(input.bankName());
                    }
                    if (input.bankCode() != null) {
                        builder.bankCode(input.bankCode());
                    }
                    if (input.branchName() != null) {
                        builder.branchName(input.branchName());
                    }
                    if (input.branchCode() != null) {
                        builder.branchCode(input.branchCode());
                    }
                    if (input.accountNumber() != null && !input.accountNumber().equals(existing.getAccountNumber())
                            // A form re-submitted with the masked number it was shown is not a new destination.
                            && !input.accountNumber().equals(com.pml.booking.security.AccountNumberMask.of(existing.getAccountNumber()))) {
                        // A changed number is a different destination; it is verified again.
                        builder.accountNumber(input.accountNumber())
                                .isVerified(false)
                                .verificationStatus(com.pml.booking.domain.model.BankAccount.VerificationStatus.PENDING)
                                .microDepositAmount(null)
                                .verificationAttempts(0)
                                .verificationLockedUntil(null);
                    }
                    if (input.accountType() != null) {
                        builder.accountType(input.accountType());
                    }
                    if (input.swiftCode() != null) {
                        builder.swiftCode(input.swiftCode());
                    }

                    return bankAccountRepository.save(builder.build());
                })
                .doOnSuccess(updated -> log.info("Bank account updated: {}", updated.getId()));
    }

    @Override
    public Mono<BankAccount> setAsDefault(String id, String organizerId) {
        log.info("Setting default bank account: {} by: {}", id, organizerId);

        // The resolver's tenantReads.bankAccountForCaller(id) already proved the account is the caller's
        // organization's; the default is the organization's, not the person's.
        return bankAccountForCaller(id)
                .flatMap(account -> bankAccountRepository
                        .findByOrganizationIdAndIsDefaultTrueAndStatusNot(account.getOrganizationId(), "DELETED")
                        .filter(existing -> !existing.getId().equals(account.getId()))
                        .flatMap(existing -> bankAccountRepository.save(existing.toBuilder().isDefault(false).build()))
                        .then(bankAccountRepository.save(account.toBuilder().isDefault(true).build())))
                .doOnSuccess(updated -> log.info("Default bank account set: {}", id));
    }

    @Override
    public Mono<Boolean> delete(String id) {
        log.info("Deleting bank account: {}", id);

        // The resolver's tenantReads.bankAccountForCaller(id) already proved ownership before
        // calling this; defense in depth here too.
        return bankAccountForCaller(id)
                .flatMap(existing -> {
                    BankAccount updated = existing.toBuilder()
                            .status("DELETED")
                            .build();
                    return bankAccountRepository.save(updated);
                })
                .map(saved -> true)
                .defaultIfEmpty(false)
                .doOnSuccess(result -> log.info("Bank account {} deleted: {}", id, result));
    }

    /**
     * The bank account the caller is entitled to act on, or {@code BANK_ACCOUNT_UNKNOWN}.
     * Reached only after the resolver's own {@code tenantReads.bankAccountForCaller}; defense in
     * depth, matching that method's shape for the same repository.
     */
    private Mono<BankAccount> bankAccountForCaller(String id) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                bankAccountRepository.findById(id),
                organizationIds -> bankAccountRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                ErrorCode.BANK_ACCOUNT_UNKNOWN,
                "bank account " + id));
    }

}
