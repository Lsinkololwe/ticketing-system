package com.pml.identity.workflow.repair;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.util.Map;
import java.util.TreeMap;

/** Runs the three passes in order and adds their counts. */
@WorkflowImpl(taskQueues = TaskQueues.ACCOUNT)
public class AccountRepairWorkflowImpl implements AccountRepairWorkflow {

    private final AccountRepairActivities repair = Workflow.newActivityStub(AccountRepairActivities.class, AccountRepairSchedule.options());

    @Override
    public Map<String, Long> run() {
        Map<String, Long> total = new TreeMap<>();
        // Declared from the first release: a later pass is added behind a higher version.
        Workflow.getVersion("account-repair-passes", Workflow.DEFAULT_VERSION, 1);
        repair.repairKeycloakUsers().forEach((key, value) -> total.merge(key, value, Long::sum));
        repair.repairAccounts().forEach((key, value) -> total.merge(key, value, Long::sum));
        repair.repairStale().forEach((key, value) -> total.merge(key, value, Long::sum));
        return total;
    }
}
