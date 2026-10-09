package com.pml.booking.workflow.chargeback;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.constants.ChargebackStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Receive, open the dispute, decide by the response deadline, recover or reverse, close.
 *
 * <pre>
 *   receive ─▶ dispute opened on the escrow (payout blocked)
 *     ├─ undecided 24 h before the deadline ─▶ finance escalated, still waiting
 *     ├─ accept, or no decision by the deadline ─▶ recovery waterfall ─┐
 *     └─ dispute ─▶ provider outcome: lost ─▶ recovery waterfall ──────┤
 *                                     won ──────────────────────────────┴▶ dispute closed, event finance woken
 * </pre>
 */
@WorkflowImpl(taskQueues = TaskQueues.FINANCE)
public class ChargebackWorkflowImpl implements ChargebackWorkflow {

    private final ChargebackActivities chargebacks = Workflow.newActivityStub(ChargebackActivities.class, ChargebackRules.options());

    private View view;
    private boolean refused;
    private boolean accepted;
    private boolean disputed;
    private boolean outcomeRecorded;
    private boolean won;

    @Override
    public void run(Start start) {
        Workflow.await(ChargebackRules.SUBMIT_WINDOW, () -> view != null || refused);
        if (view == null) {
            close();
            return;
        }
        view = chargebacks.openDispute(view.recordId());

        boolean decided = awaitDecisionUntil(view.responseDeadlineMillis() - ChargebackRules.ESCALATE_BEFORE_DEADLINE.toMillis());
        if (!decided) {
            chargebacks.escalate(view.recordId());
            decided = awaitDecisionUntil(view.responseDeadlineMillis());
        }
        if (!decided) {
            view = chargebacks.accept(view.recordId(), ChargebackRules.SYSTEM_ACTOR, "the response deadline passed without a dispute");
            accepted = true;
        }
        if (disputed && !outcomeRecorded) {
            Workflow.await(ChargebackRules.OUTCOME_LIMIT, () -> outcomeRecorded);
        }
        if (accepted) {
            view = chargebacks.recover(view.recordId());
        }
        if (accepted || outcomeRecorded) {
            chargebacks.closeDispute(view.recordId());
        }
        close();
    }

    private boolean awaitDecisionUntil(long untilMillis) {
        long remaining = untilMillis - Workflow.currentTimeMillis();
        if (decided() || remaining <= 0) {
            return decided();
        }
        return Workflow.await(Duration.ofMillis(remaining), this::decided);
    }

    private boolean decided() {
        return accepted || disputed || outcomeRecorded;
    }

    private void close() {
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    // ---- updates -------------------------------------------------------------------------------

    @Override
    public View receive(Receive command) {
        if (view != null) {
            return view;
        }
        try {
            view = chargebacks.receive(command);
            return view;
        } catch (ActivityFailure failure) {
            refused = true;
            throw Refusals.rethrow(failure);
        }
    }

    @Override
    public void validateStartReview(Decision decision) {
        requireStatus(ChargebackRules.canReview(statusOrNull()), "a review starts from RECEIVED");
    }

    @Override
    public View startReview(Decision decision) {
        return step(() -> chargebacks.startReview(view.recordId(), decision.actorId(), decision.note()));
    }

    @Override
    public void validateAccept(Decision decision) {
        requireStatus(ChargebackRules.canDecide(statusOrNull()), "a chargeback is accepted before it is disputed or decided");
    }

    @Override
    public View accept(Decision decision) {
        View accepting = step(() -> chargebacks.accept(view.recordId(), decision.actorId(), decision.note()));
        accepted = true;
        return accepting;
    }

    @Override
    public void validateDispute(Dispute command) {
        requireStatus(ChargebackRules.canDecide(statusOrNull()), "a chargeback is disputed before it is accepted or decided");
        if (command.notes() == null || command.notes().isBlank()) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a dispute needs notes");
        }
    }

    @Override
    public View dispute(Dispute command) {
        View disputing = step(() -> chargebacks.dispute(view.recordId(), command));
        disputed = true;
        return disputing;
    }

    @Override
    public void validateRecordOutcome(Outcome outcome) {
        requireStatus(ChargebackRules.awaitsOutcome(statusOrNull()), "an outcome is recorded for a disputed chargeback");
    }

    @Override
    public View recordOutcome(Outcome outcome) {
        View recorded = step(() -> outcome.won()
                ? chargebacks.recordWin(view.recordId(), outcome.actorId(), outcome.notes())
                : chargebacks.recordLoss(view.recordId(), outcome.actorId(), outcome.notes()));
        won = outcome.won();
        outcomeRecorded = true;
        return recorded;
    }

    @Override
    public View current() {
        return view;
    }

    private View step(Supplier<View> activity) {
        try {
            view = activity.get();
            return view;
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
    }

    private ChargebackStatus statusOrNull() {
        return view == null ? null : view.status();
    }

    private static void requireStatus(boolean allowed, String message) {
        if (!allowed) {
            throw Refusals.refusal(ErrorCode.RESOURCE_CONFLICT, message);
        }
    }
}
