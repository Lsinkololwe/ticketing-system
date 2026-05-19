package com.pml.booking.dto;

/**
 * Payment Failure Reason
 *
 * Business Intent: Provides details when a mobile money transaction fails.
 * Common failure reasons include insufficient funds, wrong PIN, timeout,
 * or network issues. This information helps in troubleshooting and customer support.
 *
 * @param failureCode Machine-readable failure code (e.g., INSUFFICIENT_FUNDS)
 * @param failureMessage Human-readable failure description
 */
public record FailureReason(
        String failureCode,
        String failureMessage
) {}
