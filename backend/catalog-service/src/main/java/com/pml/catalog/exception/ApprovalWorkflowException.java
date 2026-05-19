package com.pml.catalog.exception;

/**
 * Exception thrown when approval workflow operations fail.
 */
public class ApprovalWorkflowException extends RuntimeException {

    private final String eventId;
    private final String reason;

    public ApprovalWorkflowException(String message) {
        super(message);
        this.eventId = null;
        this.reason = message;
    }

    public ApprovalWorkflowException(String message, String eventId, String reason) {
        super(message);
        this.eventId = eventId;
        this.reason = reason;
    }

    public String getEventId() {
        return eventId;
    }

    public String getReason() {
        return reason;
    }
}
