package com.pml.shared.infrastructure.temporal;

import io.temporal.common.SearchAttributeKey;
import io.temporal.common.SearchAttributes;

/**
 * The custom search attributes every process is tagged with so operators can find executions by
 * business id, tenant, event or status in the Temporal UI and API.
 *
 * <p>The names are the single source in {@code docker-resources/temporal/self-hosted/search-attributes.conf}; a lint test
 * ({@code ProcessSearchAttributesTest}) fails when they drift. All are {@code Keyword}.
 *
 * <h2>Where they are set</h2>
 * At workflow start, by each {@code *Process} facade, through
 * {@link TemporalGateway#newWorkflow(Class, String, String, io.temporal.api.enums.v1.WorkflowIdConflictPolicy, ProcessSearchAttributes)}.
 * Workflow code does not set them, so no workflow history gains a command and replay is unaffected.
 * {@code BusinessStatus} has a key here but nothing sets it yet: it would need
 * {@code Workflow.upsertTypedSearchAttributes}, a command, so each call site must sit behind
 * {@code Workflow.getVersion}.
 *
 * <h2>Production safety</h2>
 * The server rejects a start that names an attribute not registered on the namespace (the start
 * fails with INVALID_ARGUMENT). The attributes are registered by the docker-resources/temporal init job, which
 * must therefore run before a build that sets them is deployed. Query with, for example,
 * {@code temporal workflow list --query 'BusinessId="<id>"'}.
 */
public final class ProcessSearchAttributes {

    public static final String BUSINESS_ID_NAME = "BusinessId";
    public static final String TENANT_ID_NAME = "TenantId";
    public static final String ORGANIZATION_ID_NAME = "OrganizationId";
    public static final String EVENT_ID_NAME = "EventId";
    public static final String BUSINESS_STATUS_NAME = "BusinessStatus";
    public static final String PROCESS_KIND_NAME = "ProcessKind";

    public static final SearchAttributeKey<String> BUSINESS_ID = SearchAttributeKey.forKeyword(BUSINESS_ID_NAME);
    public static final SearchAttributeKey<String> TENANT_ID = SearchAttributeKey.forKeyword(TENANT_ID_NAME);
    public static final SearchAttributeKey<String> ORGANIZATION_ID = SearchAttributeKey.forKeyword(ORGANIZATION_ID_NAME);
    public static final SearchAttributeKey<String> EVENT_ID = SearchAttributeKey.forKeyword(EVENT_ID_NAME);
    public static final SearchAttributeKey<String> BUSINESS_STATUS = SearchAttributeKey.forKeyword(BUSINESS_STATUS_NAME);
    public static final SearchAttributeKey<String> PROCESS_KIND = SearchAttributeKey.forKeyword(PROCESS_KIND_NAME);

    private final SearchAttributes attributes;

    private ProcessSearchAttributes(SearchAttributes attributes) {
        this.attributes = attributes;
    }

    /** Starts a builder; {@code processKind} is the workflow type's short name, e.g. {@code Purchase}. */
    public static Builder of(String processKind, String businessId) {
        return new Builder().processKind(processKind).businessId(businessId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public SearchAttributes toSearchAttributes() {
        return attributes;
    }

    public static final class Builder {
        private final SearchAttributes.Builder delegate = SearchAttributes.newBuilder();

        public Builder businessId(String value) {
            return set(BUSINESS_ID, value);
        }

        public Builder tenantId(String value) {
            return set(TENANT_ID, value);
        }

        public Builder organizationId(String value) {
            return set(ORGANIZATION_ID, value);
        }

        public Builder eventId(String value) {
            return set(EVENT_ID, value);
        }

        public Builder businessStatus(String value) {
            return set(BUSINESS_STATUS, value);
        }

        public Builder processKind(String value) {
            return set(PROCESS_KIND, value);
        }

        /** Null and blank values are skipped: a Keyword attribute is either meaningful or absent. */
        private Builder set(SearchAttributeKey<String> key, String value) {
            if (value != null && !value.isBlank()) {
                delegate.set(key, value);
            }
            return this;
        }

        public ProcessSearchAttributes build() {
            return new ProcessSearchAttributes(delegate.build());
        }
    }
}
