package com.pml.identity.workflow.usersync;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.List;

/**
 * One Keycloak user's changes, applied to {@code identity_users} in the order they arrive.
 *
 * <p>Addressed as {@code user-sync/{keycloakUserId}} and reached by signal-with-start, so the
 * endpoint Keycloak's listener calls answers as soon as the change is recorded and never waits on
 * MongoDB. A change whose originating event id was already seen is dropped; retries happen here
 * rather than being lost with a failed HTTP call.
 */
@WorkflowInterface
public interface UserSyncWorkflow {

    @WorkflowMethod
    void run(Start start);

    @SignalMethod
    void keycloakEvent(Change change);

    @QueryMethod
    Progress progress();

    /**
     * @param seenEventIds     ids already applied, carried across Continue-As-New
     * @param pending          changes received and not yet applied, carried across Continue-As-New
     * @param maxChangesPerRun a run continues as new after this many changes, even unprompted
     */
    record Start(String keycloakUserId, List<String> seenEventIds, List<Change> pending, int maxChangesPerRun) {
    }

    /**
     * {@code eventId} is null when the listener supplied nothing to derive one from; such a change is
     * never deduplicated. {@code realm} is the Keycloak realm the user belongs to; null means the buyer
     * realm, which is also what a change recorded before the field existed deserialises to.
     */
    record Change(String eventId, Kind kind, boolean registration, long occurredAtMillis, String realm) {
    }

    enum Kind { SYNC, LOGIN, DELETE }

    record Progress(int applied, int duplicates, int failed, int pending, List<String> recentEventIds) {
    }
}
