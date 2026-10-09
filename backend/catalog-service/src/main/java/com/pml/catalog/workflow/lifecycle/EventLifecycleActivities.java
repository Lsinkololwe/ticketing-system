package com.pml.catalog.workflow.lifecycle;

import com.pml.catalog.workflow.lifecycle.EventLifecycleWorkflow.View;
import io.temporal.activity.ActivityInterface;

/**
 * Every write the lifecycle makes to {@code catalog_events}.
 *
 * <p>Each state change is one transaction that moves the status and stages its
 * {@code catalog_outbox} envelope together, and each is safe to run twice: a retried activity finds
 * the event already where it was going and returns it without staging a second envelope.
 */
@ActivityInterface(namePrefix = "EventLifecycle")
public interface EventLifecycleActivities {

    /** The event as stored, or {@code null} when there is none. */
    View current(String eventId);

    /** APPROVED → PUBLISHED, staging {@code catalog.EventPublished}. */
    View publish(String eventId);

    /** A PUBLISHED event moves to a new start, staging {@code catalog.EventRescheduled}. */
    View reschedule(String eventId, long newStartsAtMillis, String reason);

    /** APPROVED or PUBLISHED → CANCELLED, staging {@code catalog.EventCancelled}. */
    View cancel(String eventId, String reason);

    /** PUBLISHED → APPROVED while no ticket is sold. */
    View unpublish(String eventId);

    /** PUBLISHED → COMPLETED, staging {@code catalog.EventCompleted}. */
    View complete(String eventId);
}
