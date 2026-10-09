package com.pml.identity.workflow.onboarding;

import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Approval;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Decision;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Submission;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.View;
import io.temporal.activity.ActivityInterface;

/**
 * Every MongoDB write an organization's review makes.
 *
 * <p>Each method is one transaction, safe to run twice, and writes the step marker its step owns.
 * Refusals are non-retryable failures whose type is the {@code ErrorCode} name.
 */
@ActivityInterface(namePrefix = "Onboarding")
public interface OnboardingActivities {

    View load(String organizationId);

    View submit(Submission submission);

    View reject(Decision decision);

    View requestChanges(Decision decision);

    /** Step 1. */
    View activate(Approval approval);

    /** Compensation for step 1. */
    void revertToPendingReview(String organizationId, String reason);

    /** Step 2. */
    void ensureOwnerMembership(String organizationId);

    /** Compensation for step 2. */
    void removeOwnerMembership(String organizationId);

    /** Step 3. */
    void promoteOwner(String organizationId);

    /** Compensation for step 3. */
    void restoreOwnerUserType(String organizationId);

    /** Step 6 · with {@code identity.OrganizationApproved} staged in the same transaction. */
    View markApproved(String organizationId, String keycloakGroupId);
}
