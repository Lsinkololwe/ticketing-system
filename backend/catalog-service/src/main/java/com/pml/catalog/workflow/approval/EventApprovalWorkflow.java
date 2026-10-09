package com.pml.catalog.workflow.approval;

import com.pml.shared.constants.EventStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * One review of one event, from submission to a decision.
 *
 * <p>Addressed as {@code event-approval/{eventId}} with conflict policy {@code USE_EXISTING}. The
 * submission is an Update-with-Start; claims and decisions are updates on the open execution. The
 * execution owns the claim lease and the SLA clock; {@code catalog_events} and
 * {@code catalog_approval_timelines} hold the status and the record of every change.
 *
 * <p>The execution ends at a decision. An approved event waits in APPROVED for its organizer, and
 * publishing it opens {@code event/{eventId}} — approval and publication are different decisions
 * made by different people.
 */
@WorkflowInterface
public interface EventApprovalWorkflow {

    @WorkflowMethod
    void run(Start start);

    /** The organizer's submission; a submission while changes are requested is a resubmission. */
    @UpdateMethod
    View submit(Submission submission);

    @UpdateValidatorMethod(updateName = "submit")
    void validateSubmit(Submission submission);

    @UpdateMethod
    View resubmit(Submission submission);

    @UpdateValidatorMethod(updateName = "resubmit")
    void validateResubmit(Submission submission);

    @UpdateMethod
    View claim(Claim claim);

    @UpdateValidatorMethod(updateName = "claim")
    void validateClaim(Claim claim);

    @UpdateMethod
    View release(Claim claim);

    @UpdateValidatorMethod(updateName = "release")
    void validateRelease(Claim claim);

    @UpdateMethod
    View approve(Decision decision);

    @UpdateValidatorMethod(updateName = "approve")
    void validateApprove(Decision decision);

    @UpdateMethod
    View reject(Decision decision);

    @UpdateValidatorMethod(updateName = "reject")
    void validateReject(Decision decision);

    @UpdateMethod
    View requestChanges(Decision decision);

    @UpdateValidatorMethod(updateName = "requestChanges")
    void validateRequestChanges(Decision decision);

    @QueryMethod
    View current();

    /** {@code adopt} takes over a review whose submission preceded its workflow. */
    record Start(String eventId, boolean adopt) {
    }

    record Submission(String eventId, String actorId) {
    }

    /** {@code reviewerId} is who holds the claim; {@code actorId} is who asked, which may be a supervisor. */
    record Claim(String eventId, String reviewerId, String actorId) {
    }

    record Decision(String eventId, String reviewerId, String reason) {
    }

    record View(String eventId, EventStatus status, String claimHolderId, long claimExpiresAtMillis,
                int escalationLevel) {
    }

    /** The stored review, as an activity reads it back. */
    record Snapshot(String eventId, EventStatus status, long submittedAtMillis, long changesRequestedAtMillis,
                    int escalationLevel) {
    }
}
