package com.pml.identity.workflow.reminder;

import com.pml.identity.domain.model.EventReminder;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.service.EventReminderService;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.notify.NotificationProcess;
import com.pml.identity.workflow.notify.NotificationRules;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import com.pml.identity.workflow.reminder.ReminderRules.Offset;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Reschedule;
import com.pml.identity.workflow.reminder.ReminderWorkflow.View;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;

/**
 * The reminder activities, adapting {@link EventReminderService} to Temporal.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.NOTIFY)
public class ReminderActivitiesImpl implements ReminderActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final EventReminderService reminders;
    private final NotificationProcess notifications;
    private final Clock clock;

    public ReminderActivitiesImpl(EventReminderService reminders, NotificationProcess notifications, Clock clock) {
        this.reminders = reminders;
        this.notifications = notifications;
        this.clock = clock;
    }

    @Override
    public View load(String reminderId) {
        return await(reminders.findById(reminderId)
                .map(ReminderActivitiesImpl::viewOf)
                .defaultIfEmpty(new View(reminderId, null, null, null, 0L)));
    }

    @Override
    public View save(Reschedule reschedule) {
        long start = reschedule.eventStartsAtMillis();
        long nextMoment = ReminderRules.next(start, clock.millis(), EnumSet.noneOf(Offset.class))
                .map(offset -> ReminderRules.fireAt(start, offset))
                .orElse(ReminderRules.fireAt(start, Offset.T_MINUS_1H));
        return view(reminders.schedule(reschedule.reminderId(), reschedule.userId(), reschedule.ticketId(),
                Instant.ofEpochMilli(start), Instant.ofEpochMilli(nextMoment)));
    }

    @Override
    public View cancel(String reminderId) {
        return view(reminders.cancel(reminderId));
    }

    @Override
    public View complete(String reminderId) {
        return view(reminders.markSent(reminderId));
    }

    @Override
    public void dispatch(String reminderId, Offset offset) {
        EventReminder reminder = await(reminders.findById(reminderId));
        if (reminder == null) {
            return;
        }
        notifications.startNow(new Request(
                ReminderRules.deduplicationKey(reminder.getTicketId(), offset),
                offset.templateKey(), reminder.getUserId(), NotificationRules.EVENT_REMINDER, reminderId));
    }

    static View viewOf(EventReminder reminder) {
        long start = reminder.getEventStartsAt() != null
                ? reminder.getEventStartsAt().toEpochMilli()
                : reminder.getReminderAt() != null ? ReminderRules.impliedEventStart(reminder.getReminderAt().toEpochMilli()) : 0L;
        return new View(reminder.getId(), reminder.getUserId(), reminder.getTicketId(), reminder.getStatus(), start);
    }

    private static View view(Mono<EventReminder> write) {
        return await(write.map(ReminderActivitiesImpl::viewOf));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
