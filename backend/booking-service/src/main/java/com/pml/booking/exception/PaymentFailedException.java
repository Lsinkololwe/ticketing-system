package com.pml.booking.exception;

/**
 * Exception thrown when a payment operation fails.
 */
public class PaymentFailedException extends RuntimeException {
    private final String failureCode;
    private final String providerMessage;

    public PaymentFailedException(String message) {
        super(message);
        this.failureCode = "UNKNOWN";
        this.providerMessage = message;
    }

    public PaymentFailedException(String failureCode, String providerMessage) {
        super(String.format("Payment failed: %s - %s", failureCode, providerMessage));
        this.failureCode = failureCode;
        this.providerMessage = providerMessage;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getProviderMessage() {
        return providerMessage;
    }
}
