package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.util.List;

/**
 * One page per run: read it, hand its users on, continue as new from the next offset.
 *
 * <p>A crash mid-page repeats at most that page, and the sync workflows drop the repeats by
 * originating id. History stays one page long however many users Keycloak holds.
 */
@WorkflowImpl(taskQueues = TaskQueues.ONBOARDING)
public class UserBackfillWorkflowImpl implements UserBackfillWorkflow {

    private final UserBackfillActivities keycloakUsers =
            Workflow.newActivityStub(UserBackfillActivities.class, UserBackfillRules.pageOptions());
    private final UserBackfillActivities handOff =
            Workflow.newActivityStub(UserBackfillActivities.class, UserBackfillRules.enqueueOptions());

    private String backfillId;
    private int offset;
    private int enqueued;
    private boolean finished;

    /**
     * A Schedule starts every run with the same arguments, so a scheduled backfill arrives with no id
     * and takes one from the workflow clock — recorded in history, so replay reads the same value.
     */
    @Override
    public void run(Start start) {
        backfillId = start.backfillId() == null || start.backfillId().isBlank()
                ? Long.toString(Workflow.currentTimeMillis())
                : start.backfillId();
        offset = start.offset();
        enqueued = start.enqueued();

        List<String> page = keycloakUsers.page(offset, UserBackfillRules.PAGE_SIZE);
        if (!page.isEmpty()) {
            handOff.enqueue(backfillId, page);
            enqueued += page.size();
        }
        if (UserBackfillRules.lastPage(page.size())) {
            finished = true;
            return;
        }
        Workflow.continueAsNew(new Start(backfillId, offset + page.size(), enqueued));
    }

    @Override
    public Progress progress() {
        return new Progress(backfillId, offset, enqueued, finished);
    }
}
