package com.pml.booking.workflow.purchase;

import com.pml.booking.domain.model.PaymentIntent.PaymentStatus;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.CancelCommand;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Evidence;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.PayCommand;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.PaymentView;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Progress;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.ReservationView;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.ReserveCommand;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Selection;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Stage;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Start;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The checkout workflow end to end, with time skipped.
 *
 * <p>The checkout activities are an in-memory stand-in that behaves like the services behind them:
 * a verified success confirms a HELD reservation, a success after the seats went back is a late
 * arrival, a failure releases. Each test asserts what happened to the seats and the money.
 */
@Tag("L3")
@Tag("ET-TKT-001")
@DisplayName("ET-TKT-001-R4/R8 · a checkout confirms once, or its seats come back and its money is escalated")
class PurchaseWorkflowTest {

    private static final String RESERVATION = "3a6f4c1e-5b7d-4e8f-9a0b-1c2d3e4f5a6b";
    private static final String BUYER = "buyer-1";
    private static final String OTHER = "buyer-2";
    private static final String INTENT = "intent-1";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private FakeCheckout checkout;

    @BeforeEach
    void startEnvironment() {
        env = TestWorkflowEnvironment.newInstance();
        checkout = new FakeCheckout(() -> env.currentTimeMillis());
        env.newWorker(TaskQueues.CHECKOUT).registerWorkflowImplementationTypes(PurchaseWorkflowImpl.class);
        env.getWorkerFactory().getWorker(TaskQueues.CHECKOUT).registerActivitiesImplementations(checkout);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void closeEnvironment() {
        env.close();
    }

    @Test
    @DisplayName("R5, R6 · a repeated reserve reaches the same execution and holds once")
    void aRepeatedReserveHoldsOnce() {
        PurchaseWorkflow purchase = started();

        ReservationView first = purchase.reserve(command());
        ReservationView second = purchase.reserve(command());

        assertThat(second).isEqualTo(first);
        assertThat(first.status()).isEqualTo(ReservationStatus.HELD);
        assertThat(checkout.holdCalls).isEqualTo(1);
    }

    @Test
    @DisplayName("a sold-out hold is refused with its code, compensated, and the execution closes")
    void aSoldOutHoldIsCompensated() {
        checkout.refuseHold = ErrorCode.TIER_SOLD_OUT;
        PurchaseWorkflow purchase = started();

        assertThatThrownBy(() -> purchase.reserve(command()))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.TIER_SOLD_OUT.name()));
        awaitClosed(purchase);

        assertThat(checkout.abandons).isEqualTo(1);
    }

    @Test
    @DisplayName("R4 · an unpaid hold is live at 9 minutes and released by its timer at 10")
    void anUnpaidHoldExpires() {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());

        env.sleep(Duration.ofMinutes(9));
        assertThat(checkout.releases).isEmpty();
        assertThat(purchase.stage()).isEqualTo(Stage.HELD);

        awaitClosed(purchase);
        assertThat(checkout.releases).containsExactly("EXPIRE");
        assertThat(checkout.status).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    @DisplayName("a verified payment confirms the reservation and returns no seats")
    void aVerifiedPaymentConfirms() {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());
        checkout.answers.add(PaymentStatus.PROCESSING);
        checkout.answers.add(PaymentStatus.SUCCEEDED);

        PaymentView payment = purchase.pay(new PayCommand(BUYER, INTENT));
        awaitClosed(purchase);

        assertThat(payment.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(checkout.status).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(checkout.releases).isEmpty();
    }

    @Test
    @DisplayName("a provider callback wakes the workflow to check at once")
    void aCallbackChecksAtOnce() {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());
        purchase.pay(new PayCommand(BUYER, INTENT));
        env.sleep(Duration.ofSeconds(80));
        int before = checkout.verifies;

        checkout.answers.add(PaymentStatus.SUCCEEDED);
        purchase.paymentCallback(new Evidence(INTENT));
        awaitClosed(purchase);

        assertThat(checkout.verifies).isEqualTo(before + 1);
        assertThat(checkout.status).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("a verified failure releases the hold")
    void aVerifiedFailureReleases() {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());
        checkout.answers.add(PaymentStatus.FAILED);

        purchase.pay(new PayCommand(BUYER, INTENT));
        awaitClosed(purchase);

        assertThat(checkout.status).isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    @DisplayName("R8 · a stalled payment returns the seats at expiry plus grace, escalates at 30 minutes, and a late success is escalated, not issued")
    void aStalledPaymentIsEscalatedNotLost() {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());
        purchase.pay(new PayCommand(BUYER, INTENT));

        env.sleep(Duration.ofMinutes(16));
        assertThat(checkout.releases).containsExactly("EXPIRE");
        assertThat(checkout.escalations).isZero();

        env.sleep(Duration.ofMinutes(20));
        assertThat(checkout.escalations).isEqualTo(1);
        assertThat(purchase.stage()).isEqualTo(Stage.AWAITING_PAYMENT);

        checkout.answers.add(PaymentStatus.SUCCEEDED);
        purchase.paymentCallback(new Evidence(INTENT));
        awaitClosed(purchase);

        assertThat(checkout.lateArrivals).isEqualTo(1);
        assertThat(checkout.status).isEqualTo(ReservationStatus.FAILED);
        assertThat(checkout.escalations).isEqualTo(1);
        assertThat(checkout.lateRefunds).as("D-22: late money is refunded automatically").isEqualTo(1);
    }

    @Test
    @DisplayName("D-22 · a success verified after the seat grace starts the automatic refund once, and its history replays")
    void lateMoneyStartsTheRefund() throws Exception {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());
        purchase.pay(new PayCommand(BUYER, INTENT));
        env.sleep(Duration.ofMinutes(16));
        assertThat(checkout.releases).containsExactly("EXPIRE");

        checkout.answers.add(PaymentStatus.SUCCEEDED);
        purchase.paymentCallback(new Evidence(INTENT));
        awaitClosed(purchase);

        assertThat(checkout.lateRefunds).isEqualTo(1);
        assertThat(checkout.status).isEqualTo(ReservationStatus.FAILED);
        assertReplays(WorkflowIds.purchase(RESERVATION), PurchaseWorkflowImpl.class);
    }

    @Test
    @DisplayName("D-22 · a payment confirmed in time starts no refund")
    void aConfirmedPaymentStartsNoRefund() {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());
        checkout.answers.add(PaymentStatus.SUCCEEDED);
        purchase.pay(new PayCommand(BUYER, INTENT));
        awaitClosed(purchase);

        assertThat(checkout.lateRefunds).isZero();
    }

    @Test
    @DisplayName("a buyer cancels before paying and the seats come back")
    void cancelBeforePaying() {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());

        ReservationView cancelled = purchase.cancel(new CancelCommand(BUYER, false));
        awaitClosed(purchase);

        assertThat(cancelled.status()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(checkout.releases).containsExactly("CANCEL");
    }

    @Test
    @DisplayName("a buyer cannot cancel once the payment is submitted")
    void noCancelWithPaymentInFlight() {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());
        purchase.pay(new PayCommand(BUYER, INTENT));

        assertThatThrownBy(() -> purchase.cancel(new CancelCommand(BUYER, false)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.RESERVATION_STATE_INVALID.name()));
        assertThat(checkout.releases).isEmpty();
    }

    @Test
    @DisplayName("another buyer can neither pay for nor cancel the hold")
    void anotherBuyerIsRefused() {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());

        assertThatThrownBy(() -> purchase.pay(new PayCommand(OTHER, INTENT)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.RESERVATION_UNKNOWN.name()));
        assertThatThrownBy(() -> purchase.cancel(new CancelCommand(OTHER, false)))
                .satisfies(error -> assertThat(Refusals.typeOf(error, null)).isEqualTo(ErrorCode.RESERVATION_UNKNOWN.name()));
        assertThat(checkout.payStarts).isZero();
    }

    @Test
    @DisplayName("R8 · a worker lost after the hold wrote its reservation holds once")
    void aLostWorkerAfterTheHold() {
        checkout.loseWorkerAfterHold = true;
        PurchaseWorkflow purchase = started();

        assertThat(purchase.reserve(command()).status()).isEqualTo(ReservationStatus.HELD);

        assertThat(checkout.holdCalls).isEqualTo(2);
        assertThat(checkout.holds).isEqualTo(1);
    }

    @Test
    @DisplayName("R8 · a worker lost after the payment was submitted submits once and still confirms")
    void aLostWorkerAfterSubmission() {
        checkout.loseWorkerAfterPaymentStart = true;
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());
        checkout.answers.add(PaymentStatus.SUCCEEDED);

        purchase.pay(new PayCommand(BUYER, INTENT));
        awaitClosed(purchase);

        assertThat(checkout.submissions).isEqualTo(1);
        assertThat(checkout.status).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("R8 · a hold that predates its workflow is adopted and expires on time")
    void anAdoptedHoldExpires() {
        checkout.adopt(BUYER, env.currentTimeMillis() + Duration.ofMinutes(3).toMillis());

        PurchaseWorkflow starter = client.newWorkflowStub(PurchaseWorkflow.class, options());
        WorkflowClient.start(starter::run, new Start(RESERVATION, true));
        awaitClosed(client.newWorkflowStub(PurchaseWorkflow.class, WorkflowIds.purchase(RESERVATION)));

        assertThat(checkout.releases).containsExactly("EXPIRE");
    }

    // ---- harness -------------------------------------------------------------------------------

    private WorkflowOptions options() {
        return WorkflowOptions.newBuilder()
                .setWorkflowId(WorkflowIds.purchase(RESERVATION))
                .setTaskQueue(TaskQueues.CHECKOUT)
                .setWorkflowIdConflictPolicy(WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING)
                .build();
    }

    private PurchaseWorkflow started() {
        PurchaseWorkflow starter = client.newWorkflowStub(PurchaseWorkflow.class, options());
        WorkflowClient.start(starter::run, new Start(RESERVATION, false));
        return client.newWorkflowStub(PurchaseWorkflow.class, WorkflowIds.purchase(RESERVATION));
    }

    private static ReserveCommand command() {
        return new ReserveCommand(RESERVATION, BUYER, "event-1", List.of(new Selection("tier-1", 2)), null, "key-1");
    }

    private static void awaitClosed(PurchaseWorkflow purchase) {
        WorkflowStub.fromTyped(purchase).getResult(Void.class);
    }

    /** The reservation, payment and purchase services, reduced to what a checkout can observe. */
    static final class FakeCheckout implements CheckoutActivities {
        private final LongSupplier now;
        ReservationStatus status;
        String userId;
        long expiresAt;
        String intentId;
        PaymentStatus intentStatus;
        int holdCalls;
        int holds;
        int abandons;
        int payStarts;
        int submissions;
        int verifies;
        int escalations;
        int lateArrivals;
        int lateRefunds;
        final List<String> releases = new ArrayList<>();
        final Deque<PaymentStatus> answers = new ConcurrentLinkedDeque<>();
        volatile ErrorCode refuseHold;
        volatile boolean loseWorkerAfterHold;
        volatile boolean loseWorkerAfterPaymentStart;

        FakeCheckout(LongSupplier now) {
            this.now = now;
        }

        synchronized void adopt(String buyer, long expiresAtMillis) {
            status = ReservationStatus.HELD;
            userId = buyer;
            expiresAt = expiresAtMillis;
        }

        private ReservationView view() {
            return new ReservationView(RESERVATION, userId, status, expiresAt, intentId);
        }

        @Override
        public synchronized ReservationView hold(String reservationId, ReserveCommand command) {
            holdCalls++;
            if (refuseHold != null) {
                throw Refusals.refusal(refuseHold, "the tier has no seats left");
            }
            if (status == null) {
                status = ReservationStatus.HELD;
                userId = command.userId();
                expiresAt = now.getAsLong() + Duration.ofMinutes(10).toMillis();
                holds++;
            }
            if (loseWorkerAfterHold) {
                loseWorkerAfterHold = false;
                throw new RuntimeException("worker lost after the reservation was written");
            }
            return view();
        }

        @Override
        public synchronized void abandonHold(String reservationId, ReserveCommand command) {
            abandons++;
        }

        @Override
        public synchronized ReservationView current(String reservationId) {
            return status == null ? null : view();
        }

        @Override
        public synchronized PaymentView startPayment(String reservationId, String paymentIntentId) {
            payStarts++;
            if (intentId == null) {
                intentId = paymentIntentId;
                intentStatus = PaymentStatus.PROCESSING;
                submissions++;
            }
            if (loseWorkerAfterPaymentStart) {
                loseWorkerAfterPaymentStart = false;
                throw new RuntimeException("worker lost after the payment was submitted");
            }
            return new PaymentView(intentId, "TXN-1", intentStatus);
        }

        @Override
        public synchronized Progress verify(String reservationId) {
            verifies++;
            PaymentStatus next = answers.poll();
            if (next != null) {
                intentStatus = next;
            }
            if (intentStatus == PaymentStatus.SUCCEEDED && status == ReservationStatus.HELD) {
                status = ReservationStatus.CONFIRMED;
            } else if (intentStatus == PaymentStatus.SUCCEEDED && status == ReservationStatus.EXPIRED) {
                lateArrivals++;
                status = ReservationStatus.FAILED;
            } else if (intentStatus == PaymentStatus.FAILED && status == ReservationStatus.HELD) {
                status = ReservationStatus.RELEASED;
            }
            return new Progress(intentStatus, status);
        }

        @Override
        public synchronized ReservationView release(String reservationId, String action) {
            if (status == ReservationStatus.HELD) {
                status = "EXPIRE".equals(action) ? ReservationStatus.EXPIRED : ReservationStatus.RELEASED;
                releases.add(action);
            }
            return view();
        }

        @Override
        public synchronized void escalatePending(String reservationId) {
            escalations++;
        }

        @Override
        public synchronized void startLateRefund(String reservationId) {
            lateRefunds++;
        }
    }
    @Test
    @DisplayName("ET-PLT-015 R3 · a confirmed checkout's recorded history replays against the current implementation")
    void itsHistoryReplays() throws Exception {
        PurchaseWorkflow purchase = started();
        purchase.reserve(command());
        checkout.answers.add(PaymentStatus.SUCCEEDED);
        purchase.pay(new PayCommand(BUYER, INTENT));
        awaitClosed(purchase);

        assertReplays(WorkflowIds.purchase(RESERVATION), PurchaseWorkflowImpl.class);
    }

    private void assertReplays(String workflowId, Class<?> implementation) throws Exception {
        String history = env.getWorkflowExecutionHistory(
                io.temporal.api.common.v1.WorkflowExecution.newBuilder().setWorkflowId(workflowId).build()).toJson(true);
        io.temporal.testing.WorkflowReplayer.replayWorkflowExecution(history, implementation);
    }
}
