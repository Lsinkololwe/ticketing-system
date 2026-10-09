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
        TestEnvironmentOptions.Builder options = TestEnvironmentOptions.newBuilder();
        for (String name : new String[]{ProcessSearchAttributes.BUSINESS_ID_NAME, ProcessSearchAttributes.TENANT_ID_NAME,
                ProcessSearchAttributes.ORGANIZATION_ID_NAME, ProcessSearchAttributes.EVENT_ID_NAME,
                ProcessSearchAttributes.BUSINESS_STATUS_NAME, ProcessSearchAttributes.PROCESS_KIND_NAME}) {
            options.registerSearchAttribute(name, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        }
        return TestWorkflowEnvironment.newInstance(options.build());
    }
}
