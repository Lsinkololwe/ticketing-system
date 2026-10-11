package com.pml.booking.workflow.payout;

import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.constants.PayoutRequestStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One payout request, from submission to a settled or reversed transfer.
 *
 * <p>Addressed as {@code payout/{escrowAccountId}} with conflict policy {@code FAIL}: while an
 * execution is open for an escrow account, the server itself refuses a second request, which is
 * "at most one open request per account" held by construction rather than by a query.
 *
 * <p>Every command from a person is an update, so a refusal — an approver who is the requester, a
 * request that became ineligible — returns to the caller as a typed error and, when a validator
 * refuses, never enters the history.
 */
@WorkflowInterface
public interface PayoutWorkflow {

    @WorkflowMethod
    void run(Start start);

    /** The organizer's request; the first update of every execution. */
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

    /** Freezes the request before any money moves; approval, retry and settlement wait for the release. */
    @UpdateMethod
    View hold(Decision decision);

    @UpdateValidatorMethod(updateName = "hold")
    void validateHold(Decision decision);

    @UpdateMethod
    View release(Decision decision);

    @UpdateValidatorMethod(updateName = "release")
    void validateRelease(Decision decision);

    @UpdateMethod
    View retry(Decision decision);

    @UpdateValidatorMethod(updateName = "retry")
    void validateRetry(Decision decision);

    /** Finance confirms a manual bank transfer; {@code note} carries the bank's reference. */
    @UpdateMethod
    View confirmTransfer(Decision decision);

    @UpdateValidatorMethod(updateName = "confirmTransfer")
    void validateConfirmTransfer(Decision decision);

    /** The provider says something changed; the workflow asks the status API what. */
    @SignalMethod
    void providerCallback(Evidence evidence);

    @QueryMethod
    View current();

    record Start(String escrowAccountId) {
    }

    record Submit(String payoutRequestId,
                  String organizerId,
                  String organizationId,
                  String eventId,
                  String escrowAccountId,
                  String bankAccountId,
                  BigDecimal requestedAmount,
                  String currency,
                  PayoutMethod payoutMethod,
                  String notes,
                  Map<String, Object> metadata,
                  String idempotencyKey,
                  String requestedById) {
    }

    record Decision(String payoutRequestId, String actorId, String note) {
    }

    record Evidence(String providerPayoutId, String status) {
    }

    record View(String payoutRequestId,
                PayoutRequestStatus status,
                int attempts,
                PayoutMethod method,
                String requestedById,
                String bankAccountId) {
    }

    /** A verified answer about a transfer. {@code PENDING} is no answer and never ends a payout. */
    record Answer(Outcome outcome, String reference, String failureCode, String reason) {

        public enum Outcome { SUCCEEDED, FAILED, PENDING }

        public static Answer succeeded(String reference) {
            return new Answer(Outcome.SUCCEEDED, reference, null, null);
        }

        public static Answer failed(String failureCode, String reason) {
            return new Answer(Outcome.FAILED, null, failureCode, reason);
        }

        public static Answer pending() {
            return new Answer(Outcome.PENDING, null, null, null);
        }
    }
}
