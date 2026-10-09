package com.pml.identity.workflow.onboarding;

import com.pml.shared.constants.OrganizationStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * One organization's review, from submission to an approval that completed or
 * was reversed.
 *
 * <p>Addressed as {@code org-onboarding/{organizationId}} with conflict policy
 * {@code USE_EXISTING}. Every command is an Update-with-Start, so a submission or decision reaches
 * the open execution, or starts one for an organization that has none. A refusal a validator raises
 * never enters the history.
 */
@WorkflowInterface
public interface OrganizerOnboardingWorkflow {

    @WorkflowMethod
    void run(Start start);

    /** The applicant submits: the first submission, or a return from {@code CHANGES_REQUESTED}. */
    @UpdateMethod
    View resubmit(Submission submission);

    @UpdateValidatorMethod(updateName = "resubmit")
    void validateResubmit(Submission submission);

    /** Runs the six-step saga; returns the approved organization or refuses with why it was reversed. */
    @UpdateMethod
    View approve(Approval approval);

    @UpdateValidatorMethod(updateName = "approve")
    void validateApprove(Approval approval);

    @UpdateMethod
    View reject(Decision decision);

    @UpdateValidatorMethod(updateName = "reject")
    void validateReject(Decision decision);

    @UpdateMethod
    View requestChanges(Decision decision);

    @UpdateValidatorMethod(updateName = "requestChanges")
    void validateRequestChanges(Decision decision);

    /** Where the organization stands, including the approval step marker. */
    @QueryMethod
    View step();

    record Start(String organizationId) {
    }

    record Submission(String organizationId, String applicantId) {
    }

    record Approval(String organizationId, String reviewerId) {
    }

    record Decision(String organizationId, String reviewerId, String reason) {
    }

    /** {@code status} is null when no such organization exists. */
    record View(String organizationId,
                OrganizationStatus status,
                String ownerId,
                int sagaStep,
                String sagaFailure) {
    }
}
