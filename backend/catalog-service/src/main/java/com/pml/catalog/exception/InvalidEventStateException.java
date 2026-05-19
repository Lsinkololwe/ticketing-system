package com.pml.catalog.exception;

/**
 * Exception thrown when an operation cannot be performed due to invalid event state.
 */
public class InvalidEventStateException extends RuntimeException {

    private final String eventId;
    private final String currentStatus;
    private final String requiredStatus;

    public InvalidEventStateException(String message) {
        super(message);
        this.eventId = null;
        this.currentStatus = null;
        this.requiredStatus = null;
    }

    public InvalidEventStateException(String eventId, String currentStatus, String requiredStatus) {
        super(String.format("Event %s is in invalid state. Current: %s, Required: %s",
                eventId, currentStatus, requiredStatus));
        this.eventId = eventId;
        this.currentStatus = currentStatus;
        this.requiredStatus = requiredStatus;
    }

    public String getEventId() {
        return eventId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getRequiredStatus() {
        return requiredStatus;
    }
}
