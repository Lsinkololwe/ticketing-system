package com.pml.identity.workflow.ensure;

import com.pml.identity.account.AccountGuard;
import com.pml.identity.account.AccountStates;
import com.pml.identity.account.AccountEnsurer;
import com.pml.identity.account.EnsureCommand;
import com.pml.identity.account.EnsureResult;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.infrastructure.temporal.WorkflowIds;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.workflow.Refusals;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateHandle;
import io.temporal.client.WorkflowUpdateStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * How {@code /accounts/ensure} reaches the account process (CONTRACT 4.3, 10).
 *
 * <ol>
 *   <li><b>Known contact, ACTIVE account:</b> answered from the database. No workflow starts, so a
 *       returning buyer's sign-in costs one read and Temporal being down does not stop it.</li>
 *   <li><b>Anything else:</b> Update-with-Start on {@code account-ensure/{contactKey}} with
 *       {@code USE_EXISTING}. The call waits a short while for the account to be finished; if the
 *       wait ends first the answer is PROVISIONING with a retry hint, and the workflow carries on.
 *       Asking again with the same proof reaches the same execution.</li>
 * </ol>
 *
 * <p>Refusals - suspended, merging, a bad proof - come back as {@link TranslatedRefusal}s carrying
 * their {@link ErrorCode}.</p>
 */
@Service
public class AccountEnsureProcess implements AccountEnsurer {

    /** How long the caller waits for the workflow before being told to ask again. */
    static final Duration DEFAULT_WAIT = Duration.ofSeconds(5);

    /** What a PROVISIONING answer tells the caller to wait before asking again. */
    static final int RETRY_AFTER_SECONDS = 2;

    private final TemporalGateway temporal;
    private final ReactiveMongoTemplate template;
    private final Duration wait;

    @Autowired
    public AccountEnsureProcess(TemporalGateway temporal, ReactiveMongoTemplate template,
                                @Value("${identity.account.ensure-wait:PT5S}") Duration wait) {
        this.temporal = temporal;
        this.template = template;
        this.wait = wait == null ? DEFAULT_WAIT : wait;
    }

    @Override
    public Mono<EnsureResult> ensure(EnsureCommand command) {
        return knownActive(command)
                .switchIfEmpty(Mono.defer(() -> viaWorkflow(command)));
    }

    /** The fast path: a contact that already belongs to an ACTIVE account. */
    private Mono<EnsureResult> knownActive(EnsureCommand command) {
        return template.findOne(Query.query(Criteria.where("type").is(command.contactType())
                        .and("valueHash").is(command.contactKey())
                        .and("verifiedAt").exists(true)
                        .and("releasedAt").is(null)), Contact.class)
                .flatMap(owner -> template.findById(owner.getAccountId(), User.class))
                .flatMap(account -> {
                    var refusal = AccountGuard.refusal(account);
                    if (refusal.isPresent()) {
                        return Mono.<EnsureResult>error(new TranslatedRefusal(refusal.get(), "account " + account.getId()));
                    }
                    return AccountStates.of(account) == AccountState.ACTIVE
                            ? Mono.just(new EnsureResult(account.getId(), AccountState.ACTIVE, false, null, null))
                            : Mono.<EnsureResult>empty();
                });
    }

    private Mono<EnsureResult> viaWorkflow(EnsureCommand command) {
        return temporal.call(() -> {
                    AccountEnsureWorkflow workflow = temporal.newWorkflow(AccountEnsureWorkflow.class,
                            WorkflowIds.accountEnsure(command.contactKey()), TaskQueues.ACCOUNT,
                            WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                            ProcessSearchAttributes.builder().processKind("AccountEnsure").build());
                    WorkflowUpdateHandle<EnsureResult> handle = WorkflowClient.startUpdateWithStart(
                            workflow::ensure, command,
                            UpdateOptions.<EnsureResult>newBuilder().setWaitForStage(WorkflowUpdateStage.ACCEPTED).build(),
                            new WithStartWorkflowOperation<>(workflow::run, command));
                    try {
                        return handle.getResultAsync().get(wait.toMillis(), TimeUnit.MILLISECONDS);
                    } catch (TimeoutException stillRunning) {
                        // The workflow carries on; the caller is told when to ask again.
                        return new EnsureResult(null, AccountState.PROVISIONING, false, null, retryAfterSeconds());
                    }
                })
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.CONTACT_ALREADY_CLAIMED));
    }

    private static int retryAfterSeconds() {
        return RETRY_AFTER_SECONDS;
    }
}
