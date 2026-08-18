package com.pml.booking.event.listener;

import com.pml.booking.domain.ReservationStateMachine;
import com.pml.booking.event.domain.PaymentCompletedEvent;
import com.pml.booking.event.domain.PaymentFailedEvent;
import com.pml.booking.exception.ReservationExpiredException;
import com.pml.booking.service.PurchaseService;
import com.pml.booking.domain.model.PurchaseEscalation;
import com.pml.booking.service.PurchaseEscalationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Turns a payment outcome into a saga step.
 *
 * <h2>Why there is almost nothing here any more</h2>
 * This class used to hold the confirmation itself — five sequential steps
 * crediting escrow, recording commission, posting journal entries, committing
 * inventory and flipping a ticket's status, each one saved on its own. Nothing
 * bound them together, so a failure at step four left steps one to three
 * applied: escrow credited for a ticket that stayed {@code PENDING_PAYMENT},
 * against inventory that was never committed.
 *
 * <p>That work now lives in {@link PurchaseService}, inside a transaction, where
 * ET-TKT-001 R7 requires it — and, just as importantly, where the R8 recovery
 * sweep can call exactly the same code after a crash. What remains here is the
 * mapping from "the provider said yes" to "confirm the reservation", which is
 * all a listener should be.
 *
 * <h2>Why it still blocks</h2>
 * Spring Modulith marks the publication complete when this method returns, and
 * retries it if it throws. A fire-and-forget {@code subscribe()} would return
 * immediately, so the event would be marked done before the work finished and a
 * failure would never be retried.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PurchaseService purchaseService;
    private final PurchaseEscalationService escalationService;

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    /**
     * The payment succeeded, so the reservation becomes tickets.
     *
     * <p>The one case that is not simply "confirm" is a payment that landed after
     * the hold expired. The platform has the buyer's money and no longer has the
     * seats, so it escalates rather than overselling — and the reservation is
     * recorded as {@code FAILED}, not {@code EXPIRED}, because somebody was
     * charged and that needs to be visible.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Payment completed for reservation {}", event.reservationId());

        try {
            purchaseService.confirm(
                            event.reservationId(),
                            event.paymentIntentId(),
                            event.providerTransactionId())
                    .doOnNext(tickets -> log.info("Reservation {} confirmed: {} ticket(s) issued",
                            event.reservationId(), tickets.size()))
                    .onErrorResume(ReservationExpiredException.class, e -> escalateLateArrival(event).then(Mono.empty()))
                    .block(BLOCK_TIMEOUT);
        } catch (Exception e) {
            log.error("Confirmation failed for reservation {}", event.reservationId(), e);
            // Rethrow so Modulith leaves the publication incomplete and retries
            // it. Swallowing here would strand a paid-for reservation in HELD
            // with nothing scheduled to look at it again.
            throw new IllegalStateException(
                    "Confirmation failed for reservation " + event.reservationId(), e);
        }
    }

    /**
     * A hold that lapsed while the buyer was on the payment prompt.
     *
     * <p>ET-TKT-001 R7 says this capture is refunded. It is not refunded
     * automatically here, and that is a stated gap rather than an oversight:
     * every path in {@code RefundService} is keyed on a ticket, and the whole
     * point of this case is that no ticket was ever issued. A ticketless refund
     * belongs to ET-FIN-004, which owns the provider's refund API and the
     * ledger entries that go with it.
     *
     * <p>So it escalates instead. That is the difference between money an
     * operator can see in a queue and money that exists only in a log line —
     * and it is what R8 already requires for the neighbouring case.
     *
     * <p>Escalate first, then fail the reservation. In that order because if the
     * process dies between them the escalation exists and the reservation is
     * still {@code HELD}, which the recovery sweep will find; the other order
     * leaves a reservation marked resolved and a charge nobody knows about.
     */
    private Mono<Void> escalateLateArrival(PaymentCompletedEvent event) {
        log.warn("Payment for reservation {} arrived after the hold expired — {} {} needs returning",
                event.reservationId(), event.amount(), event.currency());

        return escalationService.raise(
                        event.reservationId(),
                        event.eventId(),
                        event.buyerId(),
                        event.paymentIntentId(),
                        PurchaseEscalation.Reason.PAID_AFTER_EXPIRY,
                        "Buyer was charged after the hold lapsed. No tickets were issued and the "
                                + "inventory was returned to the pool. The capture must be refunded.",
                        event.amount(),
                        event.currency())
                .then(purchaseService.release(
                        event.reservationId(),
                        ReservationStateMachine.Action.FAIL,
                        "Payment captured after the hold expired; refund owed"))
                .then();
    }

    /**
     * The payment failed, so the seats go back.
     *
     * <p>{@code RELEASE} rather than {@code EXPIRE}: ET-TKT-001 §4 keeps those
     * states apart precisely so a payments outage is distinguishable from a slow
     * checkout, and recording a declined card as "expired" erases that signal.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Payment failed for reservation {}: {}", event.reservationId(), event.failureReason());

        try {
            purchaseService.release(
                            event.reservationId(),
                            ReservationStateMachine.Action.RELEASE,
                            event.failureReason())
                    .block(BLOCK_TIMEOUT);
        } catch (Exception e) {
            log.error("Release failed for reservation {}", event.reservationId(), e);
            throw new IllegalStateException(
                    "Release failed for reservation " + event.reservationId(), e);
        }
    }
}
