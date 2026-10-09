package com.pml.booking.workflow.latepayment;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * The automatic, full refund of money that arrived after its reservation lapsed (ROADMAP D-22).
 *
 * <p>Addressed as {@code late-refund/{reservationId}}: a reservation has one payment, so one late
 * payment has one refund. No ticket was ever issued, so this is not a {@code RefundWorkflow}: there
 * is no escrow to debit and no commission to adjust, and nobody approves it. It reaches the same
 * provider refund the ticket refunds use and keeps the operator escalation as the fallback when the
 * provider refuses or does not answer.
 */
@WorkflowInterface
public interface LatePaymentRefundWorkflow {

    @WorkflowMethod
    Result run(Start start);

    /** The provider called back about the refund; the next status check happens now. */
    @SignalMethod
    void providerCallback();

    @QueryMethod
    Stage stage();

    record Start(String reservationId) {
    }

    record Result(Outcome outcome, String refundId) {
    }

    /** {@code ESCALATED}: the refund did not complete by itself and an operator owns the money now. */
    enum Outcome { REFUNDED, ESCALATED }

    enum Stage { OPENING, SUBMITTING, AWAITING_PROVIDER, CLOSED }

    /** Where the refund of one late payment stands. */
    record View(String reservationId, String refundId, State state) {
    }

    enum State { REQUESTED, PROCESSING, COMPLETED, FAILED }

    /** What the provider's refund status API says now. */
    record Answer(Outcome outcome, String reference, String failureCode) {

        public enum Outcome { COMPLETED, FAILED, PENDING }
    }
}
