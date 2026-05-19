package com.pml.booking.exception;

/**
 * Exception thrown when attempting to use a ticket that has already been used.
 */
public class TicketAlreadyUsedException extends RuntimeException {
    public TicketAlreadyUsedException(String ticketNumber) {
        super(String.format("Ticket %s has already been used", ticketNumber));
    }
}
