package com.pml.booking.workflow.bank;

import com.pml.booking.domain.model.BankAccount.VerificationStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.math.BigDecimal;

/**
 * A bank account is verified by a micro-deposit its owner confirms.
 *
 * <p>Addressed as {@code bank-verification/{bankAccountId}} with {@code USE_EXISTING}: asking twice
 * reaches the verification already under way, and sends no second deposit.
 */
@WorkflowInterface
public interface BankVerificationWorkflow {

    @WorkflowMethod
    void run(Start start);

    /** Sends the deposit on the first call; answers the current state on every later one. */
    @UpdateMethod
    View begin(Start start);

    @UpdateMethod
    View confirmAmount(Confirmation confirmation);

    @UpdateValidatorMethod(updateName = "confirmAmount")
    void validateConfirmAmount(Confirmation confirmation);

    @QueryMethod
    View current();

    /** What the provider says became of a sent deposit. */
    enum DepositOutcome { DELIVERED, FAILED, PENDING }

    record Start(String bankAccountId) {
    }

    record Confirmation(String actorId, BigDecimal amount) {
    }

    record View(String bankAccountId, VerificationStatus status, int attempts, long lockedUntilMillis) {

        public boolean locked(long nowMillis) {
            return lockedUntilMillis > nowMillis;
        }
    }
}
