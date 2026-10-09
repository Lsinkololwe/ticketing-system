package com.pml.catalog.workflow.approval;

import com.pml.shared.constants.EventStatus;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Decision;
import com.pml.catalog.workflow.approval.EventApprovalWorkflow.Snapshot;
import io.temporal.activity.ActivityInterface;

/**
 * Every write a review makes to {@code catalog_events}, {@code catalog_approval_timelines}
 * and {@code catalog_approval_escalations}.
 *
 * <p>Each method is one transaction, the status moves by compare-and-set, and each is safe to run
 * twice: a retried activity finds the event already where it was going and appends no second
 * timeline row.
 */
@ActivityInterface(namePrefix = "EventApproval")
public interface EventApprovalActivities {

    /** The stored review, or {@code null} when the event does not exist. */
    Snapshot current(String eventId);

    /** DRAFT or REJECTED → PENDING_APPROVAL, with the SLA deadline the workflow computed. */
    Snapshot submit(String eventId, String actorId, long slaDeadlineMillis);

    /** CHANGES_REQUESTED → PENDING_APPROVAL; the deadline carries only the SLA time still unspent. */
    Snapshot resubmit(String eventId, String actorId, long slaDeadlineMillis);

    void claim(String eventId, String reviewerId, String actorId, long expiresAtMillis);

    void releaseClaim(String eventId, String reviewerId, boolean expired);

    /** Records escalation {@code level} once; a second call for the same level writes nothing. */
    void escalate(String eventId, int level, String holderId);

    /** @param reviewMillis SLA time spent on the review, excluding time awaiting changes */
    Snapshot approve(Decision decision, long reviewMillis);

    Snapshot reject(Decision decision, long reviewMillis);

    Snapshot requestChanges(Decision decision, long reviewMillis);

    /**
     * Asks identity to tell the people a review reaching {@code reached} concerns. Identity keys each
     * message on the event, its submission number and the status, so a retry sends nothing twice.
     */
    void announce(String eventId, EventStatus reached);
}
