package com.pml.booking.exception;

/**
 * Exception thrown when a refund is not allowed per platform policy.
 */
public class RefundNotAllowedException extends RuntimeException {
    public RefundNotAllowedException(String message) {
        super(message);
    }

    public RefundNotAllowedException(String ticketId, String reason) {
        super(String.format("Refund not allowed for ticket %s: %s", ticketId, reason));
    }
}
