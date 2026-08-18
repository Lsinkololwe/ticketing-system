package com.pml.shared.constants;

/**
 * The fixed meaning behind an admin-defined status.
 *
 * <h2>Why this exists</h2>
 * Statuses are fully configurable: an administrator can add, rename, recolour,
 * reorder and retire them without a deployment. But code has to branch on
 * <em>something</em>, and it cannot branch on a string an administrator invented
 * this morning — a payout in a status no code path recognises is a payout that
 * never moves, and nobody finds out until an organizer asks where their money
 * is.
 *
 * <p>So the configurable part is the status <em>set</em>, and the fixed part is
 * this small vocabulary of meanings. An administrator adding
 * {@code AWAITING_COMPLIANCE_REVIEW} marks it {@link #PENDING}; every existing
 * branch that asks "is this pending" is immediately correct about it, and the
 * new status appears everywhere with no code change at all.
 *
 * <p>This is the same shape as Jira's status categories, and for the same
 * reason: unlimited statuses, a handful of meanings.
 *
 * <h2>Why this lives in shared-library</h2>
 * Catalog owns the reference data that defines statuses; booking-service stores
 * the resulting semantic on every payout and ticket so its own queries and
 * branches can be written against it. Two services need the same vocabulary, so
 * a copy in each would be two vocabularies that drift.
 *
 * <h2>The one rule</h2>
 * A workflow status must declare a semantic. That is the only thing an
 * administrator cannot leave blank, and it is what keeps "fully configurable"
 * from meaning "silently broken".
 */
public enum WorkflowSemantic {

    /** The state a record is created in. Exactly one per workflow type. */
    INITIAL,

    /** Waiting on someone — a reviewer, an approver, a customer. */
    PENDING,

    /** Actively being worked or transmitted. Not yet settled. */
    IN_PROGRESS,

    /** Terminal, and the outcome everyone wanted. */
    SUCCEEDED,

    /** Terminal, and it went wrong. May be retryable depending on the type. */
    FAILED,

    /** Terminal, ended deliberately by a person rather than by failure. */
    CANCELLED
}
