package com.pml.identity.workflow.reminder;

import com.pml.identity.workflow.reminder.ReminderRules.Offset;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Reschedule;
import com.pml.identity.workflow.reminder.ReminderWorkflow.View;
import io.temporal.activity.ActivityInterface;

/**
 * The reminder row and its sends; each safe to run twice.
 */
@ActivityInterface(namePrefix = "Reminder")
public interface ReminderActivities {

    View load(String reminderId);

    /** Upserts the row as {@code SCHEDULED} at the given event start. */
    View save(Reschedule reschedule);

    View cancel(String reminderId);

    /** Both offsets are behind the reminder: {@code SCHEDULED} to {@code SENT}. */
    View complete(String reminderId);

    /** Requests the notification for one offset, keyed so a repeated dispatch sends once. */
    void dispatch(String reminderId, Offset offset);
}
