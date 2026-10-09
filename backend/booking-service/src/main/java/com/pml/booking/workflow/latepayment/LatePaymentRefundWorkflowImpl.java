package com.pml.booking.workflow.latepayment;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

/**
 * Late money, returned in full without a person.
 *
 * <pre>
 *   open (late? mint the refund id) ─▶ submit ─▶ poll the provider (30 s … 10 min)
 *        │ refused                      │ refused / silent         │ FAILED, or no answer in 72 h
 *        └──────────────────────────────┴──────────────────────────┴─▶ escalated to transaction recovery
 *                                                       COMPLETED ─▶ intent REFUNDED, escalation resolved
 * </pre>
 *
 * <p>The escalation is the fallback, never the plan: it is raised only when the provider refused the
 * refund, failed it, or did not answer.
 */
@WorkflowImpl(taskQueues = TaskQueues.CHECKOUT)
public class LatePaymentRefundWorkflowImpl implements LatePaymentRefundWorkflow {

    private final LatePaymentRefundActivities refunds =
            Workflow.newActivityStub(LatePaymentRefundActivities.class, LatePaymentRefundRules.writeOptions());
    private final LatePaymentRefundActivities provider =
            Workflow.newActivityStub(LatePaymentRefundActivities.class, LatePaymentRefundRules.providerOptions());

    private Stage stage = Stage.OPENING;
    private boolean evidence;

    @Override
    public Result run(Start start) {
        String reservationId = start.reservationId();
        Result result;
        try {
            result = refund(reservationId);
        } catch (ActivityFailure failure) {
            result = escalate(reservationId, null, "the automatic refund was refused or got no answer: " + failure.getMessage());
        }
        stage = Stage.CLOSED;
        Workflow.await(Workflow::isEveryHandlerFinished);
        return result;
    }

    private Result refund(String reservationId) {
        View view = refunds.open(reservationId);
        if (view.state() == State.COMPLETED) {
            return new Result(Outcome.REFUNDED, view.refundId());
        }
        if (view.state() == State.REQUESTED) {
            stage = Stage.SUBMITTING;
            view = provider.submit(reservationId);
        }
        if (view.state() == State.FAILED) {
            return escalate(reservationId, view.refundId(), "the provider refused the refund");
        }

        stage = Stage.AWAITING_PROVIDER;
        long started = Workflow.currentTimeMillis();
        for (int poll = 0; ; poll++) {
            evidence = false;
            Workflow.await(LatePaymentRefundRules.pollDelay(poll), () -> evidence);
            Answer answer = provider.providerStatus(reservationId);
            switch (answer.outcome()) {
                case COMPLETED -> {
                    view = refunds.complete(reservationId, answer.reference());
                    return new Result(Outcome.REFUNDED, view.refundId());
                }
                case FAILED -> {
                    view = refunds.fail(reservationId, answer.failureCode());
                    return escalate(reservationId, view.refundId(), "the provider failed the refund: " + answer.failureCode());
                }
                case PENDING -> {
                    if (Workflow.currentTimeMillis() - started >= LatePaymentRefundRules.ANSWER_LIMIT.toMillis()) {
                        return escalate(reservationId, view.refundId(), "the provider gave no answer for the refund in "
                                + LatePaymentRefundRules.ANSWER_LIMIT);
                    }
                }
            }
        }
    }

    private Result escalate(String reservationId, String refundId, String reason) {
        refunds.escalate(reservationId, reason);
        return new Result(Outcome.ESCALATED, refundId);
    }

    @Override
    public void providerCallback() {
        evidence = true;
    }

    @Override
    public Stage stage() {
        return stage;
    }
}
