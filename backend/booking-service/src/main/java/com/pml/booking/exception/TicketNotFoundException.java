package com.pml.booking.exception;

/**
 * Exception thrown when a ticket cannot be found.
 */
public class TicketNotFoundException extends RuntimeException {
    public TicketNotFoundException(String message) {
        super(message);
    }

    public TicketNotFoundException(String ticketId, String context) {
        super(String.format("Ticket not found: %s (%s)", ticketId, context));
    }
}
