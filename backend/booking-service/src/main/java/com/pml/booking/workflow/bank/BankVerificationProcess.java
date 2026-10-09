package com.pml.booking.workflow.bank;

import com.pml.booking.domain.model.BankAccount;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.service.BankVerificationService;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.bank.BankVerificationWorkflow.Confirmation;
import com.pml.booking.workflow.bank.BankVerificationWorkflow.Start;
import com.pml.booking.workflow.bank.BankVerificationWorkflow.View;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateStage;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * How GraphQL reaches a bank account's verification workflow.
 */
@Service
public class BankVerificationProcess {

    private final TemporalGateway temporal;
    private final BankVerificationService verification;

    public BankVerificationProcess(TemporalGateway temporal, BankVerificationService verification) {
        this.temporal = temporal;
        this.verification = verification;
    }

    /**
     * Starts verification, or answers the verification already under way.
     *
     * @param ownerId the caller when they must own the account; null when a platform role acts
     */
    public Mono<BankAccount> start(String bankAccountId, String ownerId) {
        return owned(bankAccountId, ownerId)
                .flatMap(account -> temporal.call(() -> {
                            BankVerificationWorkflow workflow = temporal.newWorkflow(BankVerificationWorkflow.class,
                                    WorkflowIds.bankVerification(bankAccountId), TaskQueues.FINANCE,
                                    WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                ProcessSearchAttributes.of("BankVerification", bankAccountId).build());
                            Start start = new Start(bankAccountId);
                            return WorkflowClient.startUpdateWithStart(workflow::begin, start,
                                            UpdateOptions.<View>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                                            new WithStartWorkflowOperation<>(workflow::run, start))
                                    .getResult();
                        })
                        .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.BANK_ACCOUNT_NOT_VERIFIED)))
                .flatMap(view -> verification.load(bankAccountId));
    }

    public Mono<BankAccount> confirm(String bankAccountId, String ownerId, BigDecimal amount) {
        return owned(bankAccountId, ownerId)
                .flatMap(account -> temporal.call(() -> temporal.existingWorkflow(BankVerificationWorkflow.class,
                                        WorkflowIds.bankVerification(bankAccountId))
                                .confirmAmount(new Confirmation(ownerId, amount)))
                        .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.BANK_ACCOUNT_NOT_VERIFIED)))
                .flatMap(view -> verification.load(bankAccountId));
    }

    private Mono<BankAccount> owned(String bankAccountId, String ownerId) {
        return verification.load(bankAccountId)
                .filter(account -> ownerId == null || ownerId.equals(account.getOrganizerId()))
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.BANK_ACCOUNT_UNKNOWN, "no bank account " + bankAccountId)));
    }
}
