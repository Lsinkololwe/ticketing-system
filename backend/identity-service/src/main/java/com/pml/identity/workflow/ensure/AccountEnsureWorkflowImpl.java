package com.pml.identity.workflow.ensure;

import com.pml.identity.account.EnsureCommand;
import com.pml.identity.account.EnsureResult;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.workflow.ensure.AccountEnsureActivities.Activation;
import com.pml.identity.workflow.ensure.AccountEnsureActivities.Claim;
import com.pml.identity.workflow.ensure.AccountEnsureActivities.Claimed;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.workflow.Refusals;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

/**
 * The steps of {@link AccountEnsureWorkflow}, in order, each an activity.
 *
 * <p>The execution ends when the account is ACTIVE, or when a refusal stops it. Handlers are given
 * the chance to answer before it closes.</p>
 */
@WorkflowImpl(taskQueues = TaskQueues.ACCOUNT)
public class AccountEnsureWorkflowImpl implements AccountEnsureWorkflow {

    private final AccountEnsureActivities steps =
            Workflow.newActivityStub(AccountEnsureActivities.class, AccountEnsureRules.options());

    private String startedWithProof;
    private String accountId;
    private String step = "STARTED";
    private EnsureResult result;
    private ActivityFailure refusal;
    private String refusalCode;

    @Override
    public void run(EnsureCommand command) {
        startedWithProof = command.proofId();
        // Declared from the first release: a later step is added behind a higher version, so an
        // execution already running keeps the sequence it started with.
        int version = Workflow.getVersion(AccountEnsureRules.STEPS, Workflow.DEFAULT_VERSION, 1);
        if (version >= 1) {
            try {
                finish(command);
            } catch (ActivityFailure failure) {
                refusal = failure;
                refusalCode = Refusals.typeOf(failure, ErrorCode.INTERNAL_ERROR.name());
            }
        }
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    private void finish(EnsureCommand command) {
        Claimed claimed = steps.claimContact(new Claim(command.proofId(), command.contactKey(), command.contactType()));
        accountId = claimed.accountId();
        step = "CLAIMED";
        Workflow.upsertTypedSearchAttributes(ProcessSearchAttributes.BUSINESS_ID.valueSet(accountId));

        if (claimed.state() == AccountState.ACTIVE) {
            result = new EnsureResult(accountId, AccountState.ACTIVE, false, null, null);
            step = "ACTIVE";
            return;
        }

        String keycloakUserId = steps.createKeycloakUser(accountId);
        step = "KEYCLOAK_USER";
        steps.applyAttributesAndRoles(accountId, keycloakUserId);
        step = "KEYCLOAK_ATTRIBUTES";
        AccountState state = steps.activateAccount(new Activation(accountId, keycloakUserId, command.clientId(),
                command.displayName(), command.consents()));
        step = "ACTIVE";
        steps.stageOutbox(accountId);
        result = new EnsureResult(accountId, state, claimed.created(), null, null);
    }

    @Override
    public void validateEnsure(EnsureCommand command) {
        if (command == null || isBlank(command.proofId()) || isBlank(command.contactKey())) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "an ensure names its proof and its contact key");
        }
    }

    @Override
    public EnsureResult ensure(EnsureCommand command) {
        Workflow.await(() -> result != null || refusal != null);
        if (refusal != null) {
            throw Refusals.rethrow(refusal);
        }
        // Only the call whose proof started this execution created the account.
        boolean first = result.isNew() && command.proofId().equals(startedWithProof);
        return new EnsureResult(result.accountId(), result.status(), first, null, null);
    }

    @Override
    public Progress progress() {
        return new Progress(accountId, step, refusalCode);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
