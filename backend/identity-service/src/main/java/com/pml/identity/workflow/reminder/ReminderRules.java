package com.pml.identity.workflow.reminder;

import com.pml.identity.domain.enums.ReminderStatus;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusal;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Reschedule;
import com.pml.identity.workflow.reminder.ReminderWorkflow.View;
import com.pml.shared.error.ErrorCode;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * When a reminder fires, and what it is keyed on.
 */
public final class ReminderRules {

    /** {@code notification.reminder.offsets}, in the order they fire. */
    public enum Offset {
        T_MINUS_24H(Duration.ofHours(24), "event.reminder.24h"),
        T_MINUS_1H(Duration.ofHours(1), "event.reminder.1h");

        private final Duration beforeStart;
        private final String templateKey;

        Offset(Duration beforeStart, String templateKey) {
            this.beforeStart = beforeStart;
            this.templateKey = templateKey;
        }

        public Duration beforeStart() {
            return beforeStart;
        }

        public String templateKey() {
            return templateKey;
        }
    }

    /** An execution started with no schedule, for a reminder that does not exist, closes after this long. */
    public static final Duration FIRST_COMMAND_WINDOW = Duration.ofMinutes(5);

    private ReminderRules() {
    }

    public static long fireAt(long eventStartsAtMillis, Offset offset) {
        return eventStartsAtMillis - offset.beforeStart().toMillis();
    }

    /**
     * The next offset to wait for: the first not yet sent whose moment is still ahead.
     *
     * <p>An offset whose moment has passed unsent is dropped, never sent late — a reminder that
     * the event starts in an hour, delivered after it started, is noise.
     */
    public static Optional<Offset> next(long eventStartsAtMillis, long nowMillis, Set<Offset> sent) {
        for (Offset offset : Offset.values()) {
            if (!sent.contains(offset) && fireAt(eventStartsAtMillis, offset) > nowMillis) {
                return Optional.of(offset);
            }
        }
        return Optional.empty();
    }

    /**
     * The event start a reminder implies when only its reminder moment is known: one hour later, so
     * the one-hour reminder fires exactly at the moment the row names.
     *
     * <p>{@code setEventReminder} carries no event start, and identity does not hold one; this keeps
     * such a reminder firing when its row says rather than never.
     */
    public static long impliedEventStart(long reminderAtMillis) {
        return reminderAtMillis + Offset.T_MINUS_1H.beforeStart().toMillis();
    }

    /** {@code reminder:{ticketId}:{reminderType}}. */
    public static String deduplicationKey(String ticketId, Offset offset) {
        return "reminder:" + ticketId + ":" + offset.name();
    }

    public static Optional<Refusal> rescheduleRefusal(Reschedule reschedule, View view) {
        if (blank(reschedule.reminderId()) || blank(reschedule.userId()) || blank(reschedule.ticketId())
                || reschedule.eventStartsAtMillis() <= 0) {
            return Optional.of(new Refusal(ErrorCode.COMMAND_NOT_WELL_FORMED,
                    "a reminder names its id, holder, ticket and event start"));
        }
        if (view != null && view.userId() != null && !view.userId().equals(reschedule.userId())) {
            return Optional.of(new Refusal(ErrorCode.ACTOR_NOT_PERMITTED, "the reminder belongs to another user"));
        }
        return Optional.empty();
    }

    public static Optional<Refusal> cancelRefusal(View view, String actorId) {
        if (view == null || view.status() == null) {
            return Optional.of(new Refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "no such reminder"));
        }
        if (actorId == null || !actorId.equals(view.userId())) {
            return Optional.of(new Refusal(ErrorCode.ACTOR_NOT_PERMITTED, "the reminder belongs to another user"));
        }
        return Optional.empty();
    }

    public static boolean waiting(ReminderStatus status) {
        return status == ReminderStatus.SCHEDULED;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    static ActivityOptions recordOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.NOTIFY)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(5)
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumInterval(Duration.ofSeconds(30))
                        .build())
                .build();
    }

    static ActivityOptions patientOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.NOTIFY)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setMaximumInterval(Duration.ofMinutes(5))
                        .build())
                .build();
    }
}
