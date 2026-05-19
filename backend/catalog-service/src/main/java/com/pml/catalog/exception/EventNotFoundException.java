package com.pml.catalog.exception;

/**
 * Exception thrown when an event is not found.
 */
public class EventNotFoundException extends RuntimeException {

    private final String eventId;

    public EventNotFoundException(String eventId) {
        super("Event not found: " + eventId);
        this.eventId = eventId;
    }

    public EventNotFoundException(String message, String eventId) {
        super(message);
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }
}
