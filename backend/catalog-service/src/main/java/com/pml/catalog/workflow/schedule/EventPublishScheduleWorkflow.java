package com.pml.catalog.workflow.schedule;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * An approved event waiting for the time its organizer chose to go live.
 *
 * <p>Addressed as {@code event-publish/{eventId}}. It sleeps until {@code publishAt}, then publishes
 * the event through the same lifecycle path an organizer's own Publish takes, so the event's
 * completion timer starts with it. The organizer can move the time or stop the wait with a signal;
 * MongoDB holds the event, and the activity re-reads it before acting, so a schedule the organizer
 * has since changed by other means publishes nothing.
 */
@WorkflowInterface
public interface EventPublishScheduleWorkflow {

    @WorkflowMethod
    void run(Start start);

    /** The organizer chose another time; the wait restarts for it. */
    @SignalMethod
    void reschedule(long publishAtMillis);

    /** The organizer withdrew the schedule. */
    @SignalMethod
    void cancel();

    record Start(String eventId, long publishAtMillis) {
    }
}
