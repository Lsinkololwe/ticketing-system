package com.pml.identity.workflow.mirror;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import io.temporal.common.converter.EncodedValues;
import io.temporal.failure.ApplicationFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.DynamicWorkflow;
import io.temporal.workflow.Workflow;

/**
 * The run {@link GroupMirrorSchedule} starts: one repair activity, then done.
 *
 * <p>A dynamic workflow rather than a {@code @WorkflowInterface}, because a Schedule's run has no
 * commands, queries or state — only an activity to call. It answers exactly one workflow type and
 * refuses any other that reaches {@code identity-onboarding}, so a mistyped start fails loudly
 * instead of repairing the mirror.
 */
@WorkflowImpl(taskQueues = TaskQueues.ONBOARDING)
public class GroupMirrorRepairWorkflowImpl implements DynamicWorkflow {

    static final String UNKNOWN_TYPE = "WORKFLOW_TYPE_UNKNOWN";

    private final GroupMirrorActivities mirror =
            Workflow.newActivityStub(GroupMirrorActivities.class, GroupMirrorSchedule.repairOptions());

    @Override
    public Object execute(EncodedValues args) {
        String type = Workflow.getInfo().getWorkflowType();
        if (!GroupMirrorSchedule.WORKFLOW_TYPE.equals(type)) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "no workflow type " + type + " is served on " + TaskQueues.ONBOARDING, UNKNOWN_TYPE);
        }
        return mirror.repairPending();
    }
}
