package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Kind;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The sync decisions that need neither a server nor a database.
 */
public final class UserSyncRules {

    /** How many originating ids a run remembers; a redelivery arrives within seconds, never hundreds of changes later. */
    public static final int SEEN_LIMIT = 256;

    /** An execution with nothing to apply for this long closes; the next change starts a fresh one. */
    public static final Duration IDLE_CLOSE = Duration.ofDays(1);

    /** A run continues as new after this many changes even when the server has not suggested it. */
    public static final int MAX_CHANGES_PER_RUN = 1000;

    /** Attempts at one change before it is counted failed and the next one is applied. */
    public static final int ATTEMPTS = 10;

    private static final Set<String> SYNC_EVENTS = Set.of("REGISTER", "UPDATE_PROFILE", "UPDATE_EMAIL", "VERIFY_EMAIL",
            "ADMIN_CREATE", "ADMIN_UPDATE");
    private static final Set<String> SYNC_OPERATIONS = Set.of("ADMIN_CREATE", "ADMIN_UPDATE", "CREATE", "UPDATE");
    private static final Set<String> DELETE_OPERATIONS = Set.of("ADMIN_DELETE", "DELETE");

    private UserSyncRules() {
    }

    /**
     * Remembers an originating id, forgetting the oldest beyond {@link #SEEN_LIMIT}.
     *
     * @return {@code true} when the change is new and should be applied
     */
    public static boolean remember(LinkedHashSet<String> seen, String eventId) {
        if (eventId == null) {
            return true;
        }
        if (!seen.add(eventId)) {
            return false;
        }
        Iterator<String> oldest = seen.iterator();
        while (seen.size() > SEEN_LIMIT && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    public static boolean shouldContinueAsNew(boolean suggested, int processedThisRun, int maxPerRun) {
        return suggested || processedThisRun >= (maxPerRun > 0 ? maxPerRun : MAX_CHANGES_PER_RUN);
    }

    /**
     * The originating id of a Keycloak event: the user, the event and operation types, and Keycloak's
     * own timestamp. Keycloak's listener posts no event id, so this is the closest stable identity;
     * with no timestamp there is nothing to tell a redelivery from a genuine repeat, and it is null.
     */
    public static String eventId(String keycloakUserId, String eventType, String operationType, long timestamp) {
        if (timestamp <= 0 || keycloakUserId == null) {
            return null;
        }
        return "keycloak:" + keycloakUserId + ":" + eventType + ":" + (operationType == null ? "-" : operationType)
                + ":" + timestamp;
    }

    /**
     * The originating id of a slim Keycloak event (CONTRACT 4.6): the listener's own event id when it
     * sent one, else the derived id of {@link #eventId(String, String, String, long)} (user, event type, no operation, timestamp).
     */
    public static String listenerEventId(String listenerEventId, String keycloakUserId, String eventType, long timestamp) {
        if (listenerEventId != null && !listenerEventId.isBlank()) {
            return "keycloak:" + listenerEventId;
        }
        return eventId(keycloakUserId, eventType, null, timestamp);
    }

    /** What a slim Keycloak event asks for; empty for an event nothing syncs. */
    public static Optional<Kind> kindOf(String eventType) {
        return kindOf(eventType, null);
    }

    /** Whether the realm is one identity-service syncs: the buyer realm, the staff realm, or unspecified (buyers). */
    public static boolean knownRealm(String realm, String buyerRealm, String staffRealm) {
        return realm == null || realm.isBlank() || realm.equals(buyerRealm) || realm.equals(staffRealm);
    }

    /** What a Keycloak event asks for, checked in the order delete, login, sync; empty for an event nothing syncs. */
    public static Optional<Kind> kindOf(String eventType, String operationType) {
        if ((eventType != null && DELETE_OPERATIONS.contains(eventType))
                || (operationType != null && DELETE_OPERATIONS.contains(operationType))) {
            return Optional.of(Kind.DELETE);
        }
        if ("LOGIN".equals(eventType)) {
            return Optional.of(Kind.LOGIN);
        }
        if ((eventType != null && SYNC_EVENTS.contains(eventType))
                || (operationType != null && SYNC_OPERATIONS.contains(operationType))) {
            return Optional.of(Kind.SYNC);
        }
        return Optional.empty();
    }

    public static boolean registration(String eventType, String operationType) {
        return "REGISTER".equalsIgnoreCase(eventType) || "ADMIN_CREATE".equalsIgnoreCase(eventType)
                || "ADMIN_CREATE".equalsIgnoreCase(operationType);
    }

    static ActivityOptions syncOptions() {
        return ActivityOptions.newBuilder()
                .setTaskQueue(TaskQueues.ONBOARDING)
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setMaximumAttempts(ATTEMPTS)
                        .setInitialInterval(Duration.ofSeconds(2))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofMinutes(5))
                        .build())
                .build();
    }
}
