package com.pml.identity.workflow.usersync;

import io.temporal.activity.ActivityInterface;

/**
 * The writes a Keycloak change makes to {@code identity_users}; each an upsert or a
 * no-op when repeated.
 */
@ActivityInterface(namePrefix = "UserSync")
public interface UserSyncActivities {

    /** Reads the user's current state from Keycloak by id and upserts it; a user Keycloak no longer holds is skipped. */
    void sync(String keycloakUserId);

    void recordLogin(String keycloakUserId);

    void delete(String keycloakUserId);

    // ---- realm-aware (workflow version 1) ----------------------------------------------------------

    /** Reads the user from its realm and brings the account in step; staff are adopted or created. */
    void syncIn(Target target);

    void recordLoginIn(Target target);

    /** Marks the account DELETED and releases its contacts; never a hard delete. */
    void deleteIn(Target target);

    /** Writes the failed change down as a fact, so it is more than a log line. */
    void recordFailure(Failure failure);

    record Target(String realm, String keycloakUserId) {
    }

    record Failure(String realm, String keycloakUserId, String change, String reason) {
    }
}
