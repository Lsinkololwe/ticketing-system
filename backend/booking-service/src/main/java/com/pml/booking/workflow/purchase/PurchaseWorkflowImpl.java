package com.pml.booking.workflow.purchase;

import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.failure.ActivityFailure;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * The checkout's timers and its recovery, held in one durable execution.
 *
 * <pre>
 *   reserve ─▶ HELD ──── no payment by expiresAt ───────────────▶ release(EXPIRE)
 *                │  └─── cancel, before any payment ─────────────▶ release(CANCEL)
 *                └── pay ─▶ poll the provider (10 s … 5 min) ─▶ verified answer applied, purchase driven
 *                                 │ expiresAt + 5 min, still pending ─▶ seats released; late money is refunded in full
 *                                 │                                      by a LatePaymentRefundWorkflow, escalating only if that fails
 *                                 └ pending 30 min ─▶ escalated to transaction recovery, polling hourly
 * </pre>
 *
 * <p>The workflow never infers a payment's outcome. Every check goes through the same verification
 * the provider's callback uses, so callback, poll and recovery converge on one transition.
 */
@WorkflowImpl(taskQueues = TaskQueues.CHECKOUT)
public class PurchaseWorkflowImpl implements PurchaseWorkflow {

    private final CheckoutActivities checkout =
            Workflow.newActivityStub(CheckoutActivities.class, PurchaseRules.checkoutOptions());
    private final CheckoutActivities holds =
            Workflow.newActivityStub(CheckoutActivities.class, PurchaseRules.holdOptions());

    /**
     * Carried in the start and in the reserve command, never read back from the workflow id: an
     * execution's id is metadata of the run, and code that branches on it replays differently
     * wherever that metadata is not reproduced.
     */
    private String reservationId;
    private ReservationView reservation;
    private boolean refused;
    private boolean cancelled;
    private String paymentIntentId;
    private boolean evidence;
    private Stage stage = Stage.AWAITING_RESERVATION;

    @Override
    public void run(Start start) {
        reservationId = start.reservationId();
        if (start.adopt()) {
            reservation = checkout.current(reservationId);
            paymentIntentId = reservation != null ? reservation.paymentIntentId() : null;
        } else {
            Workflow.await(PurchaseRules.RESERVE_WINDOW, () -> reservation != null || refused);
        }

        if (reservation == null || !reservationId.equals(reservation.reservationId())
                || reservation.status() != ReservationStatus.HELD) {
            close();
            return;
        }

        stage = Stage.HELD;
        Duration untilExpiry = Duration.ofMillis(Math.max(0, reservation.expiresAtMillis() - Workflow.currentTimeMillis()));
        boolean moved = Workflow.await(untilExpiry, () -> paymentIntentId != null || cancelled);
        if (!moved) {
            reservation = checkout.release(reservationId, "EXPIRE");
        } else if (!cancelled) {
            stage = Stage.AWAITING_PAYMENT;
            awaitVerdict();
        }
        close();
    }

    private void awaitVerdict() {
        long started = Workflow.currentTimeMillis();
        long seatDeadline = reservation.expiresAtMillis() + PurchaseRules.SEAT_GRACE.toMillis();
        boolean seatsReleased = false;
        boolean escalated = false;

        for (int poll = 0; ; poll++) {
            Duration wait = PurchaseRules.pollDelay(poll, escalated);
            long toDeadline = seatDeadline - Workflow.currentTimeMillis();
            if (!seatsReleased && toDeadline > 0 && toDeadline < wait.toMillis()) {
                wait = Duration.ofMillis(toDeadline);
            }
            evidence = false;
            Workflow.await(wait, () -> evidence);

            Progress progress = checkout.verify(reservationId);
            if (progress.lateMoney() && lateRefundOwed()) {
                checkout.startLateRefund(reservationId);
            }
            if (progress.settled()) {
                stage = Stage.SETTLED;
                return;
            }

            long now = Workflow.currentTimeMillis();
            if (!seatsReleased && progress.reservationStatus() == ReservationStatus.HELD && now >= seatDeadline) {
                reservation = checkout.release(reservationId, "EXPIRE");
                seatsReleased = true;
            }
            if (!escalated && now - started >= PurchaseRules.MAX_PENDING.toMillis()) {
                checkout.escalatePending(reservationId);
                escalated = true;
            }
            if (now - started >= PurchaseRules.GIVE_UP.toMillis()) {
                return;
            }
        }
    }

    /**
     * Executions that already saw late money before the automatic refund existed replay without it:
     * their escalation to transaction recovery stands, and only a later check starts the refund.
     */
    private static boolean lateRefundOwed() {
        return Workflow.getVersion("late-payment-refund", Workflow.DEFAULT_VERSION, 1) >= 1;
    }

    private void close() {
        stage = Stage.CLOSED;
        Workflow.await(Workflow::isEveryHandlerFinished);
    }

    // ---- updates -------------------------------------------------------------------------------

    @Override
    public ReservationView reserve(ReserveCommand command) {
        if (reservation != null) {
            return reservation;
        }
        if (reservationId == null) {
            reservationId = command.reservationId();
        }
        try {
            reservation = holds.hold(reservationId, command);
            return reservation;
        } catch (ActivityFailure failure) {
            checkout.abandonHold(reservationId, command);
            refused = true;
            throw Refusals.rethrow(failure);
        }
    }

    @Override
    public void validatePay(PayCommand command) {
        requireHeldBy(command.userId(), false);
        if (cancelled) {
            throw Refusals.refusal(ErrorCode.RESERVATION_STATE_INVALID, "the reservation was cancelled");
        }
        if (Workflow.currentTimeMillis() >= reservation.expiresAtMillis()) {
            throw Refusals.refusal(ErrorCode.RESERVATION_EXPIRED, "the hold has lapsed");
        }
        if (paymentIntentId != null && !paymentIntentId.equals(command.paymentIntentId())) {
            throw Refusals.refusal(ErrorCode.RESERVATION_STATE_INVALID, "a payment is already in flight for this reservation");
        }
    }

    @Override
    public PaymentView pay(PayCommand command) {
        try {
            PaymentView payment = checkout.startPayment(reservationId, command.paymentIntentId());
            paymentIntentId = payment.paymentIntentId();
            return payment;
        } catch (ActivityFailure failure) {
            throw Refusals.rethrow(failure);
        }
    }

    @Override
    public void validateCancel(CancelCommand command) {
        requireHeldBy(command.actorId(), command.administrative());
        if (paymentIntentId != null && !command.administrative()) {
            throw Refusals.refusal(ErrorCode.RESERVATION_STATE_INVALID,
                    "PAYMENT_IN_FLIGHT: the payment has been submitted, and only the provider's answer can end it");
        }
    }

    @Override
    public ReservationView cancel(CancelCommand command) {
        reservation = checkout.release(reservationId, "CANCEL");
        cancelled = true;
        return reservation;
    }

    // ---- signals and queries -------------------------------------------------------------------

    @Override
    public void paymentCallback(Evidence evidence) {
        this.evidence = true;
    }

    @Override
    public Stage stage() {
        return stage;
    }

    private void requireHeldBy(String actorId, boolean administrative) {
        if (reservation == null || (!administrative && !reservation.userId().equals(actorId))) {
            throw Refusals.refusal(ErrorCode.RESERVATION_UNKNOWN, "no such reservation for this buyer");
        }
        if (reservation.status() != ReservationStatus.HELD) {
            throw Refusals.refusal(ErrorCode.RESERVATION_STATE_INVALID, "the reservation is " + reservation.status());
        }
    }
}
