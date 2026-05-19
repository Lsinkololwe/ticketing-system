package com.pml.identity.exception;

/**
 * Exception thrown when an invitation operation fails.
 */
public class InvalidInvitationException extends RuntimeException {

    private final String invitationId;
    private final String reason;

    public InvalidInvitationException(String message) {
        super(message);
        this.invitationId = null;
        this.reason = message;
    }

    public InvalidInvitationException(String invitationId, String reason) {
        super(String.format("Invalid invitation %s: %s", invitationId, reason));
        this.invitationId = invitationId;
        this.reason = reason;
    }

    public String getInvitationId() {
        return invitationId;
    }

    public String getReason() {
        return reason;
    }
}
