package com.pml.identity.workflow.onboarding;

import com.pml.identity.domain.enums.OnboardingStep;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusal;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The approval saga, step by step, with each compensation registered before its step.
 *
 * <pre>
 *   resubmit ─▶ PENDING_REVIEW ─approve─▶ 1 ACTIVE ─▶ 2 OWNER membership ─▶ 3 ORGANIZER ─▶
 *                     │                   4 realm role ─▶ 5 group tree + owners ─▶ 6 stage OrganizationApproved
 *                     ├─reject──────────▶ REJECTED
 *                     └─requestChanges──▶ CHANGES_REQUESTED ─resubmit─▶ PENDING_REVIEW
 * </pre>
 *
 * <p>A step that fails past its retry budget compensates the completed steps in reverse — owner out
 * of {@code owners}, role revoked, {@code ORGANIZER} removed where this approval added it, the
 * membership it created removed, status back to {@code PENDING_REVIEW} with the reason recorded —
 * and the approval refuses with that reason. The group tree stays: it is harmless and a later
 * approval reuses it. The execution closes once the organization no longer waits on a person.
 */
@WorkflowImpl(taskQueues = TaskQueues.ONBOARDING)
public class OrganizerOnboardingWorkflowImpl implements OrganizerOnboardingWorkflow {

    private final OnboardingActivities records =
            Workflow.newActivityStub(OnboardingActivities.class, OnboardingRules.recordOptions());
    private final OnboardingActivities patient =
            Workflow.newActivityStub(OnboardingActivities.class, OnboardingRules.patientOptions());
    private final OnboardingKeycloakActivities keycloak =
            Workflow.newActivityStub(OnboardingKeycloakActivities.class, OnboardingRules.keycloakOptions());
    private final OnboardingKeycloakActivities keycloakUndo =
            Workflow.newActivityStub(OnboardingKeycloakActivities.class, OnboardingRules.patientOptions());

    private String organizationId;
    private View view;
    private boolean loading;
    private boolean approving;
    private int commands;
    private String compensationReason;

    @Override
    public void run(Start start) {
        if (organizationId == null) {
            organizationId = start.organizationId();
        }
        Workflow.await(OnboardingRules.FIRST_COMMAND_WINDOW, () -> commands > 0);
        ensureLoaded();
        Workflow.await(() -> Workflow.isEveryHandlerFinished() && !OnboardingRules.awaitingPerson(view.status()));
    }

    // ---- submission ----------------------------------------------------------------------------

    @Override
    public void validateResubmit(Submission submission) {
        requireOrganization(submission.organizationId());
        if (view != null) {
            refuseIf(OnboardingRules.submissionRefusal(view.status()));
        }
    }

    @Override
    public View resubmit(Submission submission) {
        begin(submission.organizationId());
        refuseIf(OnboardingRules.submissionRefusal(view.status()));
        view = command(() -> records.submit(submission));
        return view;
    }

    // ---- decisions -----------------------------------------------------------------------------

    @Override
    public void validateReject(Decision decision) {
        validateDecision(decision);
    }

    @Override
    public View reject(Decision decision) {
        begin(decision.organizationId());
        refuseIf(OnboardingRules.decisionRefusal(view.status(), approving, decision.reason()));
        view = command(() -> records.reject(decision));
        return view;
    }

    @Override
    public void validateRequestChanges(Decision decision) {
        validateDecision(decision);
    }

    @Override
    public View requestChanges(Decision decision) {
        begin(decision.organizationId());
        refuseIf(OnboardingRules.decisionRefusal(view.status(), approving, decision.reason()));
        view = command(() -> records.requestChanges(decision));
        return view;
    }

    @Override
    public void validateApprove(Approval approval) {
        requireOrganization(approval.organizationId());
        if (approval.reviewerId() == null || approval.reviewerId().isBlank()) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "an approval names its reviewer");
        }
        if (view != null) {
            refuseIf(OnboardingRules.approvalRefusal(view.status(), approving));
        }
    }

    @Override
    public View approve(Approval approval) {
        begin(approval.organizationId());
        refuseIf(OnboardingRules.approvalRefusal(view.status(), approving));
        approving = true;
        try {
            return approveInSixSteps(approval);
        } finally {
            approving = false;
        }
    }

    private View approveInSixSteps(Approval approval) {
        Saga saga = new Saga(new Saga.Options.Builder()
                .setParallelCompensation(false)
                .setContinueWithError(true)
                .build());
        OnboardingStep step = OnboardingStep.ACTIVATE;
        try {
            saga.addCompensation(() -> patient.revertToPendingReview(organizationId, compensationReason));
            view = records.activate(approval);

            step = OnboardingStep.OWNER_MEMBERSHIP;
            saga.addCompensation(patient::removeOwnerMembership, organizationId);
            records.ensureOwnerMembership(organizationId);

            step = OnboardingStep.USER_TYPE;
            saga.addCompensation(patient::restoreOwnerUserType, organizationId);
            records.promoteOwner(organizationId);

            step = OnboardingStep.REALM_ROLE;
            saga.addCompensation(keycloakUndo::revokeOrganizerRole, organizationId);
            keycloak.grantOrganizerRole(organizationId);

            step = OnboardingStep.GROUP_TREE;
            saga.addCompensation(keycloakUndo::removeOwnerFromGroups, organizationId);
            String groupId = keycloak.ensureGroupTree(organizationId);

            step = OnboardingStep.APPROVED;
            view = records.markApproved(organizationId, groupId);
            return view;
        } catch (ActivityFailure failure) {
            if (step == OnboardingStep.ACTIVATE) {
                throw Refusals.rethrow(failure);
            }
            compensationReason = OnboardingRules.compensationReason(step, Refusals.messageOf(failure));
            saga.compensate();
            view = patient.load(organizationId);
            throw Refusals.refusal(ErrorCode.ORGANIZATION_STATE_INVALID, compensationReason);
        }
    }

    @Override
    public View step() {
        return view;
    }

    // ---- helpers -------------------------------------------------------------------------------

    private void validateDecision(Decision decision) {
        requireOrganization(decision.organizationId());
        if (view != null) {
            refuseIf(OnboardingRules.decisionRefusal(view.status(), approving, decision.reason()));
        } else if (decision.reason() == null || decision.reason().isBlank()) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a review decision needs a reason");
        }
    }

    /** Every handler's first line: counts the command and loads the organization it addresses. */
    private void begin(String commandOrganizationId) {
        commands++;
        if (organizationId == null) {
            organizationId = commandOrganizationId;
        }
        requireOrganization(commandOrganizationId);
        ensureLoaded();
    }

    private void requireOrganization(String commandOrganizationId) {
        if (commandOrganizationId == null || commandOrganizationId.isBlank()) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "a command names its organization");
        }
        if (organizationId != null && !organizationId.equals(commandOrganizationId)) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "this execution belongs to another organization");
        }
    }

    private void ensureLoaded() {
        if (view != null) {
            return;
        }
        if (loading) {
            Workflow.await(() -> view != null);
            return;
        }
        loading = true;
        try {
            view = patient.load(organizationId);
        } finally {
            loading = false;
        }
    }

    private static View command(Supplier<View> activity) {
        try {
            return activity.get();
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
    }

    private static void refuseIf(Optional<Refusal> refusal) {
        if (refusal.isPresent()) {
            throw refusal.get().failure();
        }
    }
}
