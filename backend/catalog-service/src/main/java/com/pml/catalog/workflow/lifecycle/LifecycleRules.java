package com.pml.catalog.workflow.lifecycle;

import com.pml.catalog.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusal;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The lifecycle decisions that need neither a server nor a database.
 *
 * <p>Kept out of the workflow class so each is tested at layer 1, and shared with the service that
 * performs the write, so the cap a validator enforces and the cap the document enforces are one
 * constant.
 */
public final class LifecycleRules {

    /** How many times an event may be rescheduled, {@code catalog.event.max-reschedules}. */
    public static final int MAX_RESCHEDULES = 3;

    /** A started execution that receives no command in this window closes. */
    public static final Duration COMMAND_WINDOW = Duration.ofMinutes(5);

    /** An event that declares no end is treated as over this long after it starts. */
    public static final Duration ASSUMED_DURATION = Duration.ofDays(1);

    /** How long a completion that could not be written waits before it is tried again. */
    public static final Duration COMPLETION_RETRY = Duration.ofHours(1);

    /** Cancellation is legal from these states only. */
    public static final Set<EventStatus> CANCELLABLE =
            Collections.unmodifiableSet(EnumSet.of(EventStatus.APPROVED, EventStatus.PUBLISHED));

    private LifecycleRules() {
    }

    /** When an event is over: its declared end, or its start plus {@link #ASSUMED_DURATION}. */
    public static Instant endsAt(Instant startsAt, Instant endsAt) {
        if (endsAt != null) {
            return endsAt;
        }
        if (startsAt == null) {
            throw new IllegalArgumentException("an event with no start time has no end");
        }
        return startsAt.plus(ASSUMED_DURATION);
    }

    /** The completion timer: never negative, so an event already over completes at once. */
    public static Duration untilCompletion(long endsAtMillis, long nowMillis) {
        return Duration.ofMillis(Math.max(0L, endsAtMillis - nowMillis));
    }

    public static boolean completionDue(long endsAtMillis, long nowMillis) {
        return nowMillis >= endsAtMillis;
    }

    public static boolean canReschedule(int rescheduleCount) {
        return rescheduleCount < MAX_RESCHEDULES;
    }

    /**
     * A rescheduled event keeps its duration. With no declared end there is nothing to move,
     * and {@link #endsAt} supplies one from the new start.
     */
    public static Instant shiftedEnd(Instant previousStart, Instant previousEnd, Instant newStart) {
        if (previousEnd == null || previousStart == null) {
            return previousEnd == null ? null : newStart.plus(ASSUMED_DURATION);
        }
        Duration length = Duration.between(previousStart, previousEnd);
        return newStart.plus(length.isNegative() ? Duration.ZERO : length);
    }

    /**
     * Whether a reschedule may be accepted.
     *
     * @param status the event's status when known, or {@code null} before the first command has
     *               loaded it, in which case the write itself decides the state question
     */
    public static Optional<Refusal> rescheduleRefusal(EventStatus status, int rescheduleCount,
                                                      long newStartsAtMillis, String reason, long nowMillis) {
        if (reason == null || reason.isBlank()) {
            return refuse(ErrorCode.COMMAND_NOT_WELL_FORMED, "a reschedule needs a reason");
        }
        if (newStartsAtMillis <= nowMillis) {
            return refuse(ErrorCode.COMMAND_NOT_WELL_FORMED, "the new start must be in the future");
        }
        if (status != null && status != EventStatus.PUBLISHED) {
            return refuse(ErrorCode.EVENT_STATE_INVALID, "only a published event is rescheduled; this one is " + status);
        }
        if (status != null && !canReschedule(rescheduleCount)) {
            return refuse(ErrorCode.EVENT_STATE_INVALID,
                    "an event is rescheduled at most " + MAX_RESCHEDULES + " times");
        }
        return Optional.empty();
    }

    /** A cancellation carries a reason and is legal from APPROVED and PUBLISHED. */
    public static Optional<Refusal> cancelRefusal(EventStatus status, String reason) {
        if (reason == null || reason.isBlank()) {
            return refuse(ErrorCode.COMMAND_NOT_WELL_FORMED, "a cancellation needs a reason");
        }
        if (status != null && !CANCELLABLE.contains(status)) {
            return refuse(ErrorCode.EVENT_STATE_INVALID, "an event is cancelled from APPROVED or PUBLISHED; this one is " + status);
        }
        return Optional.empty();
    }

    /** Only a published event is unpublished; the sold-ticket check belongs to the write. */
    public static Optional<Refusal> unpublishRefusal(EventStatus status) {
        if (status != null && status != EventStatus.PUBLISHED) {
            return refuse(ErrorCode.EVENT_STATE_INVALID, "only a published event is unpublished; this one is " + status);
        }
        return Optional.empty();
    }

    /** Publishing starts from APPROVED. */
    public static Optional<Refusal> publishRefusal(EventStatus status) {
        if (status != null && status != EventStatus.APPROVED) {
            return refuse(ErrorCode.EVENT_STATE_INVALID, "only an approved event is published; this one is " + status);
        }
        return Optional.empty();
    }

    /** MongoDB writes: retried until they land; a refusal is non-retryable and ends the attempt at once. */
    static ActivityOptions activityOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.LIFECYCLE)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private static Optional<Refusal> refuse(ErrorCode code, String message) {
        return Optional.of(new Refusal(code, message));
    }
}
