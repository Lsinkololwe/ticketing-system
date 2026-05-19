package com.pml.booking.repository;

import com.pml.booking.domain.enums.AccountSubType;
import com.pml.booking.domain.enums.AccountType;
import com.pml.booking.domain.model.ChartOfAccountsEntry;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive Repository for Chart of Accounts Entries
 *
 * Provides reactive access to the chart_of_accounts collection in MongoDB.
 * This repository is the foundation for all double-entry bookkeeping operations.
 *
 * <h2>Key Operations</h2>
 * <ul>
 *   <li><b>Account Lookup</b>: Find accounts by code for journal entry validation</li>
 *   <li><b>Hierarchy Navigation</b>: Find child accounts for roll-up reporting</li>
 *   <li><b>Type Filtering</b>: Find accounts by type for financial reports</li>
 *   <li><b>Active Account Lists</b>: Get accounts available for use in entries</li>
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li>accountCode lookup is indexed and fast (unique index)</li>
 *   <li>Type and subType queries use compound indexes</li>
 *   <li>Parent queries use dedicated index for hierarchy navigation</li>
 * </ul>
 *
 * <h2>Usage Patterns</h2>
 * <pre>
 * // Validate account exists before creating journal entry
 * repository.findByAccountCode("1021")
 *     .switchIfEmpty(Mono.error(new AccountNotFoundException("1021")));
 *
 * // Get all active asset accounts for reporting
 * repository.findByAccountTypeAndIsActiveTrue(AccountType.ASSET)
 *     .collectList();
 *
 * // Find all escrow sub-accounts
 * repository.findByParentAccountCode("2010")
 *     .collectList();
 * </pre>
 *
 * @see ChartOfAccountsEntry
 * @since 1.0.0
 */
@Repository
public interface ChartOfAccountsRepository extends ReactiveMongoRepository<ChartOfAccountsEntry, String> {

    // ========================================================================
    // ACCOUNT CODE LOOKUPS (Primary Identifier)
    // ========================================================================

    /**
     * Find account by unique account code.
     *
     * <p>This is the primary lookup method used when validating journal entries.
     * The account code is the business identifier used in all financial transactions.</p>
     *
     * @param accountCode The unique account code (e.g., "1021", "2010-0001")
     * @return Mono containing the account if found, empty otherwise
     */
    Mono<ChartOfAccountsEntry> findByAccountCode(String accountCode);

    /**
     * Check if an account with the given code exists.
     *
     * <p>Use for validation before creating journal entries or new accounts.</p>
     *
     * @param accountCode The account code to check
     * @return Mono<Boolean> true if exists, false otherwise
     */
    Mono<Boolean> existsByAccountCode(String accountCode);

    // ========================================================================
    // TYPE-BASED QUERIES (Financial Reporting)
    // ========================================================================

    /**
     * Find all accounts of a specific type.
     *
     * <p>Useful for generating financial reports by category:</p>
     * <ul>
     *   <li>Balance Sheet: ASSET, LIABILITY, EQUITY accounts</li>
     *   <li>Income Statement: REVENUE, EXPENSE accounts</li>
     * </ul>
     *
     * @param accountType The account type to filter by
     * @return Flux of matching accounts
     */
    Flux<ChartOfAccountsEntry> findByAccountType(AccountType accountType);

    /**
     * Find active accounts of a specific type.
     *
     * <p>For UI dropdowns and entry forms, show only active accounts.</p>
     *
     * @param accountType The account type to filter by
     * @return Flux of active accounts of this type
     */
    Flux<ChartOfAccountsEntry> findByAccountTypeAndIsActiveTrue(AccountType accountType);

    /**
     * Find accounts by sub-type.
     *
     * <p>For more granular filtering in reports:</p>
     * <ul>
     *   <li>All bank accounts (BANK_ACCOUNT)</li>
     *   <li>All escrow payables (ESCROW_PAYABLE)</li>
     *   <li>All revenue accounts (COMMISSION_REVENUE, FEE_REVENUE)</li>
     * </ul>
     *
     * @param subType The sub-type to filter by
     * @return Flux of matching accounts
     */
    Flux<ChartOfAccountsEntry> findBySubType(AccountSubType subType);

    /**
     * Find active accounts by sub-type.
     *
     * @param subType The sub-type to filter by
     * @return Flux of active accounts with this sub-type
     */
    Flux<ChartOfAccountsEntry> findBySubTypeAndIsActiveTrue(AccountSubType subType);

    // ========================================================================
    // HIERARCHY QUERIES (Parent-Child Navigation)
    // ========================================================================

    /**
     * Find all child accounts of a parent account.
     *
     * <p>Used for:</p>
     * <ul>
     *   <li>Roll-up reporting (sum all child balances)</li>
     *   <li>Tree navigation in UI</li>
     *   <li>Finding all event escrow accounts (parent = "2010")</li>
     * </ul>
     *
     * @param parentAccountCode The parent account code
     * @return Flux of child accounts
     */
    Flux<ChartOfAccountsEntry> findByParentAccountCode(String parentAccountCode);

    /**
     * Find active child accounts of a parent.
     *
     * @param parentAccountCode The parent account code
     * @return Flux of active child accounts
     */
    Flux<ChartOfAccountsEntry> findByParentAccountCodeAndIsActiveTrue(String parentAccountCode);

    /**
     * Count child accounts under a parent.
     *
     * <p>Useful for UI display (show count badge) or validation
     * (prevent deactivation if has active children).</p>
     *
     * @param parentAccountCode The parent account code
     * @return Mono<Long> count of child accounts
     */
    Mono<Long> countByParentAccountCode(String parentAccountCode);

    // ========================================================================
    // ACTIVE/INACTIVE QUERIES
    // ========================================================================

    /**
     * Find all active accounts.
     *
     * <p>Primary query for populating account selection dropdowns.</p>
     *
     * @return Flux of all active accounts
     */
    Flux<ChartOfAccountsEntry> findByIsActiveTrue();

    /**
     * Find all inactive accounts.
     *
     * <p>For admin view to see deactivated accounts.</p>
     *
     * @return Flux of inactive accounts
     */
    Flux<ChartOfAccountsEntry> findByIsActiveFalse();

    /**
     * Count active accounts.
     *
     * @return Mono<Long> count of active accounts
     */
    Mono<Long> countByIsActiveTrue();

    /**
     * Count inactive accounts.
     *
     * @return Mono<Long> count of inactive accounts
     */
    Mono<Long> countByIsActiveFalse();

    // ========================================================================
    // COMBINED QUERIES
    // ========================================================================

    /**
     * Find active accounts by type and sub-type.
     *
     * <p>For precise filtering in specialized reports or forms.</p>
     *
     * @param accountType The account type
     * @param subType The sub-type
     * @return Flux of matching active accounts
     */
    Flux<ChartOfAccountsEntry> findByAccountTypeAndSubTypeAndIsActiveTrue(
            AccountType accountType,
            AccountSubType subType
    );

    // ========================================================================
    // SEARCH QUERIES
    // ========================================================================

    /**
     * Find accounts with name containing search term (case-insensitive).
     *
     * <p>For account search functionality in admin UI.</p>
     *
     * @param searchTerm The search term
     * @return Flux of accounts with matching names
     */
    Flux<ChartOfAccountsEntry> findByAccountNameContainingIgnoreCase(String searchTerm);

    /**
     * Find accounts where code starts with a prefix.
     *
     * <p>Useful for finding accounts in a range (e.g., all 1000-series accounts).</p>
     *
     * @param prefix The account code prefix
     * @return Flux of accounts with matching code prefix
     */
    Flux<ChartOfAccountsEntry> findByAccountCodeStartingWith(String prefix);

    // ========================================================================
    // CURRENCY QUERIES
    // ========================================================================

    /**
     * Find accounts by currency.
     *
     * <p>For multi-currency reporting or currency-specific operations.</p>
     *
     * @param currency The currency code (e.g., "ZMW", "USD")
     * @return Flux of accounts in that currency
     */
    Flux<ChartOfAccountsEntry> findByCurrency(String currency);

    /**
     * Find active accounts by currency.
     *
     * @param currency The currency code
     * @return Flux of active accounts in that currency
     */
    Flux<ChartOfAccountsEntry> findByCurrencyAndIsActiveTrue(String currency);
}
