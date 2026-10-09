package com.pml.booking.workflow.purchase;

import com.pml.booking.domain.model.PaymentIntent.PaymentStatus;
import com.pml.shared.constants.ReservationStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One checkout, from the hold to issued tickets or returned seats.
 *
 * <p>Addressed as {@code purchase/{reservationId}}, where the reservation id is derived from the buyer
 * and their idempotency key: a double tap, a reload or a retried request reaches the same execution
 * and the same hold. The workflow owns the timers — the ten-minute hold, the payment polls, the
 * thirty-minute escalation — and never decides a payment itself: the only path to a terminal intent
 * stays {@code PaymentOutcomeService}.
 */
@WorkflowInterface
public interface PurchaseWorkflow {

    @WorkflowMethod
    void run(Start start);

    @UpdateMethod
    ReservationView reserve(ReserveCommand command);

    @UpdateMethod
    PaymentView pay(PayCommand command);

    @UpdateValidatorMethod(updateName = "pay")
    void validatePay(PayCommand command);

    @UpdateMethod
    ReservationView cancel(CancelCommand command);

    @UpdateValidatorMethod(updateName = "cancel")
    void validateCancel(CancelCommand command);

    /** The provider called back about this purchase's payment; the next status check happens now. */
    @SignalMethod
    void paymentCallback(Evidence evidence);

    @QueryMethod
    Stage stage();

    /** {@code adopt} takes over a hold created before its workflow existed. */
    record Start(String reservationId, boolean adopt) {
    }

    record Selection(String ticketTierId, int quantity) {
    }

    record ReserveCommand(String reservationId, String userId, String eventId, List<Selection> selections, String promoCode,
                          String idempotencyKey) {
    }

    record PayCommand(String userId, String paymentIntentId) {
    }

    record CancelCommand(String actorId, boolean administrative) {
    }

    record Evidence(String paymentIntentId) {
    }

    record ReservationView(String reservationId, String userId, ReservationStatus status, long expiresAtMillis,
                           String paymentIntentId) {
    }

    record PaymentView(String paymentIntentId, String transactionRef, PaymentStatus status) {
    }

    /** Where the payment and the reservation stand after a status check. */
    record Progress(PaymentStatus intentStatus, ReservationStatus reservationStatus) {

        private static final Set<PaymentStatus> OPEN_INTENT = EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING);

        /**
         * The provider verified a payment, and the reservation it paid for is over without tickets (ROADMAP D-22):
         * the money is late and owed back in full.
         */
        public boolean lateMoney() {
            return intentStatus == PaymentStatus.SUCCEEDED && reservationStatus != null
                    && reservationStatus != ReservationStatus.HELD && reservationStatus != ReservationStatus.CONFIRMED;
        }

        /**
         * Nothing is left to wait for: the tickets exist, or the reservation is over and no payment
         * is still able to arrive for it.
         */
        public boolean settled() {
            if (reservationStatus == ReservationStatus.CONFIRMED) {
                return true;
            }
            boolean reservationOver = reservationStatus != null && reservationStatus != ReservationStatus.HELD;
            boolean moneyStillPossible = intentStatus != null && OPEN_INTENT.contains(intentStatus);
            return reservationOver && !moneyStillPossible;
        }
    }

    enum Stage { AWAITING_RESERVATION, HELD, AWAITING_PAYMENT, SETTLED, CLOSED }
}
