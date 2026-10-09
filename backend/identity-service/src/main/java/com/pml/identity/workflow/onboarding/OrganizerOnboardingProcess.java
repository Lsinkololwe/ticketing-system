package com.pml.identity.workflow.onboarding;

import com.pml.identity.domain.model.Organization;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.identity.service.OrganizationService;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Approval;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Decision;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Start;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.Submission;
import com.pml.identity.workflow.onboarding.OrganizerOnboardingWorkflow.View;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateHandle;
import io.temporal.client.WorkflowUpdateStage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * How the onboarding mutations reach an organization's workflow.
 *
 * <p>Temporal first, MongoDB second: each command is an Update-with-Start whose write
 * happens in an activity, and the answer is read back from {@code identity_organizations}, the
 * document GraphQL reads.
 */
@Service
public class OrganizerOnboardingProcess {

    private final TemporalGateway temporal;
    private final OrganizationService organizations;

    public OrganizerOnboardingProcess(TemporalGateway temporal, OrganizationService organizations) {
        this.temporal = temporal;
        this.organizations = organizations;
    }

    public Mono<Organization> submit(String organizationId, String applicantId) {
        return command(organizationId, workflow -> WorkflowClient.startUpdateWithStart(workflow::resubmit,
                new Submission(organizationId, applicantId), completed(),
                new WithStartWorkflowOperation<>(workflow::run, new Start(organizationId))));
    }

    public Mono<Organization> approve(String organizationId, String reviewerId) {
        return command(organizationId, workflow -> WorkflowClient.startUpdateWithStart(workflow::approve,
                new Approval(organizationId, reviewerId), completed(),
                new WithStartWorkflowOperation<>(workflow::run, new Start(organizationId))));
    }

    public Mono<Organization> reject(String organizationId, String reason, String reviewerId) {
        return command(organizationId, workflow -> WorkflowClient.startUpdateWithStart(workflow::reject,
                new Decision(organizationId, reviewerId, reason), completed(),
                new WithStartWorkflowOperation<>(workflow::run, new Start(organizationId))));
    }

    public Mono<Organization> requestChanges(String organizationId, String reason, String reviewerId) {
        return command(organizationId, workflow -> WorkflowClient.startUpdateWithStart(workflow::requestChanges,
                new Decision(organizationId, reviewerId, reason), completed(),
                new WithStartWorkflowOperation<>(workflow::run, new Start(organizationId))));
    }

    private Mono<Organization> command(String organizationId,
                                       Function<OrganizerOnboardingWorkflow, WorkflowUpdateHandle<View>> update) {
        return temporal.call(() -> update.apply(temporal.newWorkflow(OrganizerOnboardingWorkflow.class,
                                WorkflowIds.organizerOnboarding(organizationId), TaskQueues.ONBOARDING,
                                WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                                ProcessSearchAttributes.of("OrganizerOnboarding", organizationId).organizationId(organizationId).build()))
                        .getResult())
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.ORGANIZATION_STATE_INVALID))
                .flatMap(view -> organizations.findById(organizationId));
    }

    private static UpdateOptions<View> completed() {
        return UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build();
    }
}
