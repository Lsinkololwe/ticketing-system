package com.pml.booking.infrastructure.gateway.domain;

/**
 * Provider-agnostic payment result status.
 * Unified status across all payment providers.
 */
public enum PaymentResultStatus {
    /**
     * Payment completed successfully.
     * Funds have been collected/transferred.
     */
    SUCCESS,

    /**
     * Payment is pending user action.
     * Common with mobile money (awaiting USSD confirmation).
     */
    PENDING,

    /**
     * Payment is being processed by the provider.
     * No user action required, awaiting provider completion.
     */
    PROCESSING,

    /**
     * Payment failed due to technical or business error.
     * Check errorCode and errorMessage for details.
     */
    FAILED,

    /**
     * User rejected or cancelled the payment.
     * e.g., declined USSD prompt.
     */
    REJECTED,

    /**
     * Payment expired before completion.
     * User did not respond within the allowed time.
     */
    EXPIRED,

    /**
     * Payment was refunded.
     */
    REFUNDED;

    /**
     * Check if this status represents a terminal (final) state.
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == REJECTED ||
               this == EXPIRED || this == REFUNDED;
    }

    /**
     * Check if this status represents a successful outcome.
     */
    public boolean isSuccessful() {
        return this == SUCCESS;
    }

    /**
     * Check if payment is still in progress (not terminal).
     */
    public boolean isInProgress() {
        return this == PENDING || this == PROCESSING;
    }

    /**
     * Check if the operation can be retried.
     * Only non-terminal failures may be retryable.
     */
    public boolean isPotentiallyRetryable() {
        return this == FAILED;
    }
}
