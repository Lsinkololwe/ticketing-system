package com.pml.catalog.infrastructure.temporal;

/**
 * The task queues catalog's worker polls.
 *
 * <p>Every value here is also an entry under {@code spring.temporal.workers} in this service's
 * {@code application.yml}; {@code TemporalRegistryLintTest} holds the two together.
 * Annotations name these constants, never a literal.
 */
public final class TaskQueues {

    /** Event approval and lifecycle workflows and their activities. */
    public static final String LIFECYCLE = "catalog-lifecycle";

    private TaskQueues() {
    }
}
