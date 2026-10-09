package com.pml.identity.workflow.usersync;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Re-sync every Keycloak user into {@code identity_users}, a page at a time.
 *
 * <p>The backfill writes nothing itself: each user becomes a SYNC change on their own
 * {@code user-sync/{keycloakUserId}} execution, so a backfill and a live Keycloak event for the same
 * user are applied one after the other by one writer.
 */
@WorkflowInterface
public interface UserBackfillWorkflow {

    @WorkflowMethod
    void run(Start start);

    @QueryMethod
    Progress progress();

    /**
     * @param backfillId distinguishes this backfill's changes from any earlier one's
     * @param offset     the Keycloak position this run reads from
     * @param enqueued   users handed on by earlier runs of this backfill
     */
    record Start(String backfillId, int offset, int enqueued) {
    }

    record Progress(String backfillId, int offset, int enqueued, boolean finished) {
    }
}
