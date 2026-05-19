package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.AccountSubType;
import com.pml.booking.domain.enums.AccountType;
import com.pml.booking.domain.enums.BalanceDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Chart of Accounts Entry - Foundation for Double-Entry Bookkeeping
 *
 * The Chart of Accounts (CoA) is the backbone of any accounting system. It's
 * a complete listing of every account in the company's general ledger, organized
 * by category and used to track all financial transactions.
 *
 * <h2>Account Code Structure</h2>
 * <p>Account codes follow a hierarchical numbering system:</p>
 * <pre>
 * 1000-1999: ASSETS
 *   1010-1019: Bank Accounts
 *   1020-1029: Receivables
 *
 * 2000-2999: LIABILITIES
 *   2010-2019: Escrow Payables
 *   2020-2029: Other Payables
 *   2030-2039: Deferred Revenue
 *
 * 3000-3999: EQUITY
 *   3010: Retained Earnings
 *
 * 4000-4999: REVENUE
 *   4010: Commission Revenue
 *   4020: Fee Revenue
 *
 * 5000-5999: EXPENSES
 *   5010: Gateway Fees
 *   5020: Chargeback Losses
 *   5040: Bad Debt
 * </pre>
 *
 * <h2>Account Lifecycle</h2>
 * <pre>
 *     ┌────────────┐
 *     │   ACTIVE   │ ← Account can be used in journal entries
 *     └─────┬──────┘
 *           │ deactivate()
 *           ▼
 *     ┌────────────┐
 *     │  INACTIVE  │ ← Account cannot be used, preserved for history
 *     └────────────┘
 * </pre>
 *
 * <h2>Usage in Journal Entries</h2>
 * <p>Each journal line references an account by its accountCode:</p>
 * <pre>
 * JournalEntry:
 *   Line 1: DR accountCode="1021" amount=K100 (Gateway Receivable)
 *   Line 2: CR accountCode="2010-0001" amount=K90 (Event Escrow)
 *   Line 3: CR accountCode="4010" amount=K10 (Commission Revenue)
 * </pre>
 *
 * <h2>Important Design Decisions</h2>
 * <ul>
 *   <li><b>accountCode is unique</b>: Used as the identifier in journal entries</li>
 *   <li><b>Accounts cannot be deleted</b>: Only deactivated to preserve audit trail</li>
 *   <li><b>parentAccountCode enables hierarchy</b>: For roll-up reporting</li>
 *   <li><b>Event escrow accounts</b>: Dynamic sub-accounts (2010-0001, 2010-0002)</li>
 * </ul>
 *
 * @see AccountType
 * @see AccountSubType
 * @see JournalEntry
 * @since 1.0.0
 */
@Document(collection = "chart_of_accounts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "type_active_idx", def = "{'accountType': 1, 'isActive': 1}"),
    @CompoundIndex(name = "subtype_active_idx", def = "{'subType': 1, 'isActive': 1}"),
    @CompoundIndex(name = "parent_idx", def = "{'parentAccountCode': 1}")
})
public class ChartOfAccountsEntry {

    /**
     * MongoDB document ID.
     * Auto-generated, used internally by MongoDB.
     */
    @Id
    private String id;

    /**
     * Unique account code used in journal entries.
     *
     * <p>Format: 4-digit number or 4-digit + suffix</p>
     * <ul>
     *   <li>Standard accounts: "1011", "2010", "4010"</li>
     *   <li>Dynamic escrow accounts: "2010-0001", "2010-EVT123"</li>
     * </ul>
     *
     * <p>This is the primary business identifier and is used in all
     * journal entries. It MUST be unique and SHOULD NOT change once
     * created (immutable in practice).</p>
     */
    @NotBlank(message = "Account code is required")
    @Indexed(unique = true)
    private String accountCode;

    /**
     * Human-readable account name.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"Primary Operating Bank Account"</li>
     *   <li>"Gateway Settlement Receivable"</li>
     *   <li>"Event Escrow - Lusaka Jazz Festival 2024"</li>
     * </ul>
     */
    @NotBlank(message = "Account name is required")
    private String accountName;

    /**
     * Primary account type classification.
     *
     * @see AccountType
     */
    @NotNull(message = "Account type is required")
    @Indexed
    private AccountType accountType;

    /**
     * Sub-type for more granular classification.
     *
     * <p>Optional but recommended for proper categorization in reports.</p>
     *
     * @see AccountSubType
     */
    private AccountSubType subType;

    /**
     * Parent account code for hierarchical structure.
     *
     * <p>Used for:</p>
     * <ul>
     *   <li>Roll-up reporting (sum child accounts into parent)</li>
     *   <li>Tree navigation in UI</li>
     *   <li>Per-event escrow accounts (parent = "2010")</li>
     * </ul>
     *
     * <p>Example: Account "2010-0001" has parentAccountCode "2010"</p>
     */
    private String parentAccountCode;

    /**
     * Currency code for this account.
     *
     * <p>Defaults to ZMW (Zambian Kwacha) as the platform's primary currency.</p>
     *
     * <p>Multi-currency considerations:</p>
     * <ul>
     *   <li>Each account tracks one currency</li>
     *   <li>For multi-currency, create separate accounts (e.g., 1011-ZMW, 1011-USD)</li>
     *   <li>Currency conversion handled at transaction level</li>
     * </ul>
     */
    @Builder.Default
    private String currency = "ZMW";

    /**
     * Whether this account is active and can be used in journal entries.
     *
     * <p>Inactive accounts:</p>
     * <ul>
     *   <li>Cannot be used in new journal entries</li>
     *   <li>Preserved for historical reporting</li>
     *   <li>Balance is typically zero</li>
     * </ul>
     */
    @Builder.Default
    @Indexed
    private Boolean isActive = true;

    /**
     * Optional description providing context about the account.
     *
     * <p>Use for:</p>
     * <ul>
     *   <li>Explaining account purpose</li>
     *   <li>Documenting usage rules</li>
     *   <li>Recording special handling instructions</li>
     * </ul>
     */
    private String description;

    /**
     * Timestamp when the account was created.
     * Auto-populated by Spring Data MongoDB.
     */
    @CreatedDate
    private Instant createdAt;

    /**
     * Timestamp of last modification.
     * Auto-updated by Spring Data MongoDB.
     */
    @LastModifiedDate
    private Instant updatedAt;

    /**
     * Version for optimistic locking.
     *
     * <p>Prevents concurrent modifications. If two users try to update
     * the same account simultaneously, one will fail with OptimisticLockingFailureException.</p>
     */
    @Version
    private Long version;

    // ========================================================================
    // BUSINESS METHODS
    // ========================================================================

    /**
     * Returns the normal balance direction for this account.
     *
     * <p>The normal balance determines whether debits or credits increase
     * the account balance:</p>
     * <ul>
     *   <li>ASSET, EXPENSE: Normal balance is DEBIT (debits increase balance)</li>
     *   <li>LIABILITY, EQUITY, REVENUE: Normal balance is CREDIT (credits increase balance)</li>
     * </ul>
     *
     * <p>This is crucial for generating correct financial reports and
     * validating journal entries.</p>
     *
     * @return DEBIT or CREDIT based on account type
     */
    public BalanceDirection getNormalBalance() {
        return accountType.getNormalBalance();
    }

    /**
     * Checks if this account is a dynamic/child account.
     *
     * <p>Dynamic accounts are created programmatically (e.g., per-event
     * escrow accounts) and have a parent reference.</p>
     *
     * @return true if this account has a parent account
     */
    public boolean isDynamicAccount() {
        return parentAccountCode != null && !parentAccountCode.isBlank();
    }

    /**
     * Checks if this account belongs to the balance sheet.
     *
     * <p>Balance sheet accounts (Assets, Liabilities, Equity) carry
     * forward their balances from period to period.</p>
     *
     * @return true if this is a balance sheet account
     */
    public boolean isBalanceSheetAccount() {
        return accountType.isBalanceSheetAccount();
    }

    /**
     * Checks if this account belongs to the income statement.
     *
     * <p>Income statement accounts (Revenue, Expenses) are closed to
     * retained earnings at the end of each accounting period.</p>
     *
     * @return true if this is an income statement account
     */
    public boolean isIncomeStatementAccount() {
        return accountType.isIncomeStatementAccount();
    }

    /**
     * Deactivates the account.
     *
     * <p>Deactivated accounts:</p>
     * <ul>
     *   <li>Cannot be used in new journal entries</li>
     *   <li>Are preserved for historical reporting</li>
     *   <li>Should have zero balance before deactivation</li>
     * </ul>
     *
     * @throws IllegalStateException if account is already inactive
     */
    public void deactivate() {
        if (!this.isActive) {
            throw new IllegalStateException("Account " + accountCode + " is already inactive");
        }
        this.isActive = false;
    }

    /**
     * Reactivates a previously deactivated account.
     *
     * <p>Use sparingly - typically only to correct an accidental deactivation.</p>
     *
     * @throws IllegalStateException if account is already active
     */
    public void reactivate() {
        if (this.isActive) {
            throw new IllegalStateException("Account " + accountCode + " is already active");
        }
        this.isActive = true;
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates a standard account entry.
     *
     * @param accountCode Unique account code
     * @param accountName Human-readable name
     * @param accountType Primary type (ASSET, LIABILITY, etc.)
     * @param subType Sub-type for categorization
     * @param description Optional description
     * @return New ChartOfAccountsEntry instance
     */
    public static ChartOfAccountsEntry create(
            String accountCode,
            String accountName,
            AccountType accountType,
            AccountSubType subType,
            String description
    ) {
        return ChartOfAccountsEntry.builder()
                .accountCode(accountCode)
                .accountName(accountName)
                .accountType(accountType)
                .subType(subType)
                .description(description)
                .isActive(true)
                .build();
    }

    /**
     * Creates a standard account entry with parent and currency.
     *
     * @param accountCode Unique account code
     * @param accountName Human-readable name
     * @param accountType Primary type (ASSET, LIABILITY, etc.)
     * @param subType Sub-type for categorization (may be null)
     * @param parentAccountCode Parent account code (may be null)
     * @param currency Currency code (e.g., "ZMW")
     * @param description Optional description
     * @return New ChartOfAccountsEntry instance
     */
    public static ChartOfAccountsEntry create(
            String accountCode,
            String accountName,
            AccountType accountType,
            AccountSubType subType,
            String parentAccountCode,
            String currency,
            String description
    ) {
        return ChartOfAccountsEntry.builder()
                .accountCode(accountCode)
                .accountName(accountName)
                .accountType(accountType)
                .subType(subType)
                .parentAccountCode(parentAccountCode)
                .currency(currency != null ? currency : "ZMW")
                .description(description)
                .isActive(true)
                .build();
    }

    /**
     * Creates a dynamic child account (e.g., per-event escrow).
     *
     * @param accountCode Unique account code (e.g., "2010-0001")
     * @param accountName Human-readable name
     * @param parentAccountCode Parent account code (e.g., "2010")
     * @param accountType Primary type
     * @param subType Sub-type
     * @param description Optional description
     * @return New ChartOfAccountsEntry instance with parent reference
     */
    public static ChartOfAccountsEntry createDynamicAccount(
            String accountCode,
            String accountName,
            String parentAccountCode,
            AccountType accountType,
            AccountSubType subType,
            String description
    ) {
        return ChartOfAccountsEntry.builder()
                .accountCode(accountCode)
                .accountName(accountName)
                .parentAccountCode(parentAccountCode)
                .accountType(accountType)
                .subType(subType)
                .description(description)
                .isActive(true)
                .build();
    }

    /**
     * Creates an event-specific escrow account.
     *
     * <p>Escrow accounts follow the pattern: 2010-{eventIdPrefix}</p>
     *
     * @param eventId Event identifier
     * @param eventName Event name for account name
     * @return New escrow account entry
     */
    public static ChartOfAccountsEntry createEventEscrowAccount(
            String eventId,
            String eventName
    ) {
        String accountCode = "2010-" + eventId.substring(0, Math.min(8, eventId.length())).toUpperCase();
        String accountName = "Event Escrow - " + eventName;

        return ChartOfAccountsEntry.builder()
                .accountCode(accountCode)
                .accountName(accountName)
                .parentAccountCode("2010")
                .accountType(AccountType.LIABILITY)
                .subType(AccountSubType.ESCROW_PAYABLE)
                .description("Escrow account for event: " + eventName)
                .isActive(true)
                .build();
    }
}
