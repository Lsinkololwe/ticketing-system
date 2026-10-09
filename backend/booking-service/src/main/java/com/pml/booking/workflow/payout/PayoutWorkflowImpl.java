package com.pml.booking.workflow.payout;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.util.HashSet;
import java.util.Set;

/**
 * Settlement debits early and compensates on a verified failure.
 *
 * <pre>
 *   submit ─▶ PENDING ─approve─▶ APPROVED ─▶ beginSettlement (debit + PROCESSING, one txn)
 *                    ├─reject─▶ REJECTED        │
 *                    └─cancel─▶ CANCELLED       ▼
 *                                         transfer ─▶ verified success ─▶ completeSettlement
 *                                             │
 *                                             └──────▶ verified failure ─▶ failSettlement (reverse + FAILED)
 *                                                                              │ retry, while attempts &lt; 3
 *                                                                              └──────────▶ beginSettlement
 * </pre>
 *
 * <p>Silence is never a failure: with no verified answer after three days the request is surfaced
 * as unconfirmed and polling continues. Only the provider's status API, or finance's confirmation of
 * a manual transfer, ends a transfer.
 */
@WorkflowImpl(taskQueues = TaskQueues.FINANCE)
public class PayoutWorkflowImpl implements PayoutWorkflow {

    private final PayoutActivities ledger =
            Workflow.newActivityStub(PayoutActivities.class, PayoutRules.ledgerOptions());
    private final PayoutProviderActivities transfer =
            Workflow.newActivityStub(PayoutProviderActivities.class, PayoutRules.transferOptions());
    private final PayoutProviderActivities provider =
            Workflow.newActivityStub(PayoutProviderActivities.class, PayoutRules.statusOptions());

    private View view;
    private boolean submitRefused;
    private boolean decided;
    private boolean approved;
    private boolean retryRequested;
    private boolean held;
    private String manualReference;
    private boolean evidenceArrived;
    private final Set<String> seenEvidence = new HashSet<>();

    @Override
    public void run(Start start) {
        boolean submitted = Workflow.await(PayoutRules.SUBMIT_WINDOW, () -> view != null || submitRefused);
        if (!submitted || view == null) {
            return;
        }
        Workflow.await(() -> decided);
        if (approved) {
            settle();
        }
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    private void settle() {
        while (true) {
            // A hold placed at any point before money moves stops it here, and the release lets it go on.
            Workflow.await(() -> !held);
            try {
                view = ledger.beginSettlement(view.payoutRequestId(), Workflow.randomUUID().toString());
            } catch (ActivityFailure refused) {
                // A hold placed in the instant between the last look and this call is refused by the ledger;
                // give the hold's own update time to land, then wait it out. Any other refusal stands.
                if (Workflow.await(java.time.Duration.ofSeconds(30), () -> held)) {
                    continue;
                }
                throw refused;
            }
            Answer answer = view.method() == PayoutMethod.MOBILE_MONEY ? mobileMoneyTransfer() : manualTransfer();

            if (answer.outcome() == Answer.Outcome.SUCCEEDED) {
                view = ledger.completeSettlement(view.payoutRequestId(), answer.reference());
                return;
            }

            view = ledger.failSettlement(view.payoutRequestId(), answer.failureCode(), answer.reason());
            if (PayoutRules.isBadAccount(answer.failureCode())) {
                ledger.flagBankAccount(view.bankAccountId(), answer.failureCode());
            }
            retryRequested = false;
            if (!PayoutRules.canRetry(view.attempts())
                    || !Workflow.await(PayoutRules.RETRY_WINDOW, () -> retryRequested)) {
                return;
            }
        }
    }

    private Answer mobileMoneyTransfer() {
        try {
            transfer.initiate(view.payoutRequestId());
        } catch (ActivityFailure failure) {
            return Answer.failed(Refusals.typeOf(failure, PayoutRules.TRANSFER_REFUSED), Refusals.messageOf(failure));
        }

        long started = Workflow.currentTimeMillis();
        boolean escalated = false;
        for (int poll = 0; ; poll++) {
            evidenceArrived = false;
            Workflow.await(PayoutRules.pollDelay(poll), () -> evidenceArrived);
            Answer answer = provider.status(view.payoutRequestId());
            if (answer.outcome() != Answer.Outcome.PENDING) {
                return answer;
            }
            if (!escalated && PayoutRules.unconfirmed(started, Workflow.currentTimeMillis())) {
                ledger.markUnconfirmed(view.payoutRequestId());
                escalated = true;
            }
        }
    }

    private Answer manualTransfer() {
        boolean escalated = false;
        while (manualReference == null) {
            boolean confirmed = Workflow.await(PayoutRules.UNCONFIRMED_AFTER, () -> manualReference != null);
            if (!confirmed && !escalated) {
                ledger.markUnconfirmed(view.payoutRequestId());
                escalated = true;
            }
        }
        return Answer.succeeded(manualReference);
    }

    // ---- updates -------------------------------------------------------------------------------

    @Override
    public View submit(Submit command) {
        if (view != null) {
            if (view.payoutRequestId().equals(command.payoutRequestId())) {
                return view;
            }
            throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID, "a payout request is already open for this escrow account");
        }
        try {
            view = ledger.createRequest(command);
            return view;
        } catch (ActivityFailure failure) {
            submitRefused = true;
            throw Refusals.rethrow(failure);
        }
    }

    @Override
    public void validateApprove(Decision decision) {
        requireStatus(decision, PayoutRequestStatus.PENDING);
        requireNotHeld();
        if (decision.actorId() == null || decision.actorId().equals(view.requestedById())) {
            throw Refusals.refusal(ErrorCode.ACTOR_NOT_PERMITTED, "the requester of a payout cannot approve it");
        }
    }

    @Override
    public View approve(Decision decision) {
        try {
            view = ledger.approve(decision);
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
        approved = true;
        decided = true;
        return view;
    }

    @Override
    public void validateReject(Decision decision) {
        requireStatus(decision, PayoutRequestStatus.PENDING);
        requireNote(decision, "a rejection needs a reason");
    }

    @Override
    public View reject(Decision decision) {
        view = decide(() -> ledger.reject(decision));
        return view;
    }

    @Override
    public void validateCancel(Decision decision) {
        requireStatus(decision, PayoutRequestStatus.PENDING);
    }

    @Override
    public View cancel(Decision decision) {
        view = decide(() -> ledger.cancel(decision));
        return view;
    }

    @Override
    public void validateHold(Decision decision) {
        if (view == null || !view.payoutRequestId().equals(decision.payoutRequestId())) {
            throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID, "this payout request is not the one open for its escrow account");
        }
        if (view.status() != PayoutRequestStatus.PENDING && view.status() != PayoutRequestStatus.APPROVED
                && view.status() != PayoutRequestStatus.FAILED) {
            throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID,
                    "a payout request that is " + view.status() + " cannot be held");
        }
        requireNote(decision, "a hold needs a reason");
    }

    @Override
    public View hold(Decision decision) {
        if (held) {
            return view;
        }
        View held_ = record(() -> ledger.hold(decision));
        held = true;
        return held_;
    }

    @Override
    public void validateRelease(Decision decision) {
        if (view == null || !view.payoutRequestId().equals(decision.payoutRequestId())) {
            throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID, "this payout request is not the one open for its escrow account");
        }
        if (!held) {
            throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID, "the payout request is not on hold");
        }
    }

    @Override
    public View release(Decision decision) {
        if (!held) {
            return view;
        }
        View released = record(() -> ledger.release(decision));
        held = false;
        return released;
    }

    @Override
    public void validateRetry(Decision decision) {
        requireStatus(decision, PayoutRequestStatus.FAILED);
        requireNotHeld();
        if (!PayoutRules.canRetry(view.attempts())) {
            throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID,
                    "a payout is retried at most " + PayoutRules.MAX_ATTEMPTS + " times");
        }
    }

    @Override
    public View retry(Decision decision) {
        retryRequested = true;
        Workflow.await(() -> view.status() != PayoutRequestStatus.FAILED);
        return view;
    }

    @Override
    public void validateConfirmTransfer(Decision decision) {
        requireStatus(decision, PayoutRequestStatus.PROCESSING);
        if (view.method() == PayoutMethod.MOBILE_MONEY) {
            throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID,
                    "a mobile-money transfer is confirmed by the provider, not by hand");
        }
        requireNote(decision, "a manual transfer is confirmed with the bank's reference");
    }

    @Override
    public View confirmTransfer(Decision decision) {
        manualReference = decision.note();
        Workflow.await(() -> view.status() != PayoutRequestStatus.PROCESSING);
        return view;
    }

    // ---- signals and queries -------------------------------------------------------------------

    @Override
    public void providerCallback(Evidence evidence) {
        if (seenEvidence.add(evidence.providerPayoutId() + ":" + evidence.status())) {
            evidenceArrived = true;
        }
    }

    @Override
    public View current() {
        return view;
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** A ledger call that records a fact without deciding the request: a hold or release leaves it open. */
    private View record(java.util.function.Supplier<View> activity) {
        try {
            return activity.get();
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
    }

    private View decide(java.util.function.Supplier<View> activity) {
        try {
            View decidedView = activity.get();
            decided = true;
            return decidedView;
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
    }

    private void requireStatus(Decision decision, PayoutRequestStatus expected) {
        if (view == null || !view.payoutRequestId().equals(decision.payoutRequestId())) {
            throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID, "this payout request is not the one open for its escrow account");
        }
        if (view.status() != expected) {
            throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID,
                    "the payout request is " + view.status() + ", not " + expected);
        }
    }

    private void requireNotHeld() {
        if (held) {
            throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID, "the payout request is on hold");
        }
    }

    private static void requireNote(Decision decision, String message) {
        if (decision.note() == null || decision.note().isBlank()) {
            throw Refusals.refusal(ErrorCode.COMMAND_NOT_WELL_FORMED, message);
        }
    }
}
