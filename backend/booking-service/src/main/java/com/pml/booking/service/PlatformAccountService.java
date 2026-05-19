package com.pml.booking.service;

import com.pml.booking.domain.enums.PlatformAccountType;
import com.pml.booking.domain.model.PlatformAccount;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Platform Account Service Interface
 *
 * <p>Manages platform-owned accounts that hold funds for specific purposes.
 * These are distinct from event escrow accounts (which hold organizer funds).</p>
 *
 * <h2>Platform Account Types</h2>
 *
 * <h3>OPERATING Account</h3>
 * <p>The main operating bank account where:</p>
 * <ul>
 *   <li>Gateway settlements are received</li>
 *   <li>Organizer payouts are disbursed from</li>
 *   <li>Platform expenses are paid from</li>
 * </ul>
 *
 * <h3>RESERVE Account</h3>
 * <p>Emergency fund for chargeback recovery when:</p>
 * <ul>
 *   <li>Organizer escrow is insufficient</li>
 *   <li>Organizer has no future payouts to deduct from</li>
 * </ul>
 * <p>Should maintain minimum balance (e.g., 5% of monthly GMV).</p>
 *
 * <h3>TAX_HOLDING Account</h3>
 * <p>Holds withheld taxes (if applicable) until remittance:</p>
 * <ul>
 *   <li>Withholding tax on organizer payouts</li>
 *   <li>VAT collected on platform fees</li>
 * </ul>
 *
 * <h2>Balance Management</h2>
 * <p>Platform accounts maintain real-time balances that should be
 * reconciled with actual bank account balances during bank reconciliation.</p>
 *
 * <h2>Audit Trail</h2>
 * <p>All balance changes should be recorded via:</p>
 * <ul>
 *   <li>Journal entries in the general ledger</li>
 *   <li>Platform account transaction log</li>
 * </ul>
 *
 * @see com.pml.booking.domain.model.PlatformAccount
 * @see com.pml.booking.domain.enums.PlatformAccountType
 * @since 1.0.0
 */
public interface PlatformAccountService {

    // ========================================================================
    // ACCOUNT MANAGEMENT
    // ========================================================================

    /**
     * Gets or creates a platform account of the specified type.
     *
     * <p>Platform accounts are singletons per type. This method ensures
     * exactly one account exists for each type.</p>
     *
     * @param accountType The account type
     * @param currency    Currency code (e.g., "ZMW")
     * @return The platform account
     */
    Mono<PlatformAccount> getOrCreateAccount(PlatformAccountType accountType, String currency);

    /**
     * Gets a platform account by type.
     *
     * @param accountType The account type
     * @return The platform account, or empty if not found
     */
    Mono<PlatformAccount> getByType(PlatformAccountType accountType);

    /**
     * Gets a platform account by ID.
     *
     * @param id The account ID
     * @return The platform account, or empty if not found
     */
    Mono<PlatformAccount> findById(String id);

    /**
     * Gets all platform accounts.
     *
     * @return All platform accounts
     */
    Flux<PlatformAccount> getAllAccounts();

    // ========================================================================
    // BALANCE OPERATIONS
    // ========================================================================

    /**
     * Credits an amount to a platform account.
     *
     * <p>Used for:</p>
     * <ul>
     *   <li>Gateway settlement received (OPERATING)</li>
     *   <li>Reserve fund replenishment (RESERVE)</li>
     *   <li>Tax withheld from payout (TAX_HOLDING)</li>
     * </ul>
     *
     * @param accountType The account type
     * @param amount      Amount to credit
     * @param reference   Reference ID (e.g., settlement ID, journal entry ID)
     * @param description Description of the credit
     * @return Updated account
     */
    Mono<PlatformAccount> credit(
            PlatformAccountType accountType,
            BigDecimal amount,
            String reference,
            String description
    );

    /**
     * Debits an amount from a platform account.
     *
     * <p>Used for:</p>
     * <ul>
     *   <li>Organizer payout disbursement (OPERATING)</li>
     *   <li>Chargeback recovery (RESERVE)</li>
     *   <li>Tax remittance (TAX_HOLDING)</li>
     * </ul>
     *
     * @param accountType The account type
     * @param amount      Amount to debit
     * @param reference   Reference ID
     * @param description Description of the debit
     * @return Updated account
     * @throws com.pml.booking.exception.InsufficientEscrowBalanceException if balance insufficient
     */
    Mono<PlatformAccount> debit(
            PlatformAccountType accountType,
            BigDecimal amount,
            String reference,
            String description
    );

    /**
     * Transfers funds between platform accounts.
     *
     * <p>Example: Moving funds from OPERATING to RESERVE for reserve replenishment.</p>
     *
     * @param fromType    Source account type
     * @param toType      Destination account type
     * @param amount      Amount to transfer
     * @param reference   Reference ID
     * @param description Description of transfer
     * @return Tuple of (source account, destination account)
     */
    Mono<TransferResult> transfer(
            PlatformAccountType fromType,
            PlatformAccountType toType,
            BigDecimal amount,
            String reference,
            String description
    );

    /**
     * Result of a transfer operation.
     */
    record TransferResult(
            PlatformAccount fromAccount,
            PlatformAccount toAccount,
            BigDecimal amount
    ) {}

    // ========================================================================
    // BALANCE QUERIES
    // ========================================================================

    /**
     * Gets the current balance for an account type.
     *
     * @param accountType The account type
     * @return Current balance
     */
    Mono<BigDecimal> getBalance(PlatformAccountType accountType);

    /**
     * Gets the total balance across all platform accounts.
     *
     * @return Total balance
     */
    Mono<BigDecimal> getTotalBalance();

    /**
     * Checks if an account has sufficient balance for a debit.
     *
     * @param accountType The account type
     * @param amount      Amount to check
     * @return true if sufficient, false otherwise
     */
    Mono<Boolean> hasSufficientBalance(PlatformAccountType accountType, BigDecimal amount);

    // ========================================================================
    // RESERVE MANAGEMENT
    // ========================================================================

    /**
     * Checks if the reserve account needs replenishment.
     *
     * <p>Reserve should maintain minimum balance (configurable percentage of GMV).</p>
     *
     * @param minimumBalance Minimum required balance
     * @return Shortfall amount (0 if sufficient)
     */
    Mono<BigDecimal> getReserveShortfall(BigDecimal minimumBalance);

    /**
     * Attempts to recover funds from reserve for a chargeback.
     *
     * @param chargebackId The chargeback ID
     * @param amount       Amount to recover
     * @return Updated reserve account, or error if insufficient funds
     */
    Mono<PlatformAccount> recoverFromReserve(String chargebackId, BigDecimal amount);

    /**
     * Records a replenishment of the reserve account.
     *
     * <p>Can be funded from:</p>
     * <ul>
     *   <li>Commission revenue</li>
     *   <li>Manual bank transfer</li>
     * </ul>
     *
     * @param amount    Amount to add
     * @param source    Source description
     * @param reference Reference ID
     * @return Updated reserve account
     */
    Mono<PlatformAccount> replenishReserve(BigDecimal amount, String source, String reference);

    // ========================================================================
    // RECONCILIATION
    // ========================================================================

    /**
     * Gets the operating account balance for reconciliation.
     *
     * <p>This balance should match the actual bank account balance
     * during bank reconciliation.</p>
     *
     * @return Operating account balance
     */
    Mono<BigDecimal> getOperatingBalanceForReconciliation();

    /**
     * Records an adjustment to correct reconciliation discrepancies.
     *
     * @param accountType The account type
     * @param adjustment  Positive or negative adjustment
     * @param reason      Reason for adjustment
     * @param approvedBy  Admin who approved the adjustment
     * @return Updated account
     */
    Mono<PlatformAccount> recordReconciliationAdjustment(
            PlatformAccountType accountType,
            BigDecimal adjustment,
            String reason,
            String approvedBy
    );
}
