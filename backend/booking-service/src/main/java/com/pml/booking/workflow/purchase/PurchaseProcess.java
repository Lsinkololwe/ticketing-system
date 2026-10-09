package com.pml.booking.workflow.purchase;

import com.pml.booking.domain.model.TicketReservation;
import com.pml.shared.infrastructure.temporal.ProcessSearchAttributes;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.service.PaymentService;
import com.pml.booking.service.ReservationService;
import com.pml.booking.web.graphql.dto.PayReservationInput;
import com.pml.booking.web.graphql.dto.PaymentInitiationResponse;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.shared.workflow.Refusals;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.CancelCommand;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Evidence;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.PayCommand;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.ReservationView;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.ReserveCommand;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Selection;
import com.pml.booking.workflow.purchase.PurchaseWorkflow.Start;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.error.ErrorCode;
import io.temporal.api.enums.v1.WorkflowIdConflictPolicy;
import io.temporal.client.UpdateOptions;
import io.temporal.client.WithStartWorkflowOperation;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowUpdateStage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;

/**
 * How the buyer's mutations and the provider's callback reach a checkout's workflow.
 *
 * <p>The phone number a payment is prompted on never enters workflow history: the
 * intent that carries it is written first, idempotently per reservation, and the workflow receives
 * only its id. An intent written for a payment the workflow then refuses stays PENDING, is never
 * submitted, and is cancelled when the hold ends.
 */
@Slf4j
@Service
public class PurchaseProcess {

    private final TemporalGateway temporal;
    private final ReservationService reservations;
    private final PaymentService payments;
    private final Clock clock;
    private final com.pml.booking.service.BookingStore bookings;

    public PurchaseProcess(TemporalGateway temporal, ReservationService reservations, PaymentService payments, Clock clock,
                           com.pml.booking.service.BookingStore bookings) {
        this.bookings = bookings;
        this.temporal = temporal;
        this.reservations = reservations;
        this.payments = payments;
        this.clock = clock;
    }

    public Mono<TicketReservation> reserve(String userId, ReserveTicketsInput input) {
        String idempotencyKey = input.idempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Mono.error(new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED: reserveTickets needs a client-supplied key"));
        }
        String contactPhone = null;
        if (input.contactPhone() != null && !input.contactPhone().isBlank()) {
            contactPhone = com.pml.shared.util.PhoneNumbers.parseMobile(input.contactPhone(), com.pml.shared.util.PhoneNumbers.DEFAULT_REGION)
                    .map(com.pml.shared.util.PhoneNumbers.Parsed::e164).orElse(null);
            if (contactPhone == null) {
                return Mono.error(new com.pml.shared.error.ValidationRefusal(java.util.List.of(
                        new com.pml.shared.error.FieldViolation("input.contactPhone", "must be a valid phone number"))));
            }
        }
        String reservationId = PurchaseRules.reservationIdFor(userId, idempotencyKey);
        ReserveCommand command = new ReserveCommand(reservationId, userId, input.eventId(),
                input.selections().stream().map(s -> new Selection(s.ticketTierId(), s.quantity())).toList(),
                input.promoCode(), idempotencyKey);

        // The order's contact goes onto the booking now, by reservation id, and not into the workflow command:
        // workflow history is plain text on the Temporal server, and a name, email and phone do not belong in it.
        return bookings.stage(reservationId, userId, input.eventId(), input.contactName(), input.contactEmail(), contactPhone)
                .then(Mono.defer(() -> temporal.call(() -> {
                            PurchaseWorkflow workflow = temporal.newWorkflow(PurchaseWorkflow.class,
                                    WorkflowIds.purchase(reservationId), TaskQueues.CHECKOUT,
                                    WorkflowIdConflictPolicy.WORKFLOW_ID_CONFLICT_POLICY_USE_EXISTING,
                                    ProcessSearchAttributes.of("Purchase", reservationId).eventId(input.eventId()).build());
                            return WorkflowClient.startUpdateWithStart(workflow::reserve, command,
                                            UpdateOptions.<ReservationView>newBuilder().setWaitForStage(WorkflowUpdateStage.COMPLETED).build(),
                                            new WithStartWorkflowOperation<>(workflow::run, new Start(reservationId, false)))
                                    .getResult();
                        })
                        // A hold that was refused leaves no reservation, so the booking staged for it is withdrawn:
                        // a sold-out attempt is not a booking anybody should see.
                        .onErrorResume(refused -> bookings.discardUnheld(reservationId).then(Mono.error(refused)))))
                .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.RESERVATION_STATE_INVALID))
                .flatMap(view -> reservations.findById(view.reservationId()));
    }

    public Mono<PaymentInitiationResponse> pay(String userId, PayReservationInput input) {
        return reservations.findById(input.reservationId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("RESERVATION_UNKNOWN: " + input.reservationId())))
                .flatMap(reservation -> {
                    // Ownership before anything else: knowing a reservation id must not be enough to
                    // send someone else's handset a payment prompt.
                    if (!userId.equals(reservation.getUserId())) {
                        return Mono.just(PaymentInitiationResponse.error("RESERVATION_UNKNOWN: " + reservation.getId()));
                    }
                    if (reservation.getStatus() != ReservationStatus.HELD) {
                        return Mono.just(PaymentInitiationResponse.error(
                                "RESERVATION_STATE_INVALID: reservation is " + reservation.getStatus()));
                    }
                    if (reservation.isExpired(clock.instant())) {
                        return Mono.just(PaymentInitiationResponse.error(
                                "RESERVATION_EXPIRED: the hold lapsed at " + reservation.getExpiresAt()));
                    }
                    // The amount is the reservation's quote, never a number from the request.
                    return payments.createPaymentIntent(reservation.getId(), reservation.getEventId(), userId,
                                    reservation.getTotalAmount(), reservation.getCurrency(), input.phoneNumber())
                            .flatMap(intent -> temporal.call(() -> temporal.existingWorkflow(PurchaseWorkflow.class,
                                                    WorkflowIds.purchase(reservation.getId()))
                                            .pay(new PayCommand(userId, intent.getId())))
                                    .onErrorMap(error -> Refusals.fromTemporal(error, ErrorCode.RESERVATION_STATE_INVALID))
                                    .map(payment -> PaymentInitiationResponse.pending(payment.paymentIntentId(),
                                            payment.transactionRef(), payment.status().name(), reservation.getId())));
                })
                .onErrorResume(error -> {
                    log.warn("payReservation({}) refused: {}", input.reservationId(), error.getMessage());
                    return Mono.just(PaymentInitiationResponse.error(error.getMessage()));
                });
    }

    /**
     * @param administrative an operator's release, which is not bound to the buyer and may end a hold
     *                       with a payment in flight; a late payment is then escalated for refund
     */
    public Mono<Boolean> cancel(String actorId, String reservationId, boolean administrative) {
        return reservations.findById(reservationId)
                .filter(reservation -> administrative || actorId.equals(reservation.getUserId()))
                .flatMap(reservation -> temporal.call(() -> temporal.existingWorkflow(PurchaseWorkflow.class,
                                        WorkflowIds.purchase(reservationId))
                                .cancel(new CancelCommand(actorId, administrative)))
                        .map(view -> view.status() == ReservationStatus.RELEASED)
                        .onErrorResume(error -> {
                            log.warn("Cancel refused for reservation {}: {}", reservationId, error.getMessage());
                            return Mono.just(false);
                        }))
                .defaultIfEmpty(false);
    }

    /** The deposit callback was applied; the purchase's workflow checks now instead of at its next poll. */
    public Mono<Void> notifyPayment(String reservationId, String paymentIntentId) {
        if (reservationId == null) {
            return Mono.empty();
        }
        return temporal.run(() -> temporal.existingWorkflow(PurchaseWorkflow.class, WorkflowIds.purchase(reservationId))
                        .paymentCallback(new Evidence(paymentIntentId)))
                .onErrorResume(error -> {
                    log.debug("No open checkout to notify for reservation {}: {}", reservationId, error.getMessage());
                    return Mono.empty();
                });
    }
}
