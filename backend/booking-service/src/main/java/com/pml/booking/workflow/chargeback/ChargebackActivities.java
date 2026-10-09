package com.pml.booking.workflow.chargeback;

import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Dispute;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.Receive;
import com.pml.booking.workflow.chargeback.ChargebackWorkflow.View;
import io.temporal.activity.ActivityInterface;

/**
 * The chargeback's writes, each safe to run twice.
 */
@ActivityInterface(namePrefix = "Chargeback")
public interface ChargebackActivities {

    /** Records the chargeback and its accounting; a second notification answers the first record. */
    View receive(Receive command);

    /** Invalidates the ticket, returns its seat, counts the dispute on the escrow and notifies — the count once. */
    View openDispute(String recordId);

    View startReview(String recordId, String actorId, String note);

    View accept(String recordId, String actorId, String note);

    View dispute(String recordId, Dispute command);

    View recordWin(String recordId, String actorId, String notes);

    /** Records the loss; the service starts the recovery waterfall with it. */
    View recordLoss(String recordId, String actorId, String notes);

    /** Runs the recovery waterfall for an accepted chargeback that has not started one. */
    View recover(String recordId);

    /** Uncounts the dispute, notifies the organizer, and wakes the event's finance workflow — the uncount once. */
    void closeDispute(String recordId);

    /**
     * Alerts the finance lead and the finance channel that the chargeback is still undecided near its deadline and records
     * when. A decided or already escalated chargeback alerts nobody; a retry after a failed record alerts again.
     */
    void escalate(String recordId);
}
