package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.PlatformAccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Platform Account - Platform-Owned Financial Accounts
 *
 * Platform accounts represent the company's own financial accounts, distinct
 * from organizer escrow accounts. These accounts track platform money for
 * different operational purposes.
 *
 * <h2>Account Types</h2>
 * <pre>
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │                        PLATFORM ACCOUNTS                             │
 * ├──────────────────────────────────────────────────────────────────────┤
 * │                                                                      │
 * │  ┌─────────────────────────────────────────────────────────────┐    │
 * │  │  OPERATING ACCOUNT                                          │    │
 * │  │  Purpose: Day-to-day operational cash flow                  │    │
 * │  │  Inflows:  Earned commission, processing fees               │    │
 * │  │  Outflows: Operating expenses, payroll, infrastructure      │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                                                                      │
 * │  ┌─────────────────────────────────────────────────────────────┐    │
 * │  │  RESERVE ACCOUNT                                            │    │
 * │  │  Purpose: Emergency fund and risk buffer                    │    │
 * │  │  Inflows:  Monthly allocation from profits                  │    │
 * │  │  Outflows: Chargeback coverage, dispute losses              │    │
 * │  │  Target:   3-6 months of operating expenses                 │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                                                                      │
 * │  ┌─────────────────────────────────────────────────────────────┐    │
 * │  │  TAX HOLDING ACCOUNT                                        │    │
 * │  │  Purpose: Hold taxes until remittance to ZRA                │    │
 * │  │  Inflows:  Withholding tax from organizer payouts           │    │
 * │  │  Outflows: Monthly tax remittances                          │    │
 * │  │  Balance:  Must always match outstanding tax liability      │    │
 * │  └─────────────────────────────────────────────────────────────┘    │
 * │                                                                      │
 * └──────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Key Distinction from Escrow</h2>
 * <ul>
 *   <li><b>Platform accounts</b>: Hold platform-owned money (our revenue)</li>
 *   <li><b>Escrow accounts</b>: Hold organizer money (we owe them)</li>
 * </ul>
 *
 * <p>This distinction is critical for regulatory compliance and accurate
 * financial reporting.</p>
 *
 * <h2>Singleton Pattern</h2>
 * <p>Each account type typically has exactly ONE instance. The service layer
 * enforces this via getOrCreateAccount() which returns the existing account
 * or creates one if it doesn't exist.</p>
 *
 * <h2>Usage Examples</h2>
 * <pre>
 * // Recording earned commission
 * operatingAccount.credit(commissionAmount);
 *
 * // Covering a chargeback loss
 * reserveAccount.debit(chargebackAmount);
 *
 * // Withholding tax from payout
 * taxHoldingAccount.credit(withheldTax);
 *
 * // Remitting taxes to ZRA
 * taxHoldingAccount.debit(remittanceAmount);
 * </pre>
 *
 * @see PlatformAccountType
 * @see ChargebackFundSource#PLATFORM_RESERVE
 * @since 1.0.0
 */
@Document(collection = "platform_accounts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PlatformAccount {

    /**
     * MongoDB document ID.
     */
    @Id
    private String id;

    /**
     * Type of platform account.
     *
     * <p>Each type serves a specific purpose and has different rules
     * for deposits and withdrawals.</p>
     *
     * @see PlatformAccountType
     */
    @NotNull(message = "Account type is required")
    @Indexed(unique = true)
    private PlatformAccountType accountType;

    /**
     * Human-readable name for the account.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"Platform Operating Account"</li>
     *   <li>"Risk Reserve Account"</li>
     *   <li>"Tax Withholding Account"</li>
     * </ul>
     */
    @NotBlank(message = "Account name is required")
    private String name;

    /**
     * Current balance of the account.
     *
     * <p>Updated via credit() and debit() methods. Always >= 0.</p>
     */
    @NotNull(message = "Balance is required")
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * Currency code for this account.
     */
    @Builder.Default
    private String currency = "ZMW";

    /**
     * Description of the account's purpose.
     */
    private String description;

    /**
     * Target balance for this account (for RESERVE type).
     *
     * <p>Used for monitoring and alerts. When balance falls below
     * target, alerts may be triggered.</p>
     */
    private BigDecimal targetBalance;

    /**
     * Minimum balance threshold for alerts.
     *
     * <p>When balance drops below this level, monitoring systems
     * should trigger an alert.</p>
     */
    private BigDecimal minimumBalanceThreshold;

    /**
     * Whether the account is currently active.
     *
     * <p>Inactive accounts cannot receive credits or debits.</p>
     */
    @Builder.Default
    private Boolean isActive = true;

    // ========================================================================
    // TIMESTAMPS
    // ========================================================================

    /**
     * Timestamp when the account was created.
     */
    @CreatedDate
    private Instant createdAt;

    /**
     * Timestamp of last modification.
     */
    @LastModifiedDate
    private Instant lastUpdatedAt;

    /**
     * Version for optimistic locking.
     */
    @Version
    private Long version;

    // ========================================================================
    // BUSINESS METHODS
    // ========================================================================

    /**
     * Credits (adds) funds to this account.
     *
     * <p>Use cases:</p>
     * <ul>
     *   <li>OPERATING: Earned commission, processing fees</li>
     *   <li>RESERVE: Monthly allocation from profits</li>
     *   <li>TAX_HOLDING: Withholding tax from payouts</li>
     * </ul>
     *
     * @param amount Amount to credit (must be positive)
     * @return New balance after credit
     * @throws IllegalStateException if account is inactive
     * @throws IllegalArgumentException if amount is not positive
     */
    public BigDecimal credit(BigDecimal amount) {
        validateActive();
        validatePositiveAmount(amount, "Credit");

        this.balance = this.balance.add(amount);
        return this.balance;
    }

    /**
     * Debits (subtracts) funds from this account.
     *
     * <p>Use cases:</p>
     * <ul>
     *   <li>OPERATING: Operating expenses, payroll</li>
     *   <li>RESERVE: Chargeback coverage, dispute losses</li>
     *   <li>TAX_HOLDING: Tax remittance to ZRA</li>
     * </ul>
     *
     * @param amount Amount to debit (must be positive)
     * @return New balance after debit
     * @throws IllegalStateException if account is inactive or insufficient balance
     * @throws IllegalArgumentException if amount is not positive
     */
    public BigDecimal debit(BigDecimal amount) {
        validateActive();
        validatePositiveAmount(amount, "Debit");

        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException(String.format(
                    "Insufficient balance in %s account. Available: %s, Requested: %s",
                    accountType, this.balance, amount
            ));
        }

        this.balance = this.balance.subtract(amount);
        return this.balance;
    }

    /**
     * Checks if the account has sufficient balance for a debit.
     *
     * @param amount Amount to check
     * @return true if balance >= amount
     */
    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.balance.compareTo(amount) >= 0;
    }

    /**
     * Checks if the balance is below the minimum threshold.
     *
     * @return true if balance < minimumBalanceThreshold
     */
    public boolean isBelowMinimumThreshold() {
        if (minimumBalanceThreshold == null) {
            return false;
        }
        return this.balance.compareTo(minimumBalanceThreshold) < 0;
    }

    /**
     * Checks if the balance is below the target balance.
     *
     * @return true if balance < targetBalance
     */
    public boolean isBelowTarget() {
        if (targetBalance == null) {
            return false;
        }
        return this.balance.compareTo(targetBalance) < 0;
    }

    /**
     * Calculates the amount needed to reach the target balance.
     *
     * @return Amount to add to reach target, or ZERO if at or above target
     */
    public BigDecimal getAmountToTarget() {
        if (targetBalance == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal gap = targetBalance.subtract(this.balance);
        return gap.compareTo(BigDecimal.ZERO) > 0 ? gap : BigDecimal.ZERO;
    }

    /**
     * Deactivates the account.
     *
     * <p>Inactive accounts cannot be used for transactions.
     * Typically only used when closing/migrating accounts.</p>
     */
    public void deactivate() {
        this.isActive = false;
    }

    /**
     * Reactivates a previously deactivated account.
     */
    public void activate() {
        this.isActive = true;
    }

    // ========================================================================
    // VALIDATION HELPERS
    // ========================================================================

    private void validateActive() {
        if (!Boolean.TRUE.equals(isActive)) {
            throw new IllegalStateException(String.format(
                    "Cannot transact on inactive %s account", accountType
            ));
        }
    }

    private void validatePositiveAmount(BigDecimal amount, String operation) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(String.format(
                    "%s amount must be positive. Received: %s", operation, amount
            ));
        }
    }

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Gets the account code from the Chart of Accounts.
     *
     * @return Chart of Accounts code for this account type
     */
    public String getAccountCode() {
        return accountType.getDefaultAccountCode();
    }

    /**
     * Checks if this is the operating account.
     *
     * @return true if account type is OPERATING
     */
    public boolean isOperating() {
        return accountType == PlatformAccountType.OPERATING;
    }

    /**
     * Checks if this is the reserve account.
     *
     * @return true if account type is RESERVE
     */
    public boolean isReserve() {
        return accountType == PlatformAccountType.RESERVE;
    }

    /**
     * Checks if this is the tax holding account.
     *
     * @return true if account type is TAX_HOLDING
     */
    public boolean isTaxHolding() {
        return accountType == PlatformAccountType.TAX_HOLDING;
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates an operating account with default settings.
     *
     * @return New operating account
     */
    public static PlatformAccount createOperating() {
        return PlatformAccount.builder()
                .accountType(PlatformAccountType.OPERATING)
                .name("Platform Operating Account")
                .description("Primary account for day-to-day operations, commission revenue, and operating expenses")
                .balance(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    /**
     * Creates a reserve account with target balance.
     *
     * @param targetBalance Target balance for the reserve
     * @return New reserve account
     */
    public static PlatformAccount createReserve(BigDecimal targetBalance) {
        return PlatformAccount.builder()
                .accountType(PlatformAccountType.RESERVE)
                .name("Risk Reserve Account")
                .description("Emergency fund for chargeback coverage, dispute losses, and risk mitigation")
                .balance(BigDecimal.ZERO)
                .targetBalance(targetBalance)
                .minimumBalanceThreshold(targetBalance.multiply(new BigDecimal("0.5")))
                .isActive(true)
                .build();
    }

    /**
     * Creates a tax holding account.
     *
     * @return New tax holding account
     */
    public static PlatformAccount createTaxHolding() {
        return PlatformAccount.builder()
                .accountType(PlatformAccountType.TAX_HOLDING)
                .name("Tax Withholding Account")
                .description("Holds withholding tax collected from organizer payouts until remittance to ZRA")
                .balance(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }
}
