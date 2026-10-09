package com.pml.catalog.workflow.lifecycle;

import com.pml.shared.constants.EventStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * A published event, from publication to completion, cancellation or unpublishing.
 *
 * <p>Addressed as {@code event/{eventId}} with conflict policy {@code USE_EXISTING}: every command
 * is an Update-with-Start, so a command reaches the open execution when there is one and opens one
 * when there is not. The execution holds the completion timer; MongoDB holds the event, and every
 * answer GraphQL returns is read back from {@code catalog_events}.
 *
 * <p>Every command is an update, so a refusal returns to the caller as a typed error and, when a
 * validator refuses, never enters the history.
 */
@WorkflowInterface
public interface EventLifecycleWorkflow {

    @WorkflowMethod
    void run(Start start);

    @UpdateMethod
    View publish(Command command);

    @UpdateValidatorMethod(updateName = "publish")
    void validatePublish(Command command);

    @UpdateMethod
    View reschedule(Reschedule command);

    @UpdateValidatorMethod(updateName = "reschedule")
    void validateReschedule(Reschedule command);

    @UpdateMethod
    View cancel(Cancel command);

    @UpdateValidatorMethod(updateName = "cancel")
    void validateCancel(Cancel command);

    @UpdateMethod
    View unpublish(Command command);

    @UpdateValidatorMethod(updateName = "unpublish")
    void validateUnpublish(Command command);

    @QueryMethod
    View current();

    /** {@code adopt} takes over a PUBLISHED event whose publication preceded its workflow. */
    record Start(String eventId, boolean adopt) {
    }

    record Command(String eventId, String actorId) {
    }

    record Reschedule(String eventId, String actorId, long newStartsAtMillis, String reason) {
    }

    record Cancel(String eventId, String actorId, String reason) {
    }

    /** The event as the lifecycle needs it: its status, its schedule and how often it has moved. */
    record View(String eventId, EventStatus status, long startsAtMillis, long endsAtMillis, int rescheduleCount) {
    }
}
