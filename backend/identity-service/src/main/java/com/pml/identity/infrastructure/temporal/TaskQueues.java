package com.pml.identity.infrastructure.temporal;

/**
 * The task queues identity's worker polls.
 *
 * <p>Every value here is an entry under {@code spring.temporal.workers} in this service's
 * {@code application.yml}; {@code TemporalRegistryLintTest} holds the two together.
 * Annotations name these constants, never a literal.
 */
public final class TaskQueues {

    /** Organizer onboarding, ownership transfer and Keycloak user sync. */
    public static final String ONBOARDING = "identity-onboarding";

    /** Notifications and reminders; messaging providers fail independently of onboarding. */
    public static final String NOTIFY = "identity-notify";

    /**
     * Account ensure (ET-IDN-004). Separate from onboarding because a buyer's checkout waits on
     * {@code AccountEnsureWorkflow}, and Keycloak admin latency from a bulk backfill must not queue ahead of it.
     */
    public static final String ACCOUNT = "identity-account";

    private TaskQueues() {
    }
}
