package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;

/**
 * How a backfill walks Keycloak's users.
 */
public final class UserBackfillRules {

    /** Keycloak users read per page; each page is one run before the execution continues as new. */
    public static final int PAGE_SIZE = 100;

    private UserBackfillRules() {
    }

    /**
     * The originating id a backfilled user's change carries: unique to one backfill, so a later
     * backfill is never dropped as a duplicate, and stable within it, so a retried page is.
     */
    public static String eventId(String backfillId, String keycloakUserId) {
        return "backfill:" + backfillId + ":" + keycloakUserId;
    }

    /** A page shorter than {@link #PAGE_SIZE} is Keycloak's last. */
    public static boolean lastPage(int usersOnPage) {
        return usersOnPage < PAGE_SIZE;
    }

    /** Reading a page changes nothing, so it is retried for as long as Keycloak is unreachable. */
    static ActivityOptions pageOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofSeconds(60))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setMaximumInterval(Duration.ofMinutes(5))
                        .build())
                .build();
    }

    /** Each hand-off is a signal-with-start the sync workflow deduplicates, so a retried page is safe. */
    static ActivityOptions enqueueOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofMinutes(2))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setMaximumInterval(Duration.ofMinutes(5))
                        .build())
                .build();
    }
}
