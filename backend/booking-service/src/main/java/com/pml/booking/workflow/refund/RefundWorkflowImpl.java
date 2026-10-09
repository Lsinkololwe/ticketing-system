package com.pml.booking.workflow.refund;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Request, approval, the refund itself, and a verified answer.
 *
 * <pre>
 *   submit ─▶ PENDING ─(a person approves; a cancellation's refund approves itself)─▶ APPROVED ─▶ process
 *               ├─ still PENDING after 2 days, and again after 5 ─▶ finance escalated               │
 *               └─ reject / cancel ─▶ closed                            verified COMPLETED ◀─ poll ─┤
 *                                                                       verified FAILED ─▶ FAILED + escrow re-credited
 * </pre>
 */
@WorkflowImpl(taskQueues = TaskQueues.FINANCE)
public class RefundWorkflowImpl implements RefundWorkflow {

    private final RefundActivities refunds =
            Workflow.newActivityStub(RefundActivities.class, RefundRules.refundOptions());
    private final RefundActivities provider =
            Workflow.newActivityStub(RefundActivities.class, RefundRules.providerOptions());

    private View view;
    private boolean refused;
    private boolean submitting;
    private boolean evidenceArrived;

    @Override
    public void run(Start start) {
        if (start.automaticReason() != null) {
            view = refunds.createAutomatic(start.ticketId(), start.automaticReason());
        } else {
            Workflow.await(RefundRules.SUBMIT_WINDOW, () -> view != null || refused);
        }
        if (view == null) {
            close();
            return;
        }

        if (view.status() == RefundRequestStatus.PENDING && start.automaticReason() != null) {
            view = refunds.approve(new Decision(view.refundRequestId(), RefundRules.SYSTEM_ACTOR,
                    "automatic: " + start.automaticReason()));
        }
        awaitReview();
        if (view.status() != RefundRequestStatus.APPROVED) {
            close();
            return;
        }

        view = refunds.process(view.refundRequestId());
        Answer answer = view.status() == RefundRequestStatus.FAILED
                ? Answer.failed("REFUND_NOT_ACCEPTED", "the provider did not accept the refund")
                : awaitAnswer();

        switch (answer.outcome()) {
            case COMPLETED -> view = refunds.complete(view.refundRequestId(), answer.reference());
            case FAILED -> {
                view = refunds.fail(view.refundRequestId(), answer.failureCode(), answer.reason());
                refunds.restoreEscrow(view.refundRequestId());
                refunds.reinstateCommission(view.refundRequestId());
            }
            case PENDING -> { }
        }
        close();
    }

    /** Waits for a person's decision, escalating to finance at each of {@link RefundRules#REVIEW_ESCALATIONS}. */
    private void awaitReview() {
        long waitingSince = Workflow.currentTimeMillis();
        int level = 0;
        for (Duration after : RefundRules.REVIEW_ESCALATIONS) {
            long remaining = waitingSince + after.toMillis() - Workflow.currentTimeMillis();
            boolean reviewed = reviewed() || remaining > 0 && Workflow.await(Duration.ofMillis(remaining), this::reviewed);
            if (reviewed) {
                return;
            }
            refunds.escalateReview(view.refundRequestId(), ++level);
        }
        Workflow.await(this::reviewed);
    }

    private boolean reviewed() {
        return view.status() != RefundRequestStatus.PENDING;
    }

    private Answer awaitAnswer() {
        long started = Workflow.currentTimeMillis();
        for (int poll = 0; ; poll++) {
            evidenceArrived = false;
            Workflow.await(RefundRules.pollDelay(poll), () -> evidenceArrived);
            Answer answer = provider.providerStatus(view.refundRequestId());
            if (answer.outcome() != Answer.Outcome.PENDING) {
                return answer;
            }
            if (Workflow.currentTimeMillis() - started >= RefundRules.ANSWER_LIMIT.toMillis()) {
                return answer;
            }
        }
    }

    private void close() {
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    // ---- updates -------------------------------------------------------------------------------

    @Override
    public View submit(RefundWorkflow.Submit command) {
        // Update handlers interleave at every activity call. Two submissions arriving together would both see no
        // view yet and both create a request; the second waits here for the first to finish and gets its answer.
        // Uncontended, the wait is already true and records nothing, so histories written before this existed replay.
        Workflow.await(() -> !submitting);
        if (view != null) {
            // The same ask again is answered with the request that is open. A different amount is a different ask and
            // is refused, not quietly answered with a request for another sum.
            // Versioned: an execution that already answered a differing ask with the open request must replay as it did.
            boolean refusesDifferingAmount = Workflow.getVersion("refund-submit-differing-amount", Workflow.DEFAULT_VERSION, 1) >= 1;
            if (refusesDifferingAmount && RefundRules.asksForDifferentAmount(command.partialAmount(), view.amount())) {
                throw Refusals.refusal(ErrorCode.REFUND_NOT_PERMITTED, RefundRules.openRefundMessage(view.amount()));
            }
            return view;
        }
        submitting = true;
        try {
            view = refunds.submit(command);
            return view;
        } catch (ActivityFailure failure) {
            refused = true;
            throw Refusals.rethrow(failure);
        } finally {
            submitting = false;
        }
    }

    @Override
    public void validateApprove(Decision decision) {
        requirePending(decision);
    }

    @Override
    public View approve(Decision decision) {
        return decide(() -> refunds.approve(decision));
    }

    @Override
    public void validateReject(Decision decision) {
        requirePending(decision);
        if (decision.note() == null || decision.note().isBlank()) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a rejection needs a reason");
        }
    }

    @Override
    public View reject(Decision decision) {
        return decide(() -> refunds.reject(decision));
    }

    @Override
    public void validateCancel(Decision decision) {
        requireThisRefund(decision);
        if (view.status() != RefundRequestStatus.PENDING && view.status() != RefundRequestStatus.APPROVED) {
            throw Refusals.refusal(ErrorCode.REFUND_NOT_PERMITTED, "a refund already sent to the provider cannot be cancelled");
        }
    }

    @Override
    public View cancel(Decision decision) {
        return decide(() -> refunds.cancel(decision));
    }

    @Override
    public void providerCallback(Evidence evidence) {
        evidenceArrived = true;
    }

    @Override
    public View current() {
        return view;
    }

    private View decide(Supplier<View> activity) {
        try {
            view = activity.get();
            return view;
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
    }

    private void requirePending(Decision decision) {
        requireThisRefund(decision);
        if (view.status() != RefundRequestStatus.PENDING) {
            throw Refusals.refusal(ErrorCode.REFUND_NOT_PERMITTED, "the refund request is " + view.status());
        }
    }

    private void requireThisRefund(Decision decision) {
        if (view == null || !view.refundRequestId().equals(decision.refundRequestId())) {
            throw Refusals.refusal(ErrorCode.REFUND_NOT_PERMITTED, "this refund request is not the one open for its ticket");
        }
    }
}
