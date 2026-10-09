package com.pml.identity.workflow.reminder;

import com.pml.identity.domain.enums.ReminderStatus;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusal;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.reminder.ReminderRules.Offset;
import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Sleep until the next offset, send it, repeat; a reschedule wakes the sleep early.
 *
 * <p>Each wait is interrupted by a version counter that every reschedule and cancel raises, so a
 * moved event recomputes from the new start instead of firing at the old one. An offset whose moment
 * passed while the event moved is dropped rather than sent late.
 */
@WorkflowImpl(taskQueues = TaskQueues.NOTIFY)
public class ReminderWorkflowImpl implements ReminderWorkflow {

    private final ReminderActivities records =
            Workflow.newActivityStub(ReminderActivities.class, ReminderRules.recordOptions());
    private final ReminderActivities patient =
            Workflow.newActivityStub(ReminderActivities.class, ReminderRules.patientOptions());

    private String reminderId;
    private View view;
    private boolean loading;
    private int version;
    private final EnumSet<Offset> sent = EnumSet.noneOf(Offset.class);

    @Override
    public void run(Start start) {
        if (reminderId == null) {
            reminderId = start.reminderId();
        }
        ensureLoaded();
        if (view.status() == null) {
            Workflow.await(ReminderRules.FIRST_COMMAND_WINDOW, () -> view.status() != null);
        }

        while (ReminderRules.waiting(view.status())) {
            long current = Workflow.currentTimeMillis();
            Optional<Offset> next = ReminderRules.next(view.eventStartsAtMillis(), current, sent);
            int seenVersion = version;
            if (next.isEmpty()) {
                View completed = patient.complete(reminderId);
                if (version == seenVersion) {
                    view = completed;
                }
                continue;
            }
            Offset offset = next.get();
            Duration wait = Duration.ofMillis(ReminderRules.fireAt(view.eventStartsAtMillis(), offset) - current);
            boolean interrupted = Workflow.await(wait,
                    () -> version != seenVersion || !ReminderRules.waiting(view.status()));
            if (interrupted) {
                continue;
            }
            patient.dispatch(reminderId, offset);
            sent.add(offset);
        }
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    @Override
    public void validateReschedule(Reschedule reschedule) {
        requireReminder(reschedule.reminderId());
        refuseIf(ReminderRules.rescheduleRefusal(reschedule, view));
    }

    @Override
    public View reschedule(Reschedule reschedule) {
        begin(reschedule.reminderId());
        refuseIf(ReminderRules.rescheduleRefusal(reschedule, view));
        view = command(() -> records.save(reschedule));
        version++;
        return view;
    }

    @Override
    public void validateCancel(Cancel cancel) {
        requireReminder(cancel.reminderId());
        if (view != null) {
            refuseIf(ReminderRules.cancelRefusal(view, cancel.actorId()));
        }
    }

    @Override
    public View cancel(Cancel cancel) {
        begin(cancel.reminderId());
        refuseIf(ReminderRules.cancelRefusal(view, cancel.actorId()));
        if (view.status() != ReminderStatus.CANCELLED) {
            view = command(() -> records.cancel(reminderId));
            version++;
        }
        return view;
    }

    @Override
    public View current() {
        return view;
    }

    @Override
    public List<Offset> sent() {
        return List.copyOf(sent);
    }

    private void begin(String commandReminderId) {
        if (reminderId == null) {
            reminderId = commandReminderId;
        }
        requireReminder(commandReminderId);
        ensureLoaded();
    }

    private void requireReminder(String commandReminderId) {
        if (commandReminderId == null || commandReminderId.isBlank()) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a command names its reminder");
        }
        if (reminderId != null && !reminderId.equals(commandReminderId)) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "this execution belongs to another reminder");
        }
    }

    private void ensureLoaded() {
        if (view != null) {
            return;
        }
        if (loading) {
            Workflow.await(() -> view != null);
            return;
        }
        loading = true;
        try {
            view = patient.load(reminderId);
        } finally {
            loading = false;
        }
    }

    private static View command(Supplier<View> activity) {
        try {
            return activity.get();
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
    }

    private static void refuseIf(Optional<Refusal> refusal) {
        if (refusal.isPresent()) {
            throw refusal.get().failure();
        }
    }
}
