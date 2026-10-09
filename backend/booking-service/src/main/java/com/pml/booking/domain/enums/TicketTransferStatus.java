package com.pml.booking.domain.enums;

/** Where a ticket transfer stands. Only {@code PENDING} holds the ticket; every other state has released it. */
public enum TicketTransferStatus {
    /** Offered to the recipient; the ticket is held and cannot be scanned, refunded or sent elsewhere. */
    PENDING,
    /** The recipient accepted; they now hold the ticket. */
    ACCEPTED,
    /** The recipient declined; the ticket is back with the sender. */
    DECLINED,
    /** The sender withdrew it; the ticket is back with them. */
    CANCELLED,
    /** Nobody answered in time (or the event started or was cancelled); the ticket is back with the sender. */
    EXPIRED;

    public boolean isOpen() {
        return this == PENDING;
    }
}
