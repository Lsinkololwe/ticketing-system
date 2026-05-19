package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.PlatformAccountType;
import com.pml.booking.domain.model.PlatformAccount;
import com.pml.booking.exception.InsufficientEscrowBalanceException;
import com.pml.booking.repository.PlatformAccountRepository;
import com.pml.booking.service.PlatformAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Platform Account Service Implementation
 *
 * <p>Manages platform-owned accounts for operating, reserve, and tax holding purposes.
 * These accounts are distinct from event escrow accounts and represent funds
 * that belong to the platform.</p>
 *
 * <h2>Account Types</h2>
 * <ul>
 *   <li><b>OPERATING</b>: Main bank account for settlements and payouts</li>
 *   <li><b>RESERVE</b>: Emergency fund for chargeback protection</li>
 *   <li><b>TAX_HOLDING</b>: Withheld taxes pending remittance</li>
 * </ul>
 *
 * @see PlatformAccountService
 * @see PlatformAccount
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAccountServiceImpl implements PlatformAccountService {

    private final PlatformAccountRepository accountRepository;

    // ========================================================================
    // ACCOUNT MANAGEMENT
    // ========================================================================

    @Override
    @Transactional
    public Mono<PlatformAccount> getOrCreateAccount(PlatformAccountType accountType, String currency) {
        return accountRepository.findByAccountType(accountType)
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Creating platform account: {} ({})", accountType, currency);
                    PlatformAccount account = createAccountByType(accountType);
                    return accountRepository.save(account)
                            .doOnSuccess(saved -> log.info("Platform account created: {} with ID {}",
                                    saved.getAccountType(), saved.getId()));
                }));
    }

    private PlatformAccount createAccountByType(PlatformAccountType accountType) {
        return switch (accountType) {
            case OPERATING -> PlatformAccount.createOperating();
            case RESERVE -> PlatformAccount.createReserve(new BigDecimal("100000")); // Default target
            case TAX_HOLDING -> PlatformAccount.createTaxHolding();
        };
    }

    @Override
    public Mono<PlatformAccount> getByType(PlatformAccountType accountType) {
        return accountRepository.findByAccountType(accountType);
    }

    @Override
    public Mono<PlatformAccount> findById(String id) {
        return accountRepository.findById(id);
    }

    @Override
    public Flux<PlatformAccount> getAllAccounts() {
        return accountRepository.findAll();
    }

    // ========================================================================
    // BALANCE OPERATIONS
    // ========================================================================

    @Override
    @Transactional
    public Mono<PlatformAccount> credit(
            PlatformAccountType accountType,
            BigDecimal amount,
            String reference,
            String description
    ) {
        log.info("Crediting {} to {} account: {}", amount, accountType, description);

        return getOrCreateAccount(accountType, "ZMW")
                .flatMap(account -> {
                    account.credit(amount);
                    return accountRepository.save(account)
                            .doOnSuccess(saved -> log.info("{} account credited. New balance: {}",
                                    accountType, saved.getBalance()));
                });
    }

    @Override
    @Transactional
    public Mono<PlatformAccount> debit(
            PlatformAccountType accountType,
            BigDecimal amount,
            String reference,
            String description
    ) {
        log.info("Debiting {} from {} account: {}", amount, accountType, description);

        return getOrCreateAccount(accountType, "ZMW")
                .flatMap(account -> {
                    if (!account.hasSufficientBalance(amount)) {
                        return Mono.error(new InsufficientEscrowBalanceException(
                                String.format("Insufficient %s balance. Required: %s, Available: %s",
                                        accountType, amount, account.getBalance())
                        ));
                    }
                    account.debit(amount);
                    return accountRepository.save(account)
                            .doOnSuccess(saved -> log.info("{} account debited. New balance: {}",
                                    accountType, saved.getBalance()));
                });
    }

    @Override
    @Transactional
    public Mono<TransferResult> transfer(
            PlatformAccountType fromType,
            PlatformAccountType toType,
            BigDecimal amount,
            String reference,
            String description
    ) {
        log.info("Transferring {} from {} to {}: {}", amount, fromType, toType, description);

        return debit(fromType, amount, reference, "Transfer out: " + description)
                .flatMap(fromAccount -> credit(toType, amount, reference, "Transfer in: " + description)
                        .map(toAccount -> new TransferResult(fromAccount, toAccount, amount)))
                .doOnSuccess(result -> log.info("Transfer completed: {} from {} to {}",
                        amount, fromType, toType));
    }

    // ========================================================================
    // BALANCE QUERIES
    // ========================================================================

    @Override
    public Mono<BigDecimal> getBalance(PlatformAccountType accountType) {
        return getByType(accountType)
                .map(PlatformAccount::getBalance)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    @Override
    public Mono<BigDecimal> getTotalBalance() {
        return accountRepository.getTotalPlatformBalance();
    }

    @Override
    public Mono<Boolean> hasSufficientBalance(PlatformAccountType accountType, BigDecimal amount) {
        return getBalance(accountType)
                .map(balance -> balance.compareTo(amount) >= 0);
    }

    // ========================================================================
    // RESERVE MANAGEMENT
    // ========================================================================

    @Override
    public Mono<BigDecimal> getReserveShortfall(BigDecimal minimumBalance) {
        return getBalance(PlatformAccountType.RESERVE)
                .map(balance -> {
                    BigDecimal shortfall = minimumBalance.subtract(balance);
                    return shortfall.compareTo(BigDecimal.ZERO) > 0 ? shortfall : BigDecimal.ZERO;
                });
    }

    @Override
    @Transactional
    public Mono<PlatformAccount> recoverFromReserve(String chargebackId, BigDecimal amount) {
        log.info("Recovering {} from reserve for chargeback: {}", amount, chargebackId);

        return debit(
                PlatformAccountType.RESERVE,
                amount,
                chargebackId,
                "Chargeback recovery: " + chargebackId
        );
    }

    @Override
    @Transactional
    public Mono<PlatformAccount> replenishReserve(BigDecimal amount, String source, String reference) {
        log.info("Replenishing reserve with {} from {}", amount, source);

        return credit(
                PlatformAccountType.RESERVE,
                amount,
                reference,
                "Reserve replenishment from: " + source
        );
    }

    // ========================================================================
    // RECONCILIATION
    // ========================================================================

    @Override
    public Mono<BigDecimal> getOperatingBalanceForReconciliation() {
        return getBalance(PlatformAccountType.OPERATING);
    }

    @Override
    @Transactional
    public Mono<PlatformAccount> recordReconciliationAdjustment(
            PlatformAccountType accountType,
            BigDecimal adjustment,
            String reason,
            String approvedBy
    ) {
        log.info("Recording reconciliation adjustment of {} for {} account: {}",
                adjustment, accountType, reason);

        if (adjustment.compareTo(BigDecimal.ZERO) > 0) {
            return credit(accountType, adjustment, "RECON-ADJ-" + System.currentTimeMillis(),
                    "Reconciliation adjustment (approved by " + approvedBy + "): " + reason);
        } else {
            return debit(accountType, adjustment.abs(), "RECON-ADJ-" + System.currentTimeMillis(),
                    "Reconciliation adjustment (approved by " + approvedBy + "): " + reason);
        }
    }
}
