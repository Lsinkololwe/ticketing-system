package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.AccountSubType;
import com.pml.booking.domain.enums.AccountType;
import com.pml.booking.domain.model.ChartOfAccountsEntry;
import com.pml.booking.exception.AccountNotFoundException;
import com.pml.booking.exception.InactiveAccountException;
import com.pml.booking.repository.ChartOfAccountsRepository;
import com.pml.booking.service.ChartOfAccountsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Chart of Accounts Service Implementation
 *
 * <p>Manages the Chart of Accounts (CoA) which provides the foundation for
 * the double-entry bookkeeping system. All financial transactions reference
 * accounts defined in this chart.</p>
 *
 * <h2>Standard Account Structure</h2>
 * <pre>
 * 1000-1999 : ASSETS        (Normal balance: DEBIT)
 * 2000-2999 : LIABILITIES   (Normal balance: CREDIT)
 * 3000-3999 : EQUITY        (Normal balance: CREDIT)
 * 4000-4999 : REVENUE       (Normal balance: CREDIT)
 * 5000-5999 : EXPENSES      (Normal balance: DEBIT)
 * </pre>
 *
 * @see ChartOfAccountsService
 * @see ChartOfAccountsEntry
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChartOfAccountsServiceImpl implements ChartOfAccountsService {

    private final ChartOfAccountsRepository accountsRepository;

    // ========================================================================
    // ACCOUNT CREATION
    // ========================================================================

    @Override
    @Transactional
    public Mono<ChartOfAccountsEntry> createAccount(
            String accountCode,
            String accountName,
            AccountType accountType,
            AccountSubType subType,
            String parentAccountCode,
            String currency,
            String description
    ) {
        log.info("Creating account: {} - {}", accountCode, accountName);

        // Validate parent account exists if specified
        Mono<Void> parentValidation = parentAccountCode != null
                ? validateAccountCode(parentAccountCode)
                : Mono.empty();

        return parentValidation
                .then(accountsRepository.existsByAccountCode(accountCode))
                .flatMap(exists -> {
                    if (exists) {
                        log.warn("Account already exists: {}", accountCode);
                        return accountsRepository.findByAccountCode(accountCode);
                    }

                    ChartOfAccountsEntry account = ChartOfAccountsEntry.create(
                            accountCode,
                            accountName,
                            accountType,
                            subType,
                            parentAccountCode,
                            currency,
                            description
                    );

                    return accountsRepository.save(account)
                            .doOnSuccess(saved -> log.info("Account created: {}", saved.getAccountCode()));
                });
    }

    @Override
    @Transactional
    public Mono<ChartOfAccountsEntry> createEventEscrowAccount(
            String eventId,
            String eventName,
            String currency
    ) {
        // Generate account code: 2011-{eventId} (truncate if needed)
        String shortEventId = eventId.length() > 8 ? eventId.substring(0, 8) : eventId;
        String accountCode = "2011-" + shortEventId;
        String accountName = "Event Escrow - " + eventName;

        log.info("Creating event escrow account: {} for event {}", accountCode, eventId);

        return createAccount(
                accountCode,
                accountName,
                AccountType.LIABILITY,
                AccountSubType.ESCROW_PAYABLE,
                "2010",  // Parent: Event Escrow (master)
                currency,
                "Escrow account for event: " + eventName + " (ID: " + eventId + ")"
        );
    }

    // ========================================================================
    // ACCOUNT UPDATES
    // ========================================================================

    @Override
    @Transactional
    public Mono<ChartOfAccountsEntry> updateAccount(
            String accountCode,
            String accountName,
            String description
    ) {
        log.info("Updating account: {}", accountCode);

        return accountsRepository.findByAccountCode(accountCode)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(accountCode)))
                .flatMap(account -> {
                    if (accountName != null) {
                        account.setAccountName(accountName);
                    }
                    if (description != null) {
                        account.setDescription(description);
                    }
                    return accountsRepository.save(account);
                })
                .doOnSuccess(updated -> log.info("Account updated: {}", updated.getAccountCode()));
    }

    @Override
    @Transactional
    public Mono<ChartOfAccountsEntry> deactivateAccount(String accountCode) {
        log.info("Deactivating account: {}", accountCode);

        return accountsRepository.findByAccountCode(accountCode)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(accountCode)))
                .flatMap(account -> {
                    account.deactivate();
                    return accountsRepository.save(account);
                })
                .doOnSuccess(deactivated -> log.info("Account deactivated: {}", deactivated.getAccountCode()));
    }

    @Override
    @Transactional
    public Mono<ChartOfAccountsEntry> reactivateAccount(String accountCode) {
        log.info("Reactivating account: {}", accountCode);

        return accountsRepository.findByAccountCode(accountCode)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(accountCode)))
                .flatMap(account -> {
                    account.reactivate();
                    return accountsRepository.save(account);
                })
                .doOnSuccess(reactivated -> log.info("Account reactivated: {}", reactivated.getAccountCode()));
    }

    // ========================================================================
    // ACCOUNT QUERIES
    // ========================================================================

    @Override
    public Mono<ChartOfAccountsEntry> findByAccountCode(String accountCode) {
        return accountsRepository.findByAccountCode(accountCode);
    }

    @Override
    public Flux<ChartOfAccountsEntry> findByAccountType(AccountType accountType) {
        return accountsRepository.findByAccountType(accountType);
    }

    @Override
    public Flux<ChartOfAccountsEntry> findBySubType(AccountSubType subType) {
        return accountsRepository.findBySubType(subType);
    }

    @Override
    public Flux<ChartOfAccountsEntry> findByParentAccountCode(String parentAccountCode) {
        return accountsRepository.findByParentAccountCode(parentAccountCode);
    }

    @Override
    public Flux<ChartOfAccountsEntry> findAllActive() {
        return accountsRepository.findByIsActiveTrue();
    }

    @Override
    public Flux<ChartOfAccountsEntry> findAll() {
        return accountsRepository.findAll();
    }

    // ========================================================================
    // VALIDATION
    // ========================================================================

    @Override
    public Mono<Void> validateAccountCode(String accountCode) {
        return accountsRepository.findByAccountCode(accountCode)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(accountCode)))
                .flatMap(account -> {
                    if (!account.getIsActive()) {
                        return Mono.error(new InactiveAccountException(
                                account.getAccountCode(),
                                account.getAccountName()
                        ));
                    }
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> validateAccountCodes(Iterable<String> accountCodes) {
        return Flux.fromIterable(accountCodes)
                .flatMap(this::validateAccountCode)
                .then();
    }

    @Override
    public Mono<Boolean> existsByAccountCode(String accountCode) {
        return accountsRepository.existsByAccountCode(accountCode);
    }

    // ========================================================================
    // SEEDING
    // ========================================================================

    @Override
    @Transactional
    public Mono<Boolean> seedStandardAccounts() {
        log.info("Seeding standard chart of accounts...");

        return isSeeded()
                .flatMap(alreadySeeded -> {
                    if (alreadySeeded) {
                        log.info("Chart of accounts already seeded");
                        return Mono.just(false);
                    }

                    return seedAllAccounts()
                            .then(Mono.just(true))
                            .doOnSuccess(v -> log.info("Chart of accounts seeding completed"));
                });
    }

    @Override
    public Mono<Boolean> isSeeded() {
        // Check if at least one standard account exists
        return accountsRepository.existsByAccountCode("1011");
    }

    /**
     * Creates all standard accounts.
     */
    private Mono<Void> seedAllAccounts() {
        List<ChartOfAccountsEntry> standardAccounts = List.of(
                // ========== ASSETS (1000-1999) ==========
                ChartOfAccountsEntry.create("1000", "Assets", AccountType.ASSET, null, null, "ZMW",
                        "Parent account for all asset accounts"),
                ChartOfAccountsEntry.create("1011", "Primary Operating Bank Account", AccountType.ASSET,
                        AccountSubType.BANK_ACCOUNT, "1000", "ZMW",
                        "Main bank account for receiving settlements and disbursing payouts"),
                ChartOfAccountsEntry.create("1012", "Escrow Bank Account", AccountType.ASSET,
                        AccountSubType.BANK_ACCOUNT, "1000", "ZMW",
                        "Separate bank account holding escrow funds"),
                ChartOfAccountsEntry.create("1021", "Gateway Settlement Receivable", AccountType.ASSET,
                        AccountSubType.GATEWAY_RECEIVABLE, "1000", "ZMW",
                        "Amounts due from payment gateway for processed transactions"),
                ChartOfAccountsEntry.create("1022", "Commission Receivable", AccountType.ASSET,
                        AccountSubType.COMMISSION_RECEIVABLE, "1000", "ZMW",
                        "Pending commission amounts (before event completion)"),
                ChartOfAccountsEntry.create("1023", "Chargeback Recovery Receivable", AccountType.ASSET,
                        AccountSubType.CHARGEBACK_RECEIVABLE, "1000", "ZMW",
                        "Amounts to be recovered from organizers for chargebacks"),

                // ========== LIABILITIES (2000-2999) ==========
                ChartOfAccountsEntry.create("2000", "Liabilities", AccountType.LIABILITY, null, null, "ZMW",
                        "Parent account for all liability accounts"),
                ChartOfAccountsEntry.create("2010", "Event Escrow", AccountType.LIABILITY,
                        AccountSubType.ESCROW_PAYABLE, "2000", "ZMW",
                        "Parent account for per-event escrow accounts"),
                ChartOfAccountsEntry.create("2021", "Organizer Payouts Payable", AccountType.LIABILITY,
                        AccountSubType.PAYOUTS_PAYABLE, "2000", "ZMW",
                        "Amounts approved for payout to organizers"),
                ChartOfAccountsEntry.create("2022", "Customer Refunds Payable", AccountType.LIABILITY,
                        AccountSubType.REFUNDS_PAYABLE, "2000", "ZMW",
                        "Refunds approved but not yet disbursed"),
                ChartOfAccountsEntry.create("2023", "Tax Withholding Payable", AccountType.LIABILITY,
                        AccountSubType.TAX_PAYABLE, "2000", "ZMW",
                        "Taxes withheld pending remittance"),
                ChartOfAccountsEntry.create("2024", "Gateway Fees Payable", AccountType.LIABILITY,
                        AccountSubType.FEES_PAYABLE, "2000", "ZMW",
                        "Payment gateway fees pending deduction"),
                ChartOfAccountsEntry.create("2031", "Deferred Commission Revenue", AccountType.LIABILITY,
                        AccountSubType.DEFERRED_REVENUE, "2000", "ZMW",
                        "Commission collected but not yet earned (pending event completion)"),

                // ========== EQUITY (3000-3999) ==========
                ChartOfAccountsEntry.create("3000", "Equity", AccountType.EQUITY, null, null, "ZMW",
                        "Parent account for equity accounts"),
                ChartOfAccountsEntry.create("3010", "Retained Earnings", AccountType.EQUITY,
                        AccountSubType.RETAINED_EARNINGS, "3000", "ZMW",
                        "Accumulated earnings retained in the business"),
                ChartOfAccountsEntry.create("3020", "Platform Reserve", AccountType.EQUITY,
                        AccountSubType.RESERVE, "3000", "ZMW",
                        "Reserve fund for chargeback protection"),

                // ========== REVENUE (4000-4999) ==========
                ChartOfAccountsEntry.create("4000", "Revenue", AccountType.REVENUE, null, null, "ZMW",
                        "Parent account for revenue accounts"),
                ChartOfAccountsEntry.create("4010", "Commission Revenue", AccountType.REVENUE,
                        AccountSubType.COMMISSION_REVENUE, "4000", "ZMW",
                        "Earned commission from ticket sales"),
                ChartOfAccountsEntry.create("4020", "Payout Processing Fee Revenue", AccountType.REVENUE,
                        AccountSubType.FEE_REVENUE, "4000", "ZMW",
                        "Fees charged for processing organizer payouts"),
                ChartOfAccountsEntry.create("4099", "Reconciliation Variance Income", AccountType.REVENUE,
                        AccountSubType.OTHER_INCOME, "4000", "ZMW",
                        "Income from favorable reconciliation variances (unexpected gains)"),

                // ========== EXPENSES (5000-5999) ==========
                ChartOfAccountsEntry.create("5000", "Expenses", AccountType.EXPENSE, null, null, "ZMW",
                        "Parent account for expense accounts"),
                ChartOfAccountsEntry.create("5010", "Payment Gateway Fees", AccountType.EXPENSE,
                        AccountSubType.GATEWAY_FEES, "5000", "ZMW",
                        "Fees paid to payment gateway for processing transactions"),
                ChartOfAccountsEntry.create("5020", "Chargeback Losses", AccountType.EXPENSE,
                        AccountSubType.CHARGEBACK_LOSS, "5000", "ZMW",
                        "Losses from unrecovered chargebacks"),
                ChartOfAccountsEntry.create("5030", "Chargeback Fees", AccountType.EXPENSE,
                        AccountSubType.CHARGEBACK_FEES, "5000", "ZMW",
                        "Fees charged by gateway per chargeback"),
                ChartOfAccountsEntry.create("5040", "Bad Debt Expense", AccountType.EXPENSE,
                        AccountSubType.BAD_DEBT, "5000", "ZMW",
                        "Unrecoverable amounts written off"),
                ChartOfAccountsEntry.create("5099", "Reconciliation Variance Expense", AccountType.EXPENSE,
                        AccountSubType.OTHER_EXPENSE, "5000", "ZMW",
                        "Expense from unfavorable reconciliation variances (unexpected losses)")
        );

        return Flux.fromIterable(standardAccounts)
                .flatMap(account -> accountsRepository.existsByAccountCode(account.getAccountCode())
                        .flatMap(exists -> exists
                                ? Mono.empty()
                                : accountsRepository.save(account)))
                .then();
    }
}
