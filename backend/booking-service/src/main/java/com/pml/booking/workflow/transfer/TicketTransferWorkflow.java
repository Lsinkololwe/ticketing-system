package com.pml.booking.workflow.transfer;

import com.pml.booking.domain.enums.TicketTransferStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * One offer of a ticket to another account, from the moment it holds the ticket until the moment it
 * lets go. Id {@code ticket-transfer/{transferId}}.
 *
 * <p>The execution is what makes "held" safe: the offer's expiry is a durable timer, so a ticket
 * never stays unusable because a process restarted, and accept, decline and cancel are updates that
 * race one another through the workflow rather than through three writers on one document.
 */
@WorkflowInterface
public interface TicketTransferWorkflow {

    @WorkflowMethod
    void run(Start start);

    /** Holds the ticket and records the offer. Idempotent: a second call returns the first answer. */
    @UpdateMethod
    View begin(Begin begin);

    @UpdateMethod
    View accept(Decision decision);

    @UpdateValidatorMethod(updateName = "accept")
    void validateAccept(Decision decision);

    @UpdateMethod
    View decline(Decision decision);

    @UpdateValidatorMethod(updateName = "decline")
    void validateDecline(Decision decision);

    @UpdateMethod
    View cancel(Decision decision);

    @UpdateValidatorMethod(updateName = "cancel")
    void validateCancel(Decision decision);

    @QueryMethod
    View current();

    record Start(String transferId) {
    }

    record Begin(String transferId, String ticketId, String fromUserId, String toUserId, String fromDisplayName,
                 String toDisplayName, String recipientChannel, String recipientMasked, String note,
                 long expiresAtMillis) {
    }

    record Decision(String transferId, String actorId) {
    }

    record View(String transferId, String ticketId, String fromUserId, String toUserId, TicketTransferStatus status,
                long expiresAtMillis) {
    }
}
