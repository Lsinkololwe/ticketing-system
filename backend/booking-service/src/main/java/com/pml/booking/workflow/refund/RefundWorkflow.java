package com.pml.booking.workflow.refund;

import com.pml.shared.constants.RefundRequestStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.math.BigDecimal;

/**
 * One ticket's refund, from request to a verified provider answer.
 *
 * <p>Addressed as {@code refund/{ticketId}}: one ticket, one refund in flight. The ticket moves
 * to REFUNDED only after the provider's status API confirms the money went back; a verified failure
 * restores the escrow debit the refund made.
 */
@WorkflowInterface
public interface RefundWorkflow {

    /** {@code automaticReason} set: an event cancellation's refund, created and approved without a person. */
    @WorkflowMethod
    void run(Start start);

    @UpdateMethod
    View submit(Submit command);

    @UpdateMethod
    View approve(Decision decision);

    @UpdateValidatorMethod(updateName = "approve")
    void validateApprove(Decision decision);

    @UpdateMethod
    View reject(Decision decision);

    @UpdateValidatorMethod(updateName = "reject")
    void validateReject(Decision decision);

    @UpdateMethod
    View cancel(Decision decision);

    @UpdateValidatorMethod(updateName = "cancel")
    void validateCancel(Decision decision);

    @SignalMethod
    void providerCallback(Evidence evidence);

    @QueryMethod
    View current();

    record Start(String ticketId, String automaticReason) {
    }

    record Submit(Kind kind, String ticketId, String reason, String actorId, BigDecimal partialAmount, boolean bypassApproval) {

        public enum Kind { BUYER, PARTIAL, ADMIN }
    }

    record Decision(String refundRequestId, String actorId, String note) {
    }

    record Evidence(String providerRefundId, String status) {
    }

    record View(String refundRequestId, String ticketId, RefundRequestStatus status, BigDecimal amount) {
    }

    record Answer(Outcome outcome, String reference, String failureCode, String reason) {

        public enum Outcome { COMPLETED, FAILED, PENDING }

        public static Answer completed(String reference) {
            return new Answer(Outcome.COMPLETED, reference, null, null);
        }

        public static Answer failed(String failureCode, String reason) {
            return new Answer(Outcome.FAILED, null, failureCode, reason);
        }

        public static Answer pending() {
            return new Answer(Outcome.PENDING, null, null, null);
        }
    }
}
