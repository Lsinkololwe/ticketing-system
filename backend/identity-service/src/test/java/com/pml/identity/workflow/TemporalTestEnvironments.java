package com.pml.identity.workflow;

import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.testing.TestEnvironmentOptions;
import io.temporal.testing.TestWorkflowEnvironment;

/**
 * An in-process {@link TestWorkflowEnvironment} that knows the platform's custom search attributes, as the
 * production namespace does. A test that starts a workflow through a {@code *Process} facade uses this;
 * a plain {@code TestWorkflowEnvironment.newInstance()} refuses the start ("search attribute BusinessId is not defined").
 */
public final class TemporalTestEnvironments {

    private TemporalTestEnvironments() {
    }

    public static TestWorkflowEnvironment newInstance() {
        return newInstance(true);
    }

    /**
     * @param timeSkipping false runs on the wall clock: a test that holds an activity open from its own
     *                     thread needs it, because skipped time would time that activity out
     */
    public static TestWorkflowEnvironment newInstance(boolean timeSkipping) {
        TestEnvironmentOptions.Builder options = TestEnvironmentOptions.newBuilder().setUseTimeskipping(timeSkipping);
        for (String name : new String[]{ProcessSearchAttributes.BUSINESS_ID_NAME, ProcessSearchAttributes.TENANT_ID_NAME,
                ProcessSearchAttributes.ORGANIZATION_ID_NAME, ProcessSearchAttributes.EVENT_ID_NAME,
                ProcessSearchAttributes.BUSINESS_STATUS_NAME, ProcessSearchAttributes.PROCESS_KIND_NAME}) {
            options.registerSearchAttribute(name, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        }
        return TestWorkflowEnvironment.newInstance(options.build());
    }
}
