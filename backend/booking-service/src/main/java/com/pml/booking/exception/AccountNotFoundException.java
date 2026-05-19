package com.pml.booking.exception;

/**
 * Exception thrown when a referenced account is not found in the Chart of Accounts.
 *
 * <p>This exception occurs when attempting to create a journal entry line
 * that references an account code that doesn't exist in the chart_of_accounts
 * collection.</p>
 *
 * <h2>Common Causes</h2>
 * <ul>
 *   <li>Typo in account code</li>
 *   <li>Using an account that hasn't been seeded yet</li>
 *   <li>Dynamic account (e.g., escrow sub-account) not created</li>
 *   <li>Account was deleted or deactivated</li>
 * </ul>
 *
 * <h2>Resolution</h2>
 * <ol>
 *   <li>Verify the account code is correct</li>
 *   <li>Check if the account exists in ChartOfAccountsEntry collection</li>
 *   <li>Run chart of accounts seed if needed</li>
 *   <li>Create dynamic sub-account if needed (e.g., for new event escrow)</li>
 * </ol>
 *
 * @see com.pml.booking.domain.model.ChartOfAccountsEntry
 * @since 1.0.0
 */
public class AccountNotFoundException extends RuntimeException {

    /**
     * The account code that was not found.
     */
    private final String accountCode;

    /**
     * Creates a new AccountNotFoundException with basic message.
     *
     * @param message Error message
     */
    public AccountNotFoundException(String message) {
        super(message);
        this.accountCode = null;
    }

    /**
     * Creates a new AccountNotFoundException for a specific account code.
     *
     * @param accountCode The account code that was not found
     * @param contextMessage Additional context about where the lookup occurred
     */
    public AccountNotFoundException(String accountCode, String contextMessage) {
        super(String.format(
                "Account not found: %s. %s",
                accountCode,
                contextMessage != null ? contextMessage : ""
        ));
        this.accountCode = accountCode;
    }

    /**
     * Creates a new AccountNotFoundException with cause.
     *
     * @param message Error message
     * @param cause Underlying cause
     */
    public AccountNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.accountCode = null;
    }

    /**
     * Static factory for creating exception from account code lookup.
     *
     * @param accountCode The account code that was not found
     * @return New AccountNotFoundException
     */
    public static AccountNotFoundException forAccountCode(String accountCode) {
        return new AccountNotFoundException(
                accountCode,
                "Ensure the account exists in the Chart of Accounts."
        );
    }

    // Getter

    public String getAccountCode() {
        return accountCode;
    }
}
