package com.pml.identity.workflow.ensure;

import com.pml.identity.account.EnsureCommand;
import com.pml.identity.account.EnsureResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Finds or creates the account that owns a verified contact, and finishes it (CONTRACT 10).
 *
 * <p>Addressed as {@code account-ensure/{contactKey}} - the keyed hash, never the contact - and
 * reached by Update-with-Start with conflict policy {@code USE_EXISTING}, so two simultaneous
 * sign-ins with one contact meet one execution and so one account. The {@code ensure} update
 * answers when the account is ACTIVE; a caller that stops waiting leaves the workflow running, and
 * asking again with the same proof reaches the same execution.</p>
 *
 * <pre>
 *   claimContact -> createKeycloakUser -> applyAttributesAndRoles -> activateAccount -> stageOutbox
 * </pre>
 *
 * <p>The process only moves forward. A transient failure (Keycloak down, Mongo failing over) is
 * retried until it clears; the account stays PROVISIONING meanwhile. Only a refusal - suspended,
 * merging, a bad proof - stops it, and nothing is deleted when it does.</p>
 */
@WorkflowInterface
public interface AccountEnsureWorkflow {

    @WorkflowMethod
    void run(EnsureCommand command);

    /** Waits for the account to be finished and answers with it; a refusal reaches the caller as such. */
    @UpdateMethod
    EnsureResult ensure(EnsureCommand command);

    @UpdateValidatorMethod(updateName = "ensure")
    void validateEnsure(EnsureCommand command);

    @QueryMethod
    Progress progress();

    /** {@code step} is the last step that completed; {@code refusal} the error code that stopped the process, if any. */
    record Progress(String accountId, String step, String refusal) {
    }
}
