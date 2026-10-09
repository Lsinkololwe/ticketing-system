package com.pml.catalog.workflow.schedule;

import io.temporal.activity.ActivityInterface;

/** The one write the schedule makes: publishing the event when its time comes. */
@ActivityInterface(namePrefix = "EventPublishSchedule")
public interface EventPublishScheduleActivities {

    /**
     * Publishes the event if it is still APPROVED, still scheduled and still scheduled for
     * {@code publishAtMillis}; otherwise leaves it alone.
     *
     * @return {@code PUBLISHED}, {@code SKIPPED} (the schedule no longer applies) or {@code REFUSED}
     *         (the event could not be published; the schedule is cleared so the organizer sees it)
     */
    String publishDue(String eventId, long publishAtMillis);
}
