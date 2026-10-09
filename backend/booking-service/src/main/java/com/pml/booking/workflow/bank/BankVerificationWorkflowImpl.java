package com.pml.booking.workflow.bank;

import com.pml.booking.domain.model.BankAccount.VerificationStatus;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Async;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Deposit, confirm, lock after three misses, unlock after a day, expire after a week.
 */
@WorkflowImpl(taskQueues = TaskQueues.FINANCE)
public class BankVerificationWorkflowImpl implements BankVerificationWorkflow {

    private final BankVerificationActivities accounts =
            Workflow.newActivityStub(BankVerificationActivities.class, BankVerificationRules.accountOptions());
    private final BankVerificationActivities deposits =
            Workflow.newActivityStub(BankVerificationActivities.class, BankVerificationRules.depositOptions());

    private View view;
    private String depositId;

    @Override
    public void run(Start start) {
        boolean begun = Workflow.await(Duration.ofMinutes(5), () -> view != null);
        if (!begun) {
            return;
        }
        Async.procedure(this::watchDeposit);
        while (view.status() == VerificationStatus.VERIFYING) {
            long now = Workflow.currentTimeMillis();
            if (view.locked(now)) {
                Workflow.sleep(Duration.ofMillis(view.lockedUntilMillis() - now));
                view = accounts.unlock(start.bankAccountId());
                continue;
            }
            boolean moved = Workflow.await(BankVerificationRules.EXPIRY,
                    () -> view.status() != VerificationStatus.VERIFYING || view.locked(Workflow.currentTimeMillis()));
            if (!moved) {
                view = accounts.expire(start.bankAccountId());
            }
        }
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    /**
     * Asks the provider what became of the deposit while the owner has not confirmed it. A
     * confirmed failure reverses the booked cost and ends the verification, so the owner can start again.
     */
    private void watchDeposit() {
        long started = Workflow.currentTimeMillis();
        for (int poll = 0; view.status() == VerificationStatus.VERIFYING; poll++) {
            Workflow.sleep(BankVerificationRules.depositPollDelay(poll));
            if (view.status() != VerificationStatus.VERIFYING
                    || Workflow.currentTimeMillis() - started >= BankVerificationRules.EXPIRY.toMillis()) {
                return;
            }
            DepositOutcome outcome;
            try {
                outcome = deposits.depositOutcome(view.bankAccountId(), depositId);
            } catch (ActivityFailure unanswered) {
                continue;
            }
            if (outcome == DepositOutcome.DELIVERED) {
                return;
            }
            if (outcome == DepositOutcome.FAILED) {
                accounts.reverseDeposit(view.bankAccountId(), depositId);
                view = accounts.expire(view.bankAccountId());
                return;
            }
        }
    }

    @Override
    public View begin(Start start) {
        if (view == null) {
            View prepared = accounts.prepare(start.bankAccountId(),
                    BankVerificationRules.depositFor(Workflow.newRandom().nextInt(1_000)));
            depositId = Workflow.randomUUID().toString();
            try {
                deposits.sendDeposit(start.bankAccountId(), depositId);
            } catch (ActivityFailure failure) {
                accounts.expire(start.bankAccountId());
                throw Refusals.refusal(ErrorCode.BANK_ACCOUNT_NOT_VERIFIED,
                        "the verification deposit could not be sent: " + Refusals.messageOf(failure));
            }
            accounts.bookDeposit(start.bankAccountId(), depositId);
            view = prepared;
        }
        return view;
    }

    @Override
    public void validateConfirmAmount(Confirmation confirmation) {
        if (view == null || view.status() != VerificationStatus.VERIFYING) {
            throw Refusals.refusal(ErrorCode.BANK_ACCOUNT_NOT_VERIFIED, "no verification deposit is awaiting confirmation");
        }
        if (view.locked(Workflow.currentTimeMillis())) {
            throw Refusals.refusal(ErrorCode.BANK_ACCOUNT_NOT_VERIFIED,
                    "verification is locked after " + BankVerificationRules.MAX_ATTEMPTS + " wrong amounts; try again later");
        }
        if (confirmation.amount() == null) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "confirm the amount that arrived");
        }
    }

    @Override
    public View confirmAmount(Confirmation confirmation) {
        try {
            view = accounts.confirm(view.bankAccountId(), confirmation.actorId(), confirmation.amount());
            return view;
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
    }

    @Override
    public View current() {
        return view;
    }
}
