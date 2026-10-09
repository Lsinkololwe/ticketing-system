package com.pml.booking.workflow.refund;

import com.pml.booking.workflow.refund.RefundWorkflow.Answer;
import com.pml.booking.workflow.refund.RefundWorkflow.Decision;
import com.pml.booking.workflow.refund.RefundWorkflow.Submit;
import com.pml.booking.workflow.refund.RefundWorkflow.View;
import io.temporal.activity.ActivityInterface;

/**
 * The refund's writes and its provider calls. Each is safe to run twice.
 */
@ActivityInterface(namePrefix = "Refund")
public interface RefundActivities {

    /** Creates the ticket's refund request, or answers the one already open for it. */
    View submit(Submit command);

    /** An event cancellation's 100% refund request for this ticket, or the one that exists. */
    View createAutomatic(String ticketId, String reason);

    View approve(Decision decision);

    View reject(Decision decision);

    View cancel(Decision decision);

    /** Adjusts commission, debits the escrow and asks the provider to refund; a request already past APPROVED is answered unchanged. */
    View process(String refundRequestId);

    /** What the provider's refund status API says now. */
    Answer providerStatus(String refundRequestId);

    /** A verified completion: accounting, ticket REFUNDED, status COMPLETED — once. */
    View complete(String refundRequestId, String reference);

    /** A verified failure: status FAILED — once. */
    View fail(String refundRequestId, String failureCode, String reason);

    /** Re-credits the escrow the refund debited, once. */
    void restoreEscrow(String refundRequestId);

    /** Reinstates a pending commission this refund cancelled at initiation, once. */
    void reinstateCommission(String refundRequestId);

    /**
     * Alerts the finance lead and the finance channel that the request has waited past escalation {@code level} and records
     * it in the request's history. A decided request, or a level already recorded, alerts nobody.
     */
    void escalateReview(String refundRequestId, int level);
}
