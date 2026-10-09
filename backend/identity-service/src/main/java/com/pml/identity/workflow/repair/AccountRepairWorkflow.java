package com.pml.identity.workflow.repair;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.Map;

/**
 * One pass of the account repair (ET-IDN-004 R4): drift classes D1..D9, started by the Schedule
 * {@code identity-account-repair} every PT15M with overlap policy SKIP. Returns the repairs by class and the
 * alerts by kind, counts only.
 */
@WorkflowInterface
public interface AccountRepairWorkflow {

    @WorkflowMethod
    Map<String, Long> run();
}
