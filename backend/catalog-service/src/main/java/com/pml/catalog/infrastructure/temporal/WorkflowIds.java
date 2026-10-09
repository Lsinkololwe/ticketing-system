package com.pml.catalog.infrastructure.temporal;

/**
 * Workflow ids are business ids.
 *
 * <p>One function per workflow type, so no call site builds an id by hand, and a repeated start
 * reaches the same execution.
 */
public final class WorkflowIds {

    private WorkflowIds() {
    }

    public static String eventApproval(String eventId) {
        return "event-approval/" + require(eventId);
    }

    public static String eventLifecycle(String eventId) {
        return "event/" + require(eventId);
    }

    public static String eventPublishSchedule(String eventId) {
        return "event-publish/" + require(eventId);
    }

    private static String require(String part) {
        if (part == null || part.isBlank()) {
            throw new IllegalArgumentException("a workflow id needs its business identifier");
        }
        return part;
    }
}
