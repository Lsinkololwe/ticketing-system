package com.pml.booking.service.impl;

import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.repository.BankAccountRepository;
import com.pml.booking.service.BankAccountService;
import com.pml.booking.web.graphql.dto.CreateBankAccountInput;
import com.pml.booking.web.graphql.dto.UpdateBankAccountInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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
    public Mono<BankAccount> findDefaultByOrganizerId(String organizerId) {
        log.debug("Finding default bank account for organizer: {}", organizerId);
        return bankAccountRepository.findByOrganizerIdAndIsDefaultTrue(organizerId);
    }

    @Override
    public Mono<BankAccount> create(CreateBankAccountInput input, String organizerId) {
        log.info("Creating bank account for organizer: {}", organizerId);

        BankAccount bankAccount = BankAccount.builder()
                .organizerId(organizerId)
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
            return bankAccountRepository.findByOrganizerIdAndIsDefaultTrue(organizerId)
                    .flatMap(existing -> {
                        BankAccount updated = existing.toBuilder().isDefault(false).build();
                        return bankAccountRepository.save(updated);
                    })
                    .then(bankAccountRepository.save(bankAccount))
                    .doOnSuccess(saved -> log.info("Bank account created with ID: {}", saved.getId()));
        }

        return bankAccountRepository.save(bankAccount)
                .doOnSuccess(saved -> log.info("Bank account created with ID: {}", saved.getId()));
    }

    @Override
    public Mono<BankAccount> update(String id, UpdateBankAccountInput input) {
        log.info("Updating bank account: {}", id);

        return bankAccountRepository.findById(id)
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
                    if (input.accountNumber() != null) {
                        builder.accountNumber(input.accountNumber());
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
        log.info("Setting default bank account: {} for organizer: {}", id, organizerId);

        return bankAccountRepository.findByOrganizerIdAndIsDefaultTrue(organizerId)
                .flatMap(existing -> {
                    BankAccount updated = existing.toBuilder().isDefault(false).build();
                    return bankAccountRepository.save(updated);
                })
                .then(bankAccountRepository.findById(id))
                .flatMap(account -> {
                    BankAccount updated = account.toBuilder().isDefault(true).build();
                    return bankAccountRepository.save(updated);
                })
                .doOnSuccess(updated -> log.info("Default bank account set: {}", id));
    }

    @Override
    public Mono<Boolean> delete(String id) {
        log.info("Deleting bank account: {}", id);

        return bankAccountRepository.findById(id)
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

    @Override
    public Mono<BankAccount> verify(String id, String verifiedBy) {
        log.info("Verifying bank account: {} by: {}", id, verifiedBy);

        return bankAccountRepository.findById(id)
                .flatMap(existing -> {
                    BankAccount updated = existing.toBuilder()
                            .isVerified(true)
                            .verifiedAt(LocalDateTime.now())
                            .verifiedBy(verifiedBy)
                            .build();
                    return bankAccountRepository.save(updated);
                })
                .doOnSuccess(verified -> log.info("Bank account verified: {}", id));
    }
}
