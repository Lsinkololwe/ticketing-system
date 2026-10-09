package com.pml.booking.workflow.payout;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.payout.PayoutWorkflow.Answer;
import com.pml.booking.workflow.payout.PayoutWorkflow.Decision;
import com.pml.booking.workflow.payout.PayoutWorkflow.Evidence;
import com.pml.booking.workflow.payout.PayoutWorkflow.Start;
import com.pml.booking.workflow.payout.PayoutWorkflow.Submit;
import com.pml.booking.workflow.payout.PayoutWorkflow.View;
import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The payout workflow end to end, with time skipped.
 *
 * <p>The ledger and the provider are in-memory stand-ins that count what they were asked to do, so
 * each test asserts on money movements rather than on calls: one debit, one reversal, a balance
 * back to exactly where it started. The MongoDB side of those same steps is proven against a real
 * replica set in {@code PayoutSettlementServiceTest}.
 */
@Tag("L3")
@Tag("ET-FIN-003")
@DisplayName("ET-FIN-003-R3/R6/R7 · a payout settles once, or its escrow is restored exactly")
class PayoutWorkflowTest {

    private static final String ESCROW = "escrow-1";
    private static final String REQUEST = "payout-1";
    private static final String ORGANIZER = "organizer-1";
    private static final String FINANCE = "finance-1";
    private static final String BANK = "bank-1";
    private static final BigDecimal BALANCE = new BigDecimal("500.00");

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeLedger ledger;
    private FakeProvider provider;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        ledger = new FakeLedger();
        provider = new FakeProvider();
        Worker finance = env.newWorker(TaskQueues.FINANCE);
        finance.registerWorkflowImplementationTypes(PayoutWorkflowImpl.class);
        finance.registerActivitiesImplementations(ledger);
        env.newWorker(TaskQueues.PROVIDER).registerActivitiesImplementations(provider);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("a verified mobile-money transfer debits once, completes once, and closes the execution")
    void aVerifiedTransferSettlesOnce() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        provider.answers.add(Answer.pending());
        provider.answers.add(Answer.succeeded("prov-1"));

        payout.approve(by(FINANCE));
        awaitClosed(payout);

        assertThat(ledger.status).isEqualTo(PayoutRequestStatus.COMPLETED);
        assertThat(ledger.debits).isEqualTo(1);
        assertThat(ledger.completions).isEqualTo(1);
        assertThat(ledger.reversals).isZero();
        assertThat(ledger.balance).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("R3 · the requester cannot approve, and the refusal never enters the history")
    void theRequesterCannotApprove() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);

        assertThatThrownBy(() -> payout.approve(by(ORGANIZER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED.name()));

        assertThat(ledger.approvals).isZero();
        assertThat(payout.current().status()).isEqualTo(PayoutRequestStatus.PENDING);
        assertThat(acceptedUpdates()).as("only the submission was accepted").isEqualTo(1);
    }

    @Test
    @DisplayName("ADM-006 · a held request cannot be approved; the release lets the same approval through")
    void aHeldRequestCannotBeApproved() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);

        payout.hold(new Decision(REQUEST, FINANCE, "compliance asked us to wait for the KYB review"));
        assertThatThrownBy(() -> payout.approve(by("finance-2")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.PAYOUT_STATE_INVALID.name()));
        assertThat(ledger.approvals).as("the ledger was never asked").isZero();

        payout.release(new Decision(REQUEST, FINANCE, "review finished"));
        provider.answers.add(Answer.succeeded("prov-1"));
        payout.approve(by("finance-2"));
        awaitClosed(payout);

        assertThat(ledger.holds).isEqualTo(1);
        assertThat(ledger.status).isEqualTo(PayoutRequestStatus.COMPLETED);
        assertThat(ledger.debits).isEqualTo(1);
    }

    @Test
    @DisplayName("ADM-006 · a hold needs a reason, and only a held request can be released")
    void aHoldNeedsAReason() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);

        assertThatThrownBy(() -> payout.hold(new Decision(REQUEST, FINANCE, "  ")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED.name()));
        assertThatThrownBy(() -> payout.release(new Decision(REQUEST, FINANCE, "nothing to release")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.PAYOUT_STATE_INVALID.name()));
        assertThat(ledger.holds).isZero();
    }

    @Test
    @DisplayName("ADM-006 · holding twice is one hold")
    void holdingTwiceIsOneHold() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);

        payout.hold(new Decision(REQUEST, FINANCE, "compliance review"));
        payout.hold(new Decision(REQUEST, FINANCE, "compliance review"));

        assertThat(ledger.holds).isEqualTo(1);
    }

    @Test
    @DisplayName("R1 · an approval refused as ineligible leaves the request open for a later approval")
    void anIneligibleApprovalLeavesTheRequestOpen() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        ledger.refuseApproval = ErrorCode.PAYOUT_WINDOW_NOT_OPEN;

        assertThatThrownBy(() -> payout.approve(by(FINANCE)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.PAYOUT_WINDOW_NOT_OPEN.name()));
        assertThat(payout.current().status()).isEqualTo(PayoutRequestStatus.PENDING);

        ledger.refuseApproval = null;
        provider.answers.add(Answer.succeeded("prov-1"));
        payout.approve(by(FINANCE));
        awaitClosed(payout);

        assertThat(ledger.completions).isEqualTo(1);
    }

    @Test
    @DisplayName("R6 · a verified transfer failure restores the escrow exactly and marks the request FAILED")
    void aVerifiedFailureRestoresTheEscrowExactly() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        provider.answers.add(Answer.failed("INSUFFICIENT_BALANCE", "provider float empty"));

        payout.approve(by(FINANCE));
        awaitClosed(payout);

        assertThat(ledger.status).isEqualTo(PayoutRequestStatus.FAILED);
        assertThat(ledger.debits).isEqualTo(1);
        assertThat(ledger.reversals).isEqualTo(1);
        assertThat(ledger.completions).isZero();
        assertThat(ledger.balance).isEqualByComparingTo(BALANCE);
    }

    @Test
    @DisplayName("R7 · bad account details fail at once, are not retried, and flag the account")
    void badDetailsFailFast() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        provider.initiateFailure = PayoutRules.BAD_ACCOUNT_DETAILS;

        payout.approve(by(FINANCE));
        awaitClosed(payout);

        assertThat(provider.initiations.get()).isEqualTo(1);
        assertThat(ledger.flagged).containsExactly(BANK);
        assertThat(ledger.failureCode).isEqualTo(PayoutRules.BAD_ACCOUNT_DETAILS);
        assertThat(ledger.balance).isEqualByComparingTo(BALANCE);
    }

    @Test
    @DisplayName("R7 · an unavailable provider is tried three times, then the payout fails with the escrow restored")
    void anUnavailableProviderIsTriedThreeTimes() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        provider.alwaysUnavailable = true;

        payout.approve(by(FINANCE));
        awaitClosed(payout);

        assertThat(provider.initiations.get()).isEqualTo(PayoutRules.MAX_ATTEMPTS);
        assertThat(ledger.failureCode).isEqualTo(PayoutRules.TRANSFER_UNAVAILABLE);
        assertThat(ledger.flagged).isEmpty();
        assertThat(ledger.balance).isEqualByComparingTo(BALANCE);
    }

    @Test
    @DisplayName("R6 · a worker lost after the debit committed resumes without debiting twice")
    void aLostWorkerDoesNotDebitTwice() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        ledger.loseWorkerAfterDebit = true;
        provider.answers.add(Answer.succeeded("prov-1"));

        payout.approve(by(FINANCE));
        awaitClosed(payout);

        assertThat(ledger.beginCalls).as("the begin activity ran again").isEqualTo(2);
        assertThat(ledger.debits).as("but moved money once").isEqualTo(1);
        assertThat(ledger.completions).isEqualTo(1);
    }

    @Test
    @DisplayName("R7 · finance retries a failed payout; the second attempt debits again and settles")
    void aRetrySettles() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        provider.answers.add(Answer.failed("INSUFFICIENT_BALANCE", "provider float empty"));
        payout.approve(by(FINANCE));
        until(payout, PayoutRequestStatus.FAILED);

        provider.answers.add(Answer.succeeded("prov-2"));
        View retried = payout.retry(by(FINANCE));
        awaitClosed(payout);

        assertThat(retried.status()).isNotEqualTo(PayoutRequestStatus.FAILED);
        assertThat(ledger.debits).isEqualTo(2);
        assertThat(ledger.reversals).isEqualTo(1);
        assertThat(ledger.completions).isEqualTo(1);
        assertThat(ledger.balance).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("§4 · three days without an answer escalate once and fail nothing; a later answer still settles")
    void silenceIsEscalatedNotFailed() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        payout.approve(by(FINANCE));

        env.sleep(Duration.ofDays(3).plusHours(2));

        assertThat(ledger.unconfirmed).isEqualTo(1);
        assertThat(ledger.reversals).isZero();
        assertThat(payout.current().status()).isEqualTo(PayoutRequestStatus.PROCESSING);

        provider.answers.add(Answer.succeeded("prov-late"));
        payout.providerCallback(new Evidence("pp", "COMPLETED"));
        awaitClosed(payout);

        assertThat(ledger.completions).isEqualTo(1);
        assertThat(ledger.unconfirmed).isEqualTo(1);
    }

    @Test
    @DisplayName("a manual bank transfer completes on finance's confirmation with the bank reference")
    void aManualTransferCompletesOnConfirmation() {
        PayoutWorkflow payout = open(PayoutMethod.BANK_TRANSFER);
        payout.approve(by(FINANCE));
        until(payout, PayoutRequestStatus.PROCESSING);

        View confirmed = payout.confirmTransfer(new Decision(REQUEST, FINANCE, "BANK-REF-9"));
        awaitClosed(payout);

        assertThat(confirmed.status()).isEqualTo(PayoutRequestStatus.COMPLETED);
        assertThat(ledger.reference).isEqualTo("BANK-REF-9");
        assertThat(provider.initiations.get()).as("no provider is involved in a manual transfer").isZero();
    }

    @Test
    @DisplayName("a mobile-money transfer cannot be confirmed by hand")
    void mobileMoneyIsNotConfirmedByHand() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        payout.approve(by(FINANCE));
        until(payout, PayoutRequestStatus.PROCESSING);

        assertThatThrownBy(() -> payout.confirmTransfer(new Decision(REQUEST, FINANCE, "BANK-REF-9")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.PAYOUT_STATE_INVALID.name()));
        assertThat(ledger.completions).isZero();
    }

    @Test
    @DisplayName("R8 · an organizer cancels only while PENDING")
    void cancelOnlyWhilePending() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        payout.approve(by(FINANCE));

        assertThatThrownBy(() -> payout.cancel(by(ORGANIZER)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.PAYOUT_STATE_INVALID.name()));
    }

    @Test
    @DisplayName("a cancelled request closes the execution without moving money")
    void aCancelledRequestMovesNothing() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);

        assertThat(payout.cancel(by(ORGANIZER)).status()).isEqualTo(PayoutRequestStatus.CANCELLED);
        awaitClosed(payout);

        assertThat(ledger.debits).isZero();
    }

    @Test
    @DisplayName("a rejection needs a reason")
    void aRejectionNeedsAReason() {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);

        assertThatThrownBy(() -> payout.reject(new Decision(REQUEST, FINANCE, " ")))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED.name()));
    }

    @Test
    @DisplayName("R2 · a second request while one is open for the escrow is refused by the server")
    void oneOpenRequestPerEscrow() {
        open(PayoutMethod.MOBILE_MONEY);

        assertThatThrownBy(() -> WorkflowClient.start(starter()::run, new Start(ESCROW)))
                .isInstanceOf(WorkflowExecutionAlreadyStarted.class);
    }

    // ---- harness -------------------------------------------------------------------------------

    private PayoutWorkflow starter() {
        return client.newWorkflowStub(PayoutWorkflow.class, WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.payout(ESCROW))
                .setTaskQueue(TaskQueues.FINANCE)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_FAIL)
                .build());
    }

    private PayoutWorkflow open(PayoutMethod method) {
        WorkflowClient.start(starter()::run, new Start(ESCROW));
        PayoutWorkflow payout = client.newWorkflowStub(PayoutWorkflow.class, WorkflowIds.payout(ESCROW));
        payout.submit(new Submit(REQUEST, ORGANIZER, "event-1", ESCROW, BANK, BALANCE, "ZMW", method,
                null, null, null, ORGANIZER));
        return payout;
    }

    private static Decision by(String actor) {
        return new Decision(REQUEST, actor, "reviewed");
    }

    private static void awaitClosed(PayoutWorkflow payout) {
        WorkflowStub.fromTyped(payout).getResult(Void.class);
    }

    private void until(PayoutWorkflow payout, PayoutRequestStatus status) {
        for (int step = 0; step < 400 && payout.current().status() != status; step++) {
            env.sleep(Duration.ofSeconds(30));
        }
        assertThat(payout.current().status()).isEqualTo(status);
    }

    private long acceptedUpdates() {
        WorkflowExecution execution = WorkflowExecution.newBuilder().setWorkflowId(WorkflowIds.payout(ESCROW)).build();
        return env.getWorkflowExecutionHistory(execution).getEvents().stream()
                .filter(event -> event.getEventType() == EventType.EVENT_TYPE_WORKFLOW_EXECUTION_UPDATE_ACCEPTED)
                .count();
    }

    /** The MongoDB side, in memory: a balance that moves, and counts of every movement. */
    static final class FakeLedger implements PayoutActivities {
        BigDecimal balance = BALANCE;
        PayoutRequestStatus status;
        PayoutMethod method;
        int attempts;
        String providerPayoutId;
        String failureCode;
        String reference;
        int approvals;
        int beginCalls;
        int debits;
        int reversals;
        int completions;
        int unconfirmed;
        final List<String> flagged = new ArrayList<>();
        volatile ErrorCode refuseApproval;
        volatile boolean loseWorkerAfterDebit;
        boolean onHold;
        int holds;

        private View view() {
            return new View(REQUEST, status, attempts, method, ORGANIZER, BANK);
        }

        @Override
        public synchronized View createRequest(Submit command) {
            if (status == null) {
                status = PayoutRequestStatus.PENDING;
                method = command.payoutMethod();
            }
            return view();
        }

        @Override
        public synchronized View approve(Decision decision) {
            if (refuseApproval != null) {
                throw Refusals.refusal(refuseApproval, "the escrow is not eligible");
            }
            approvals++;
            status = PayoutRequestStatus.APPROVED;
            return view();
        }

        @Override
        public synchronized View hold(Decision decision) {
            holds++;
            onHold = true;
            return view();
        }

        @Override
        public synchronized View release(Decision decision) {
            onHold = false;
            return view();
        }

        @Override
        public synchronized View reject(Decision decision) {
            status = PayoutRequestStatus.REJECTED;
            return view();
        }

        @Override
        public synchronized View cancel(Decision decision) {
            status = PayoutRequestStatus.CANCELLED;
            return view();
        }

        @Override
        public synchronized View beginSettlement(String payoutRequestId, String providerPayoutId) {
            beginCalls++;
            if (onHold) {
                throw Refusals.refusal(ErrorCode.PAYOUT_STATE_INVALID, "the payout request is on hold");
            }
            if (status == PayoutRequestStatus.PROCESSING && providerPayoutId.equals(this.providerPayoutId)) {
                return view();
            }
            status = PayoutRequestStatus.PROCESSING;
            attempts++;
            this.providerPayoutId = providerPayoutId;
            balance = balance.subtract(BALANCE);
            debits++;
            if (loseWorkerAfterDebit) {
                loseWorkerAfterDebit = false;
                throw new IllegalStateException("worker lost after the debit committed");
            }
            return view();
        }

        @Override
        public synchronized View completeSettlement(String payoutRequestId, String reference) {
            if (status != PayoutRequestStatus.COMPLETED) {
                status = PayoutRequestStatus.COMPLETED;
                this.reference = reference;
                completions++;
            }
            return view();
        }

        @Override
        public synchronized View failSettlement(String payoutRequestId, String failureCode, String reason) {
            if (status != PayoutRequestStatus.FAILED) {
                status = PayoutRequestStatus.FAILED;
                this.failureCode = failureCode;
                balance = balance.add(BALANCE);
                reversals++;
            }
            return view();
        }

        @Override
        public synchronized void markUnconfirmed(String payoutRequestId) {
            unconfirmed++;
        }

        @Override
        public synchronized void flagBankAccount(String bankAccountId, String failureCode) {
            flagged.add(bankAccountId);
        }
    }

    /** The provider, scripted: how initiation behaves, and the status answers to give in order. */
    static final class FakeProvider implements PayoutProviderActivities {
        final AtomicInteger initiations = new AtomicInteger();
        final Deque<Answer> answers = new ConcurrentLinkedDeque<>();
        volatile String initiateFailure;
        volatile boolean alwaysUnavailable;

        @Override
        public void initiate(String payoutRequestId) {
            initiations.incrementAndGet();
            if (initiateFailure != null) {
                throw ApplicationFailure.newNonRetryableFailure("the provider refused the destination", initiateFailure);
            }
            if (alwaysUnavailable) {
                throw ApplicationFailure.newFailure("the provider is unavailable", PayoutRules.TRANSFER_UNAVAILABLE);
            }
        }

        @Override
        public Answer status(String payoutRequestId) {
            Answer next = answers.poll();
            return next != null ? next : Answer.pending();
        }
    }
    @Test
    @DisplayName("ET-PLT-015 R3 · a settled payout's recorded history replays against the current implementation")
    void itsHistoryReplays() throws Exception {
        PayoutWorkflow payout = open(PayoutMethod.MOBILE_MONEY);
        provider.answers.add(Answer.succeeded("prov-1"));
        payout.approve(by(FINANCE));
        awaitClosed(payout);

        assertReplays(WorkflowIds.payout(ESCROW), PayoutWorkflowImpl.class);
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                io.temporal.api.common.v1.WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }
}
