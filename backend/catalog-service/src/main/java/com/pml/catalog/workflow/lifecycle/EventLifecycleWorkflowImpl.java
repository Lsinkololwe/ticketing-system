package com.pml.catalog.workflow.lifecycle;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusal;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.constants.EventStatus;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The completion timer and the commands that move or end it.
 *
 * <pre>
 *   publish ─▶ PUBLISHED ── timer to endsAt ─────────────▶ complete (COMPLETED + catalog.EventCompleted)
 *                 │ ├─ reschedule (≤ 3) ─▶ timer recomputed from the new end
 *                 │ ├─ cancel ───────────▶ CANCELLED + catalog.EventCancelled, execution closes
 *                 │ └─ unpublish (0 sold) ▶ APPROVED, execution closes; a later publish opens a new one
 * </pre>
 *
 * <p>The timer never fires into a command that is still writing: completion waits for every
 * in-flight update, then re-reads the schedule, so a reschedule accepted a moment before the end is
 * honoured rather than overtaken.
 */
@WorkflowImpl(taskQueues = TaskQueues.LIFECYCLE)
public class EventLifecycleWorkflowImpl implements EventLifecycleWorkflow {

    private final EventLifecycleActivities events =
            Workflow.newActivityStub(EventLifecycleActivities.class, LifecycleRules.activityOptions());

    private String eventId;
    private View view;
    private boolean firstCommandRefused;
    private boolean scheduleChanged;
    private int inFlight;

    @Override
    public void run(Start start) {
        eventId = start.eventId();
        if (start.adopt()) {
            View loaded = events.current(eventId);
            if (view == null) {
                view = loaded;
            }
        } else {
            Workflow.await(LifecycleRules.COMMAND_WINDOW, () -> view != null || firstCommandRefused);
        }

        while (view != null && view.status() == EventStatus.PUBLISHED) {
            scheduleChanged = false;
            Duration wait = LifecycleRules.untilCompletion(view.endsAtMillis(), Workflow.currentTimeMillis());
            boolean woken = !wait.isZero()
                    && Workflow.await(wait, () -> scheduleChanged || view.status() != EventStatus.PUBLISHED);
            Workflow.await(() -> inFlight == 0);
            if (woken || scheduleChanged || view == null || view.status() != EventStatus.PUBLISHED
                    || !LifecycleRules.completionDue(view.endsAtMillis(), Workflow.currentTimeMillis())) {
                continue;
            }
            complete();
        }
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    private void complete() {
        try {
            view = events.complete(eventId);
        } catch (ActivityFailure failure) {
            // The write was refused: the stored event decides what happens next. One still published
            // is tried again later rather than in a tight loop; any other state ends the execution.
            view = events.current(eventId);
            if (view != null && view.status() == EventStatus.PUBLISHED) {
                Workflow.sleep(LifecycleRules.COMPLETION_RETRY);
            }
        }
    }

    // ---- updates -------------------------------------------------------------------------------

    @Override
    public void validatePublish(Command command) {
        raise(LifecycleRules.publishRefusal(status()));
    }

    @Override
    public View publish(Command command) {
        return apply(() -> events.publish(command.eventId()));
    }

    @Override
    public void validateReschedule(Reschedule command) {
        raise(LifecycleRules.rescheduleRefusal(status(), view != null ? view.rescheduleCount() : 0,
                command.newStartsAtMillis(), command.reason(), Workflow.currentTimeMillis()));
    }

    @Override
    public View reschedule(Reschedule command) {
        return apply(() -> events.reschedule(command.eventId(), command.newStartsAtMillis(), command.reason()));
    }

    @Override
    public void validateCancel(Cancel command) {
        raise(LifecycleRules.cancelRefusal(status(), command.reason()));
    }

    @Override
    public View cancel(Cancel command) {
        return apply(() -> events.cancel(command.eventId(), command.reason()));
    }

    @Override
    public void validateUnpublish(Command command) {
        raise(LifecycleRules.unpublishRefusal(status()));
    }

    @Override
    public View unpublish(Command command) {
        return apply(() -> events.unpublish(command.eventId()));
    }

    @Override
    public View current() {
        return view;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private View apply(Supplier<View> activity) {
        inFlight++;
        try {
            View next = activity.get();
            if (view != null && next.endsAtMillis() != view.endsAtMillis()) {
                scheduleChanged = true;
            }
            view = next;
            return view;
        } catch (ActivityFailure failure) {
            if (view == null) {
                firstCommandRefused = true;
            }
            throw Refusals.rethrow(failure);
        } finally {
            inFlight--;
        }
    }

    private EventStatus status() {
        return view != null ? view.status() : null;
    }

    private static void raise(Optional<Refusal> refusal) {
        if (refusal.isPresent()) {
            throw refusal.get().failure();
        }
    }
}
