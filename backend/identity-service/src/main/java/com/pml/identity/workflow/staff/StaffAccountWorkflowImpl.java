package com.pml.identity.workflow.staff;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.workflow.staff.StaffAccountActivities.KeycloakUser;
import com.pml.identity.workflow.staff.StaffAccountActivities.Link;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.workflow.Refusals;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

/**
 * The steps of {@link StaffAccountWorkflow}, in order, each an activity.
 *
 * <p>The platform's record is written before anything exists in the login system, so a person who is in
 * Keycloak always has a record that says what they were to become. If a step stays refused or unreachable
 * the creation is undone in a detached scope, which keeps running when the execution itself is cancelled.
 * If the execution is terminated outright nothing can run; the unfinished record is then what a repeated
 * request picks up and completes, and until it does the person cannot sign in.</p>
 */
@WorkflowImpl(taskQueues = TaskQueues.ONBOARDING)
public class StaffAccountWorkflowImpl implements StaffAccountWorkflow {

    private final StaffAccountActivities steps =
            Workflow.newActivityStub(StaffAccountActivities.class, StaffAccountRules.options());
    private final StaffAccountActivities undo =
            Workflow.newActivityStub(StaffAccountActivities.class, StaffAccountRules.undoOptions());
    private final StaffAccountActivities notices =
            Workflow.newActivityStub(StaffAccountActivities.class, StaffAccountRules.noticeOptions());

    private String accountId;
    private String keycloakUserId;
    private String step = "STARTED";

    @Override
    public Created run(Request request) {
        Workflow.upsertTypedSearchAttributes(ProcessSearchAttributes.BUSINESS_ID.valueSet(request.creationId()));
        // Declared from the first release: a later step is added behind a higher version.
        Workflow.getVersion(StaffAccountRules.STEPS, Workflow.DEFAULT_VERSION, 1);

        boolean recorded = false;
        boolean finished = false;
        try {
            StaffAccountActivities.Claimed claimed = steps.claim(request.sealed());
            accountId = claimed.accountId();
            if (claimed.finished()) {
                // This same administrator already opened this account with this role: nothing is left to do.
                step = "ACTIVE";
                return new Created(accountId, null);
            }
            recorded = true;
            step = "CLAIMED";

            KeycloakUser user = steps.createInKeycloak(accountId, request.sealed());
            keycloakUserId = user.id();
            step = "KEYCLOAK_USER";

            accountId = steps.link(new Link(accountId, user.id(), user.createdHere()));
            step = "LINKED";

            steps.grantRole(accountId);
            step = "ROLE_GRANTED";

            steps.activate(accountId);
            step = "ACTIVE";
            finished = true;
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        } finally {
            if (recorded && !finished) {
                undoDetached();
            }
        }
        try {
            notices.notifyCreated(accountId);
        } catch (ActivityFailure notDelivered) {
            // The account is open; the notice is best effort.
        }
        return new Created(accountId, request.attemptId());
    }

    private void undoDetached() {
        String target = accountId;
        String linked = keycloakUserId;
        Workflow.newDetachedCancellationScope(() -> {
            try {
                undo.compensate(target);
                // The record takes the Keycloak id when linked; if that write landed but its answer was
                // lost, the record is under the Keycloak id and the first call found nothing.
                if (linked != null && !linked.equals(target)) {
                    undo.compensate(linked);
                }
            } catch (ActivityFailure cancelled) {
                // The undo retries until it lands, so reaching here means the execution was terminated
                // mid-undo; the unfinished record stays for a repeated request to finish or remove.
            }
        }).run();
    }

    @Override
    public Progress progress() {
        return new Progress(accountId, step);
    }
}
