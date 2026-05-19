package com.pml.booking.service;

import com.pml.booking.domain.enums.AccountSubType;
import com.pml.booking.domain.enums.AccountType;
import com.pml.booking.domain.model.ChartOfAccountsEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Chart of Accounts Service Interface
 *
 * <p>Manages the Chart of Accounts (CoA) which is the foundation of the double-entry
 * bookkeeping system. Every financial transaction must reference valid accounts
 * from this chart.</p>
 *
 * <h2>Account Code Structure</h2>
 * <pre>
 * 1000-1999 : ASSETS
 *   1011    : Primary Operating Bank Account
 *   1012    : Escrow Bank Account
 *   1021    : Gateway Settlement Receivable
 *   1022    : Commission Receivable (pending)
 *   1023    : Chargeback Recovery Receivable
 *
 * 2000-2999 : LIABILITIES
 *   2010    : Event Escrow (parent)
 *   2011-XXXX: Dynamic per-event escrow accounts
 *   2021    : Organizer Payouts Payable
 *   2022    : Customer Refunds Payable
 *   2023    : Tax Withholding Payable
 *   2031    : Deferred Commission Revenue
 *
 * 3000-3999 : EQUITY
 *   3010    : Retained Earnings
 *
 * 4000-4999 : REVENUE
 *   4010    : Commission Revenue
 *   4020    : Payout Processing Fee Revenue
 *
 * 5000-5999 : EXPENSES
 *   5010    : Payment Gateway Fees
 *   5020    : Chargeback Losses
 *   5040    : Bad Debt Expense
 * </pre>
 *
 * <h2>Account Lifecycle</h2>
 * <ol>
 *   <li>Create: Account added to chart (seed or dynamic)</li>
 *   <li>Active: Account can receive journal entries</li>
 *   <li>Inactive: Account preserved for history but cannot receive new entries</li>
 * </ol>
 *
 * <h2>Business Rules</h2>
 * <ul>
 *   <li>Account codes must be unique across the entire chart</li>
 *   <li>Parent accounts cannot be deleted if child accounts exist</li>
 *   <li>Account type determines normal balance (debit or credit)</li>
 *   <li>Inactive accounts cannot receive new journal entries</li>
 * </ul>
 *
 * @see com.pml.booking.domain.model.ChartOfAccountsEntry
 * @see com.pml.booking.service.JournalService
 * @since 1.0.0
 */
public interface ChartOfAccountsService {

    // ========================================================================
    // ACCOUNT CREATION
    // ========================================================================

    /**
     * Creates a new account in the Chart of Accounts.
     *
     * <p>Validates that:</p>
     * <ul>
     *   <li>Account code is unique</li>
     *   <li>Parent account exists (if specified)</li>
     *   <li>Account type is valid</li>
     * </ul>
     *
     * @param accountCode       Unique account identifier (e.g., "1011")
     * @param accountName       Human-readable name (e.g., "Primary Operating Bank Account")
     * @param accountType       Primary type (ASSET, LIABILITY, etc.)
     * @param subType           Detailed classification
     * @param parentAccountCode Parent account code (optional)
     * @param currency          Currency code (e.g., "ZMW")
     * @param description       Detailed description of account purpose
     * @return Created chart of accounts entry
     */
    Mono<ChartOfAccountsEntry> createAccount(
            String accountCode,
            String accountName,
            AccountType accountType,
            AccountSubType subType,
            String parentAccountCode,
            String currency,
            String description
    );

    /**
     * Creates a dynamic escrow account for a specific event.
     *
     * <p>Generates an account code in the format 2011-{eventId} under
     * the Event Escrow parent account (2010).</p>
     *
     * @param eventId   The event ID
     * @param eventName The event name (used in account name)
     * @param currency  Currency code
     * @return Created escrow account entry
     */
    Mono<ChartOfAccountsEntry> createEventEscrowAccount(
            String eventId,
            String eventName,
            String currency
    );

    // ========================================================================
    // ACCOUNT UPDATES
    // ========================================================================

    /**
     * Updates an existing account's mutable fields.
     *
     * <p>Only the following fields can be updated:</p>
     * <ul>
     *   <li>accountName</li>
     *   <li>description</li>
     * </ul>
     *
     * <p>Account type, code, and parent cannot be changed after creation.</p>
     *
     * @param accountCode Account code to update
     * @param accountName New account name (null to keep existing)
     * @param description New description (null to keep existing)
     * @return Updated account entry
     */
    Mono<ChartOfAccountsEntry> updateAccount(
            String accountCode,
            String accountName,
            String description
    );

    /**
     * Deactivates an account.
     *
     * <p>Deactivated accounts:</p>
     * <ul>
     *   <li>Cannot receive new journal entries</li>
     *   <li>Are preserved for historical reporting</li>
     *   <li>Can be reactivated if needed</li>
     * </ul>
     *
     * @param accountCode Account code to deactivate
     * @return Updated (inactive) account entry
     */
    Mono<ChartOfAccountsEntry> deactivateAccount(String accountCode);

    /**
     * Reactivates a previously deactivated account.
     *
     * @param accountCode Account code to reactivate
     * @return Updated (active) account entry
     */
    Mono<ChartOfAccountsEntry> reactivateAccount(String accountCode);

    // ========================================================================
    // ACCOUNT QUERIES
    // ========================================================================

    /**
     * Finds an account by its unique code.
     *
     * @param accountCode The account code
     * @return The account entry, or empty if not found
     */
    Mono<ChartOfAccountsEntry> findByAccountCode(String accountCode);

    /**
     * Finds all accounts of a specific type.
     *
     * @param accountType The account type to filter by
     * @return All matching accounts
     */
    Flux<ChartOfAccountsEntry> findByAccountType(AccountType accountType);

    /**
     * Finds all accounts of a specific sub-type.
     *
     * @param subType The sub-type to filter by
     * @return All matching accounts
     */
    Flux<ChartOfAccountsEntry> findBySubType(AccountSubType subType);

    /**
     * Finds all child accounts of a parent account.
     *
     * @param parentAccountCode The parent account code
     * @return All child accounts
     */
    Flux<ChartOfAccountsEntry> findByParentAccountCode(String parentAccountCode);

    /**
     * Finds all active accounts.
     *
     * @return All active accounts in the chart
     */
    Flux<ChartOfAccountsEntry> findAllActive();

    /**
     * Finds all accounts (including inactive).
     *
     * @return All accounts in the chart
     */
    Flux<ChartOfAccountsEntry> findAll();

    // ========================================================================
    // VALIDATION
    // ========================================================================

    /**
     * Validates that an account code exists and is active.
     *
     * <p>Used by JournalService before posting entries.</p>
     *
     * @param accountCode The account code to validate
     * @return Mono completing successfully if valid, error otherwise
     */
    Mono<Void> validateAccountCode(String accountCode);

    /**
     * Validates multiple account codes exist and are active.
     *
     * @param accountCodes The account codes to validate
     * @return Mono completing successfully if all valid, error otherwise
     */
    Mono<Void> validateAccountCodes(Iterable<String> accountCodes);

    /**
     * Checks if an account code exists.
     *
     * @param accountCode The account code to check
     * @return true if exists, false otherwise
     */
    Mono<Boolean> existsByAccountCode(String accountCode);

    // ========================================================================
    // SEEDING
    // ========================================================================

    /**
     * Seeds the chart of accounts with standard accounts.
     *
     * <p>Called during application startup or via admin mutation.
     * Idempotent - will not create duplicate accounts.</p>
     *
     * @return true if seeding performed, false if already seeded
     */
    Mono<Boolean> seedStandardAccounts();

    /**
     * Checks if the chart of accounts has been seeded.
     *
     * @return true if seeded, false otherwise
     */
    Mono<Boolean> isSeeded();
}
