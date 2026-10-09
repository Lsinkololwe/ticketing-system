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
}
