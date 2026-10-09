package com.pml.identity.workflow.usersync;

import io.temporal.activity.ActivityInterface;

import java.util.List;

/**
 * The backfill's two steps: read a page of Keycloak user ids, hand them to their sync workflows.
 */
@ActivityInterface(namePrefix = "UserBackfill")
public interface UserBackfillActivities {

    /** One page of Keycloak user ids, in Keycloak's order. */
    List<String> page(int offset, int size);

    /** A SYNC change to each user's {@code UserSyncWorkflow}; a repeat is dropped there by originating id. */
    void enqueue(String backfillId, List<String> keycloakUserIds);
}
