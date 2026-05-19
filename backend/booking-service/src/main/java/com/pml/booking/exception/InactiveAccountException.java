package com.pml.booking.exception;

/**
 * Exception thrown when attempting to post to an inactive account.
 *
 * <p>Accounts in the Chart of Accounts can be deactivated (isActive = false)
 * when they are no longer in use. Inactive accounts cannot receive new
 * journal entries but are preserved for historical reporting.</p>
 *
 * <h2>Common Causes</h2>
 * <ul>
 *   <li>Attempting to post to a closed event's escrow account</li>
 *   <li>Using an outdated account code that was replaced</li>
 *   <li>Account was deactivated during system migration</li>
 * </ul>
 *
 * <h2>Resolution</h2>
 * <ol>
 *   <li>Check why the account was deactivated</li>
 *   <li>Find the replacement account if applicable</li>
 *   <li>Reactivate the account if deactivation was in error</li>
 *   <li>Update the process to use the correct active account</li>
 * </ol>
 *
 * @see com.pml.booking.domain.model.ChartOfAccountsEntry#isActive
 * @since 1.0.0
 */
public class InactiveAccountException extends RuntimeException {

    /**
     * The account code that is inactive.
     */
    private final String accountCode;

    /**
     * The account name for context.
     */
    private final String accountName;

    /**
     * Creates a new InactiveAccountException with basic message.
     *
     * @param message Error message
     */
    public InactiveAccountException(String message) {
        super(message);
        this.accountCode = null;
        this.accountName = null;
    }

    /**
     * Creates a new InactiveAccountException for a specific account.
     *
     * @param accountCode The inactive account code
     * @param accountName The account name
     */
    public InactiveAccountException(String accountCode, String accountName) {
        super(String.format(
                "Account %s (%s) is inactive and cannot receive new entries. " +
                "Reactivate the account or use an active alternative.",
                accountCode,
                accountName != null ? accountName : "Unknown"
        ));
        this.accountCode = accountCode;
        this.accountName = accountName;
    }

    /**
     * Creates a new InactiveAccountException with cause.
     *
     * @param message Error message
     * @param cause Underlying cause
     */
    public InactiveAccountException(String message, Throwable cause) {
        super(message, cause);
        this.accountCode = null;
        this.accountName = null;
    }

    /**
     * Static factory for creating exception from account lookup.
     *
     * @param accountCode The inactive account code
     * @return New InactiveAccountException
     */
    public static InactiveAccountException forAccountCode(String accountCode) {
        return new InactiveAccountException(accountCode, (String) null);
    }

    // Getters

    public String getAccountCode() {
        return accountCode;
    }

    public String getAccountName() {
        return accountName;
    }
}
