package com.pml.booking.service;

import com.pml.booking.domain.ReservationStateMachine;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.PurchaseEscalation;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.repository.PaymentIntentRepository;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.shared.constants.ReservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Resolves purchases the process died in the middle of (ET-TKT-001 R8).
 *
 * <h2>The saga has no state of its own</h2>
 * There is no saga collection. The whole state of a purchase is the
 * reservation's status plus its payment intent's, and this class is the proof
 * that those two are sufficient: given only that pair it can decide, for any
 * reservation, what should happen next. A separate saga document would be a
 * second thing to keep in step with the first, and the interesting failures are
 * exactly the ones where two records disagree.
 *
 * <h2>The four cases</h2>
 * A reservation still {@code HELD} is unfinished business. Which of the four it
 * is depends entirely on its intent:
 *
 * <table>
 *   <tr><th>Intent</th><th>Meaning</th><th>Action</th></tr>
 *   <tr><td>{@code SUCCEEDED}</td><td>the buyer paid and the crash ate the confirmation</td><td>confirm</td></tr>
 *   <tr><td>{@code FAILED}/{@code EXPIRED}/{@code CANCELLED}</td><td>no money moved</td><td>release</td></tr>
 *   <tr><td>none, past expiry</td><td>never charged</td><td>release</td></tr>
 *   <tr><td>{@code PENDING} past max-pending</td><td>unknown — money may still arrive</td><td><b>escalate</b></td></tr>
 * </table>
 *
 * <h2>Why the last row is not "release"</h2>
 * Releasing is the tidy-looking answer and it is the dangerous one. A
 * mobile-money confirmation genuinely can arrive half an hour late — a handset
 * that was off, a subscriber who walked away mid-prompt. Release the hold and
 * the seats are resold; the late payment then lands on a reservation whose
 * inventory belongs to somebody else, and the platform has sold one seat twice.
 * The spec is explicit that this case is escalated rather than released, and the
 * reason is that the platform genuinely does not know, and guessing costs a
 * seat.
 *
 * @see <a href="file:../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001 R8</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseRecoveryService {

    private final TicketReservationRepository reservations;
    private final PaymentIntentRepository paymentIntents;
    private final PurchaseService purchaseService;
    private final PurchaseEscalationService escalationService;

    /**
     * Beyond this, a pending intent stops being "slow" and starts being a
     * question for a human.
     */
    @Value("${booking.payment.max-pending:PT30M}")
    private Duration maxPending;

    /** What the sweep decided for one reservation. Returned so tests can assert on it. */
    public enum Outcome {
        /** The intent succeeded; tickets were issued. */
        CONFIRMED,
        /** No money moved; the inventory went back. */
        RELEASED,
        /** Unknown; handed to an operator with the hold intact. */
        ESCALATED,
        /** Still within its window. Left alone deliberately. */
        LEFT_ALONE
    }

    /**
     * Sweeps every reservation still {@code HELD} and resolves what it can.
     *
     * @return how many reached a terminal state or an operator's queue
     */
    public Mono<Long> recoverStuckPurchases() {
        return reservations.findByStatus(ReservationStatus.HELD)
                // Sequential on purpose. Each recovery may confirm a purchase —
                // writing tickets, crediting escrow, moving inventory — and
                // fanning those out would put the platform's heaviest
                // transactions in flight together during whatever incident
                // produced the backlog in the first place.
                .concatMap(reservation -> recover(reservation)
                        .onErrorResume(error -> {
                            log.error("Recovery failed for reservation {}: {}",
                                    reservation.getId(), error.getMessage());
                            return Mono.just(Outcome.LEFT_ALONE);
                        }))
                .filter(outcome -> outcome != Outcome.LEFT_ALONE)
                .count()
                .doOnSuccess(resolved -> {
                    if (resolved > 0) {
                        log.info("Recovery sweep resolved {} stuck purchase(s)", resolved);
                    }
                });
    }

    /**
     * Decides and applies the outcome for a single reservation.
     *
     * <p>Public because it is the unit worth testing: R8 asks for six tests that
     * kill the process at each saga step and assert the restart resolves
     * correctly, and each of those is one reservation in one state.
     */
    public Mono<Outcome> recover(TicketReservation reservation) {
        if (reservation.getStatus() != ReservationStatus.HELD) {
            return Mono.just(Outcome.LEFT_ALONE);
        }

        return intentFor(reservation)
                .flatMap(intent -> switch (intent.getStatus()) {
                    case SUCCEEDED -> confirm(reservation, intent);
                    case FAILED, EXPIRED, CANCELLED -> release(
                            reservation,
                            ReservationStateMachine.Action.RELEASE,
                            "Recovery: payment " + intent.getStatus());
                    case PENDING, PROCESSING -> pendingIntent(reservation, intent);
                    case REFUNDED -> release(
                            reservation,
                            ReservationStateMachine.Action.FAIL,
                            "Recovery: payment was refunded before the purchase completed");
                })
                // No intent at all. The buyer never got as far as being charged,
                // so once the hold lapses there is nothing to reconcile.
                .switchIfEmpty(Mono.defer(() -> reservation.isExpired()
                        ? release(reservation, ReservationStateMachine.Action.EXPIRE, null)
                        : Mono.just(Outcome.LEFT_ALONE)));
    }

    private Mono<PaymentIntent> intentFor(TicketReservation reservation) {
        // By reservation id rather than by the reservation's paymentIntentId,
        // because the crash this recovers from can happen between creating the
        // intent and writing its id back onto the reservation. Looking it up the
        // other way round would miss exactly the case that needs recovering.
        return paymentIntents.findByReservationId(reservation.getId());
    }

    private Mono<Outcome> confirm(TicketReservation reservation, PaymentIntent intent) {
        log.info("Recovery: reservation {} was paid ({}) but never confirmed — confirming now",
                reservation.getId(), intent.getTransactionRef());

        return purchaseService.confirm(
                        reservation.getId(), intent.getId(), intent.getProviderTransactionId())
                .thenReturn(Outcome.CONFIRMED)
                .onErrorResume(error -> {
                    // The buyer has paid and cannot be given tickets. Nothing
                    // automatic is safe from here — the hold may have lapsed and
                    // the seats gone — so it goes to a person with the money
                    // amount attached.
                    log.error("Recovery: could not confirm paid reservation {}: {}",
                            reservation.getId(), error.getMessage());
                    return escalate(reservation, intent,
                            PurchaseEscalation.Reason.CONFIRMATION_FAILED,
                            "Payment succeeded but confirmation could not be completed: "
                                    + error.getMessage());
                });
    }

    private Mono<Outcome> pendingIntent(TicketReservation reservation, PaymentIntent intent) {
        LocalDateTime cutoff = LocalDateTime.now().minus(maxPending);
        boolean overdue = reservation.getCreatedAt() != null && reservation.getCreatedAt().isBefore(cutoff);

        if (!overdue) {
            // Still inside the window where a slow subscriber is normal.
            return Mono.just(Outcome.LEFT_ALONE);
        }

        return escalate(reservation, intent,
                PurchaseEscalation.Reason.PAYMENT_PENDING_TOO_LONG,
                "Payment intent has been " + intent.getStatus() + " for longer than " + maxPending
                        + ". The hold is being kept rather than released, because the money may "
                        + "still arrive and releasing would risk an oversell.");
    }

    private Mono<Outcome> escalate(TicketReservation reservation,
                                   PaymentIntent intent,
                                   PurchaseEscalation.Reason reason,
                                   String detail) {
        return escalationService.raise(
                        reservation.getId(),
                        reservation.getEventId(),
                        reservation.getUserId(),
                        intent == null ? null : intent.getId(),
                        reason,
                        detail,
                        intent == null ? reservation.getTotalAmount() : intent.getAmount(),
                        reservation.getCurrency())
                .thenReturn(Outcome.ESCALATED);
    }

    private Mono<Outcome> release(TicketReservation reservation,
                                  ReservationStateMachine.Action action,
                                  String reason) {
        return purchaseService.release(reservation.getId(), action, reason)
                .thenReturn(Outcome.RELEASED);
    }
}
