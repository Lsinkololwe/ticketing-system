package com.pml.identity.workflow.reminder;

import com.pml.identity.domain.enums.ReminderStatus;
import com.pml.identity.workflow.reminder.ReminderRules.Offset;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

/**
 * One ticket holder's reminder for one event, firing at start minus 24 hours and minus 1 hour.
 *
 * <p>Addressed as {@code reminder/{reminderId}} with conflict policy {@code USE_EXISTING}. The two
 * moments are durable timers, recomputed whenever the event start moves; no sweep scans for due
 * reminders.
 */
@WorkflowInterface
public interface ReminderWorkflow {

    @WorkflowMethod
    void run(Start start);

    /** Sets or moves the event start; the pending timer is recomputed. */
    @UpdateMethod
    View reschedule(Reschedule reschedule);

    @UpdateValidatorMethod(updateName = "reschedule")
    void validateReschedule(Reschedule reschedule);

    @UpdateMethod
    View cancel(Cancel cancel);

    @UpdateValidatorMethod(updateName = "cancel")
    void validateCancel(Cancel cancel);

    @QueryMethod
    View current();

    @QueryMethod
    List<Offset> sent();

    record Start(String reminderId) {
    }

    record Reschedule(String reminderId, String userId, String ticketId, long eventStartsAtMillis) {
    }

    record Cancel(String reminderId, String actorId) {
    }

    /** {@code status} is null when no such reminder exists. */
    record View(String reminderId, String userId, String ticketId, ReminderStatus status, long eventStartsAtMillis) {
    }
}
