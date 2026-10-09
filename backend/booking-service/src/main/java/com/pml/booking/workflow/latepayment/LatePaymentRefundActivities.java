package com.pml.booking.workflow.latepayment;

import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.Answer;
import com.pml.booking.workflow.latepayment.LatePaymentRefundWorkflow.View;
import io.temporal.activity.ActivityInterface;

/**
 * The late-money refund's writes and its provider calls. Each moves a status by compare-and-set,
 * so a second execution changes nothing.
 */
@ActivityInterface(namePrefix = "LateRefund")
public interface LatePaymentRefundActivities {

    /**
     * Confirms the money is late — a verified success whose reservation did not confirm — and mints
     * the refund id once. A payment that is not owed back is refused.
     */
    View open(String reservationId);

    /** Asks the provider to return the full deposit under the minted id; a repeat finds it submitted. */
    View submit(String reservationId);

    /** What the provider's refund status API says now. */
    Answer providerStatus(String reservationId);

    /** A verified completion: the refund COMPLETED and the intent REFUNDED, in one write, and the escalation resolved. */
    View complete(String reservationId, String reference);

    /** A verified failure: the refund FAILED, once. */
    View fail(String reservationId, String failureCode);

    /** Hands the money to transaction recovery with the reason the automatic refund stopped; raised once. */
    void escalate(String reservationId, String reason);
}
