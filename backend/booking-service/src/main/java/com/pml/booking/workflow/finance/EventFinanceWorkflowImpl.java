package com.pml.booking.workflow.finance;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.workflow.refund.CancellationRefundsWorkflow;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.ChildWorkflowOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The escrow's life as one execution.
 *
 * <pre>
 *   EventPublished ─▶ escrow opened (ACTIVE)
 *   EventRescheduled ─▶ refund window opened; holdUntil recomputed once holding
 *   EventCompleted ─▶ HOLD until completion + 7 days ─▶ no open disputes ─▶ PAYOUT_ELIGIBLE ─▶ commission earned
 *   EventCancelled ─▶ every live ticket refunded in batches, escrow closed
 * </pre>
 */
@WorkflowImpl(taskQueues = TaskQueues.FINANCE)
public class EventFinanceWorkflowImpl implements EventFinanceWorkflow {

    private final EventFinanceActivities finance =
            Workflow.newActivityStub(EventFinanceActivities.class, EventFinanceRules.financeOptions());

    private final Set<String> seenFacts = new HashSet<>();
    private final Deque<Rescheduled> reschedules = new ArrayDeque<>();
    private Published published;
    private Completed completed;
    private Cancelled cancelled;
    private boolean disputeClosed;
    private Stage stage = Stage.AWAITING_FACTS;

    @Override
    public void run(Start start) {
        String eventId = start.eventId();
        boolean anyFact = Workflow.await(EventFinanceRules.IDLE_LIMIT,
                () -> published != null || completed != null || cancelled != null);
        if (!anyFact) {
            close();
            return;
        }

        if (published != null) {
            finance.openEscrow(eventId, published.organizationId(), published.startsAtMillis());
            stage = Stage.SELLING;
        }
        while (completed == null && cancelled == null) {
            Workflow.await(() -> completed != null || cancelled != null || !reschedules.isEmpty());
            applyReschedules(eventId, false);
        }
        if (cancelled != null) {
            refundEveryone(eventId);
            return;
        }

        stage = Stage.HOLDING;
        long holdUntil = finance.startHold(eventId, completed.completedAtMillis());
        while (true) {
            long remaining = holdUntil - Workflow.currentTimeMillis();
            if (remaining <= 0 && reschedules.isEmpty()) {
                break;
            }
            Workflow.await(Duration.ofMillis(Math.max(remaining, 0)), () -> !reschedules.isEmpty() || cancelled != null);
            if (cancelled != null) {
                refundEveryone(eventId);
                return;
            }
            Long moved = applyReschedules(eventId, true);
            if (moved != null) {
                holdUntil = moved;
            }
        }

        stage = Stage.AWAITING_DISPUTES;
        while (finance.openDisputes(eventId) > 0) {
            disputeClosed = false;
            Workflow.await(EventFinanceRules.DISPUTE_RECHECK, () -> disputeClosed);
        }
        finance.makePayoutEligible(eventId);
        finance.recogniseCommission(eventId);
        stage = Stage.ELIGIBLE;
        close();
    }

    private Long applyReschedules(String eventId, boolean holding) {
        Long holdUntil = null;
        while (!reschedules.isEmpty()) {
            Rescheduled fact = reschedules.poll();
            finance.openRescheduleWindow(eventId, fact.previousStartsAtMillis(), fact.newStartsAtMillis());
            if (holding) {
                holdUntil = finance.rescheduleHold(eventId, fact.newStartsAtMillis());
            }
        }
        return holdUntil;
    }

    private void refundEveryone(String eventId) {
        stage = Stage.REFUNDING;
        CancellationRefundsWorkflow refunds = Workflow.newChildWorkflowStub(CancellationRefundsWorkflow.class,
                ChildWorkflowOptions.newBuilder()
                        .setWorkflowId(WorkflowIds.cancellationRefunds(eventId))
                        .setTaskQueue(TaskQueues.FINANCE)
                        .build());
        refunds.run(new CancellationRefundsWorkflow.Start(eventId, cancelled.reason(), null, 0));
        close();
    }

    private void close() {
        if (stage != Stage.ELIGIBLE) {
            stage = Stage.CLOSED;
        }
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    // ---- signals -------------------------------------------------------------------------------

    @Override
    public void published(Published fact) {
        if (seenFacts.add(fact.envelopeId())) {
            published = fact;
        }
    }

    @Override
    public void completed(Completed fact) {
        if (seenFacts.add(fact.envelopeId())) {
            completed = fact;
        }
    }

    @Override
    public void cancelled(Cancelled fact) {
        if (seenFacts.add(fact.envelopeId())) {
            cancelled = fact;
        }
    }

    @Override
    public void rescheduled(Rescheduled fact) {
        if (seenFacts.add(fact.envelopeId())) {
            reschedules.add(fact);
        }
    }

    @Override
    public void disputeClosed(String chargebackId) {
        disputeClosed = true;
    }

    @Override
    public Stage stage() {
        return stage;
    }
}
