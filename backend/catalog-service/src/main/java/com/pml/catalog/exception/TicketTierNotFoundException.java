package com.pml.catalog.exception;

/**
 * Exception thrown when a ticket tier is not found.
 */
public class TicketTierNotFoundException extends RuntimeException {

    private final String tierId;

    public TicketTierNotFoundException(String tierId) {
        super("Ticket tier not found: " + tierId);
        this.tierId = tierId;
    }

    public TicketTierNotFoundException(String message, String tierId) {
        super(message);
        this.tierId = tierId;
    }

    public String getTierId() {
        return tierId;
    }
}
