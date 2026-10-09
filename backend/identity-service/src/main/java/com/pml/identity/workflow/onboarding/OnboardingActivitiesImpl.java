package com.pml.identity.workflow.onboarding;

import com.pml.identity.domain.model.Organization;
import com.pml.identity.error.IdentityRefusalTranslator;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.service.OrganizationApprovalService;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Approval;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Decision;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Submission;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.View;
import com.pml.shared.constants.OrganizationStatus;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * The onboarding activities, adapting {@link OrganizationApprovalService} to Temporal.
 *
 * <p>Activity methods are synchronous by contract and run on the worker's activity executor, so the
 * reactive chain is awaited here, for less than the start-to-close timeout. An
 * identity exception with a registered refusal code is translated first, so a submission missing
 * its documents fails at once as {@code DOCUMENT_REQUIRED} instead of being retried.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.ONBOARDING)
public class OnboardingActivitiesImpl implements OnboardingActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final OrganizationApprovalService approvals;
    private final OrganizationOnboardingService onboarding;
    private final IdentityRefusalTranslator translator;

    public OnboardingActivitiesImpl(OrganizationApprovalService approvals, OrganizationOnboardingService onboarding,
                                    IdentityRefusalTranslator translator) {
        this.approvals = approvals;
        this.onboarding = onboarding;
        this.translator = translator;
    }

    @Override
    public View load(String organizationId) {
        return await(approvals.load(organizationId)
                .map(organization -> viewOf(organizationId, organization))
                .defaultIfEmpty(new View(organizationId, null, null, 0, null)));
    }

    @Override
    public View submit(Submission submission) {
        return view(submission.organizationId(), onboarding.submitForReview(submission.organizationId()));
    }

    @Override
    public View reject(Decision decision) {
        return view(decision.organizationId(), approvals.decide(decision.organizationId(), decision.reviewerId(),
                decision.reason(), OrganizationStatus.REJECTED));
    }

    @Override
    public View requestChanges(Decision decision) {
        return view(decision.organizationId(), approvals.decide(decision.organizationId(), decision.reviewerId(),
                decision.reason(), OrganizationStatus.CHANGES_REQUESTED));
    }

    @Override
    public View activate(Approval approval) {
        return view(approval.organizationId(), approvals.activate(approval.organizationId(), approval.reviewerId()));
    }

    @Override
    public void revertToPendingReview(String organizationId, String reason) {
        await(approvals.revertToPendingReview(organizationId, reason).thenReturn(Boolean.TRUE));
    }

    @Override
    public void ensureOwnerMembership(String organizationId) {
        await(approvals.ensureOwnerMembership(organizationId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void removeOwnerMembership(String organizationId) {
        await(approvals.removeOwnerMembership(organizationId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void promoteOwner(String organizationId) {
        await(approvals.promoteOwner(organizationId).thenReturn(Boolean.TRUE));
    }

    @Override
    public void restoreOwnerUserType(String organizationId) {
        await(approvals.restoreOwnerUserType(organizationId).thenReturn(Boolean.TRUE));
    }

    @Override
    public View markApproved(String organizationId, String keycloakGroupId) {
        return view(organizationId, approvals.markApproved(organizationId, keycloakGroupId));
    }

    static View viewOf(String organizationId, Organization organization) {
        return new View(organizationId, organization.getStatus(), organization.getOwnerId(),
                organization.getApprovalSagaStep() == null ? 0 : organization.getApprovalSagaStep(),
                organization.getApprovalSagaFailure());
    }

    private View view(String organizationId, Mono<Organization> write) {
        return await(write.map(organization -> viewOf(organizationId, organization)));
    }

    private <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            Throwable root = Exceptions.unwrap(error);
            throw Refusals.forActivity(translator.translate(root).<Throwable>map(refusal -> refusal).orElse(root));
        }
    }
}
