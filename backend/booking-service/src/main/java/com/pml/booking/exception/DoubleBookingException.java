package com.pml.booking.exception;

/**
 * Exception thrown when a double-booking attempt is detected.
 *
 * This occurs when optimistic locking detects concurrent modification
 * of a ticket, typically during high-demand events where multiple
 * users attempt to book the same seat/ticket simultaneously.
 */
public class DoubleBookingException extends RuntimeException {

    private final String ticketId;
    private final String eventId;

    public DoubleBookingException(String message) {
        super(message);
        this.ticketId = null;
        this.eventId = null;
    }

    public DoubleBookingException(String message, String ticketId, String eventId) {
        super(message);
        this.ticketId = ticketId;
        this.eventId = eventId;
    }

    public DoubleBookingException(String message, Throwable cause) {
        super(message, cause);
        this.ticketId = null;
        this.eventId = null;
    }

    public DoubleBookingException(String message, String ticketId, String eventId, Throwable cause) {
        super(message, cause);
        this.ticketId = ticketId;
        this.eventId = eventId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public String getEventId() {
        return eventId;
    }
}
