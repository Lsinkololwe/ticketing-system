package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import com.pml.shared.workflow.Refusals;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Changes are applied one at a time, retried, and never twice for one event.
 *
 * <p>The execution is an entity: it lives while changes arrive, closes after a day of quiet, and
 * continues as new when the server suggests it or a run has applied its budget, carrying the
 * remembered ids and any unapplied changes so a redelivery straddling the boundary is still dropped.
 */
@WorkflowImpl(taskQueues = TaskQueues.ONBOARDING)
public class UserSyncWorkflowImpl implements UserSyncWorkflow {

    /** Version 1: changes carry their realm, staff are adopted, and a failed change is recorded and surfaced. */
    static final String REALM_AWARE = "user-sync-realm-aware";

    private final UserSyncActivities users =
            Workflow.newActivityStub(UserSyncActivities.class, UserSyncRules.syncOptions());

    private final LinkedHashSet<String> seen = new LinkedHashSet<>();
    private final Deque<Change> pending = new ArrayDeque<>();
    private int applied;
    private int duplicates;
    private int failed;

    @Override
    public void run(Start start) {
        String keycloakUserId = start.keycloakUserId();
        if (start.seenEventIds() != null) {
            start.seenEventIds().forEach(id -> UserSyncRules.remember(seen, id));
        }
        if (start.pending() != null) {
            List<Change> carried = start.pending();
            for (int index = carried.size() - 1; index >= 0; index--) {
                pending.addFirst(carried.get(index));
            }
        }

        int processed = 0;
        while (true) {
            if (!Workflow.await(UserSyncRules.IDLE_CLOSE, () -> !pending.isEmpty())) {
                return;
            }
            apply(keycloakUserId, pending.poll());
            processed++;
            if (UserSyncRules.shouldContinueAsNew(Workflow.getInfo().isContinueAsNewSuggested(), processed,
                    start.maxChangesPerRun())) {
                Workflow.continueAsNew(new Start(keycloakUserId, List.copyOf(seen), List.copyOf(pending),
                        start.maxChangesPerRun()));
            }
        }
    }

    private void apply(String keycloakUserId, Change change) {
        boolean realmAware = Workflow.getVersion(REALM_AWARE, Workflow.DEFAULT_VERSION, 1) >= 1;
        try {
            if (realmAware) {
                UserSyncActivities.Target target = new UserSyncActivities.Target(change.realm(), keycloakUserId);
                switch (change.kind()) {
                    case DELETE -> users.deleteIn(target);
                    case LOGIN -> users.recordLoginIn(target);
                    case SYNC -> users.syncIn(target);
                }
            } else {
                switch (change.kind()) {
                    case DELETE -> users.delete(keycloakUserId);
                    case LOGIN -> users.recordLogin(keycloakUserId);
                    case SYNC -> users.sync(keycloakUserId);
                }
            }
            applied++;
        } catch (ActivityFailure failure) {
            failed++;
            if (!realmAware) {
                return;
            }
            // A failure is written down as a fact, and it is not swallowed: a refusal (a conflict
            // retrying cannot fix) is recorded and the next change carries on; a change that
            // exhausted its retries ends the run, which Temporal shows as failed, and the next
            // change - or the nightly reconciliation - starts a fresh one.
            users.recordFailure(new UserSyncActivities.Failure(change.realm(), keycloakUserId,
                    change.kind().name(), Refusals.typeOf(failure, "UNKNOWN")));
            boolean refusal = failure.getCause() instanceof ApplicationFailure application && application.isNonRetryable();
            if (!refusal) {
                throw failure;
            }
        }
    }

    @Override
    public void keycloakEvent(Change change) {
        if (UserSyncRules.remember(seen, change.eventId())) {
            pending.addLast(change);
        } else {
            duplicates++;
        }
    }

    @Override
    public Progress progress() {
        return new Progress(applied, duplicates, failed, pending.size(), List.copyOf(seen));
    }
}
