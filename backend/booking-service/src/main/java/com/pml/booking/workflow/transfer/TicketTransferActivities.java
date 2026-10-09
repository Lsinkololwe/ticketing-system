package com.pml.booking.workflow.transfer;

import com.pml.booking.domain.enums.TicketTransferStatus;
import com.pml.booking.workflow.transfer.TicketTransferWorkflow.Begin;
import com.pml.booking.workflow.transfer.TicketTransferWorkflow.View;
import io.temporal.activity.ActivityInterface;

/**
 * The writes of a ticket transfer. Each is a conditional update, so an activity that runs twice
 * answers what the first run did instead of doing it again.
 */
@ActivityInterface(namePrefix = "TicketTransfer")
public interface TicketTransferActivities {

    /** Holds the ticket for the offer and records it, together; refuses if the ticket cannot be held. */
    View open(Begin begin);

    /** The recipient takes the ticket: ownership, the cached holder name, the chain count and the event, together. */
    View accept(String transferId, String actorId);

    /** Ends an open offer as {@code DECLINED}, {@code CANCELLED} or {@code EXPIRED} and gives the ticket back. */
    View release(String transferId, TicketTransferStatus to, String actorId);

    /** Tells the recipient there is a ticket waiting. Best effort: the offer does not depend on it. */
    void notifyRecipient(String transferId);

    /** Tells the sender how the offer ended. Best effort. */
    void notifySender(String transferId);
}
