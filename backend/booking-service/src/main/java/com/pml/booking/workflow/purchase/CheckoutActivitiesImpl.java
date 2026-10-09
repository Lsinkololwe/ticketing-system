package com.pml.booking.workflow.purchase;

import com.pml.booking.domain.ReservationStateMachine;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.PurchaseEscalation;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.repository.PaymentIntentRepository;
import com.pml.booking.service.PaymentOutcomeService;
import com.pml.booking.service.PaymentService;
import com.pml.booking.service.PurchaseEscalationService;
import com.pml.booking.service.PurchaseService;
import com.pml.booking.service.ReservationService;
import com.pml.booking.service.impl.PurchaseServiceImpl;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.booking.workflow.latepayment.LatePaymentRefundProcess;
import com.pml.booking.web.graphql.dto.TicketSelectionInput;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.PaymentView;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Progress;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.ReservationView;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.ReserveCommand;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

/**
 * The checkout activities, adapting the reservation, payment and purchase services.
 *
 * <p>Each step reuses the service that already enforces its rule — the quote and the per-buyer hold
 * in {@link ReservationService}, the single payment transition in {@link PaymentOutcomeService}, the
 * confirmation transaction in {@link PurchaseService} — so the workflow adds timing and recovery
 * without a second copy of any business rule.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.CHECKOUT)
public class CheckoutActivitiesImpl implements CheckoutActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);
    private static final Duration HOLD_AWAIT = Duration.ofSeconds(9);

    private final ReservationService reservations;
    private final PurchaseService purchases;
    private final PaymentService payments;
    private final PaymentOutcomeService outcomes;
    private final PurchaseEscalationService escalations;
    private final CatalogServiceClient catalog;
    private final PaymentIntentRepository intents;
    private final ReactiveMongoTemplate template;
    private final LatePaymentRefundProcess lateRefunds;
    private final com.pml.booking.service.BookingStore bookings;

    public CheckoutActivitiesImpl(ReservationService reservations,
                                  PurchaseService purchases,
                                  PaymentService payments,
                                  PaymentOutcomeService outcomes,
                                  PurchaseEscalationService escalations,
                                  CatalogServiceClient catalog,
                                  PaymentIntentRepository intents,
                                  ReactiveMongoTemplate template,
                                  LatePaymentRefundProcess lateRefunds,
                                  com.pml.booking.service.BookingStore bookings) {
        this.reservations = reservations;
        this.purchases = purchases;
        this.payments = payments;
        this.outcomes = outcomes;
        this.escalations = escalations;
        this.catalog = catalog;
        this.intents = intents;
        this.template = template;
        this.lateRefunds = lateRefunds;
        this.bookings = bookings;
    }

    @Override
    public ReservationView hold(String reservationId, ReserveCommand command) {
        return await(reservations.createReservation(command.userId(), input(command), reservationId)
                .flatMap(this::openBooking)
                .map(CheckoutActivitiesImpl::view), HOLD_AWAIT);
    }

    /**
     * A hold whose outcome was lost may have written its reservation; that one is released through the
     * purchase service so its document and its seats end together. Otherwise only seats can exist, and
     * each tier's release is a no-op for a reservation the catalog never held.
     */
    /**
     * The booking that carries this hold's number, contact and lifecycle. Opened here, in the same
     * retried activity as the hold, so a hold never exists without one; opening twice returns the
     * first. A catalog that cannot name the event costs the booking its title, not its existence.
     */
    private Mono<TicketReservation> openBooking(TicketReservation reservation) {
        return catalog.getEventById(reservation.getEventId())
                .map(event -> new String[]{event.getTitle(), event.getStartDate() == null ? null : event.getStartDate().toString()})
                .onErrorResume(unavailable -> Mono.empty())
                .defaultIfEmpty(new String[]{null, null})
                .flatMap(event -> bookings.open(reservation, event[0], event[1]))
                .map(booking -> {
                    reservation.setBookingNumber(booking.getBookingNumber());
                    return reservation;
                });
    }

    @Override
    public void abandonHold(String reservationId, ReserveCommand command) {
        await(reservations.findById(reservationId)
                .filter(existing -> existing.getStatus() == ReservationStatus.HELD)
                .flatMap(existing -> purchases.release(reservationId, ReservationStateMachine.Action.CANCEL,
                        "the hold did not complete").thenReturn(Boolean.TRUE))
                .switchIfEmpty(Flux.fromIterable(command.selections())
                        .concatMap(selection -> catalog.releaseInventory(selection.ticketTierId(), selection.quantity(), reservationId))
                        .then(Mono.just(Boolean.TRUE))), AWAIT);
    }

    @Override
    public ReservationView current(String reservationId) {
        return await(reservations.findById(reservationId)
                .map(reservation -> Optional.of(view(reservation)))
                .defaultIfEmpty(Optional.empty()), AWAIT).orElse(null);
    }

    @Override
    public PaymentView startPayment(String reservationId, String paymentIntentId) {
        return await(payments.findById(paymentIntentId)
                .filter(intent -> reservationId.equals(intent.getReservationId()))
                .switchIfEmpty(Mono.error(Refusals.refusal(ErrorCode.PAYMENT_INTENT_UNKNOWN,
                        "the payment does not belong to this reservation")))
                .flatMap(intent -> template.updateFirst(
                                Query.query(Criteria.where("_id").is(reservationId).and("paymentIntentId").is(null)),
                                new Update().set("paymentIntentId", paymentIntentId),
                                TicketReservation.class)
                        .thenReturn(intent))
                .flatMap(intent -> intent.getStatus() == PaymentIntent.PaymentStatus.PENDING
                        ? payments.initiatePayment(paymentIntentId)
                        : Mono.just(intent))
                .map(intent -> new PaymentView(intent.getId(), intent.getTransactionRef(), intent.getStatus())), AWAIT);
    }

    @Override
    public Progress verify(String reservationId) {
        return await(intents.findByReservationId(reservationId)
                .flatMap(intent -> intent.getDepositId() != null && !intent.isTerminal()
                        ? outcomes.verifyAndApply(intent.getDepositId())
                        : Mono.just(intent))
                .then(outcomes.resume(reservationId))
                .then(Mono.zip(
                        intents.findByReservationId(reservationId).map(intent -> Optional.of(intent.getStatus()))
                                .defaultIfEmpty(Optional.empty()),
                        reservations.findById(reservationId).map(TicketReservation::getStatus)))
                .map(state -> new Progress(state.getT1().orElse(null), state.getT2())), AWAIT);
    }

    @Override
    public ReservationView release(String reservationId, String action) {
        return await(purchases.release(reservationId, ReservationStateMachine.Action.valueOf(action), null)
                .onErrorResume(PurchaseServiceImpl.ReservationStateInvalid.class, moved -> reservations.findById(reservationId))
                .flatMap(released -> released.getPaymentIntentId() == null || released.getStatus() == ReservationStatus.HELD
                        ? Mono.just(released)
                        : payments.cancelPayment(released.getPaymentIntentId())
                                .onErrorResume(submitted -> Mono.empty())
                                .thenReturn(released))
                .map(CheckoutActivitiesImpl::view), AWAIT);
    }

    @Override
    public void escalatePending(String reservationId) {
        await(intents.findByReservationId(reservationId)
                .flatMap(intent -> escalations.raise(reservationId, intent.getEventId(), intent.getUserId(), intent.getId(),
                        PurchaseEscalation.Reason.PAYMENT_PENDING_TOO_LONG,
                        "The provider has not given a verified answer for thirty minutes. Polling continues hourly; "
                                + "the money may still arrive.",
                        intent.getAmount(), intent.getCurrency()))
                .thenReturn(Boolean.TRUE), AWAIT);
    }

    @Override
    public void startLateRefund(String reservationId) {
        await(lateRefunds.start(reservationId).thenReturn(Boolean.TRUE), AWAIT);
    }

    static ReservationView view(TicketReservation reservation) {
        return new ReservationView(reservation.getId(), reservation.getUserId(), reservation.getStatus(),
                reservation.getExpiresAt() != null ? reservation.getExpiresAt().toEpochMilli() : 0L,
                reservation.getPaymentIntentId());
    }

    private static ReserveTicketsInput input(ReserveCommand command) {
        return new ReserveTicketsInput(command.eventId(),
                command.selections().stream().map(s -> new TicketSelectionInput(s.ticketTierId(), s.quantity())).toList(),
                command.promoCode(), command.idempotencyKey(), null, null, null);
    }

    private static <T> T await(Mono<T> work, Duration budget) {
        try {
            return work.block(budget);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
