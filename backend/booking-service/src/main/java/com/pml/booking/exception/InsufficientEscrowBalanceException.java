package com.pml.booking.exception;

import java.math.BigDecimal;

/**
 * Exception thrown when an escrow operation fails due to insufficient balance.
 */
public class InsufficientEscrowBalanceException extends RuntimeException {
    private final String accountId;
    private final BigDecimal requestedAmount;
    private final BigDecimal availableBalance;

    /**
     * Creates exception with a simple message.
     *
     * @param message Error message
     */
    public InsufficientEscrowBalanceException(String message) {
        super(message);
        this.accountId = null;
        this.requestedAmount = null;
        this.availableBalance = null;
    }

    public InsufficientEscrowBalanceException(String accountId, BigDecimal requestedAmount, BigDecimal availableBalance) {
        super(String.format("Insufficient escrow balance in account %s: requested %s, available %s",
                accountId, requestedAmount, availableBalance));
        this.accountId = accountId;
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }
}
