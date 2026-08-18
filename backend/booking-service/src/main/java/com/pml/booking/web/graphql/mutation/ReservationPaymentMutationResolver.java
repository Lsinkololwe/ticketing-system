package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.booking.service.PaymentService;
import com.pml.booking.web.graphql.dto.PayReservationInput;
import com.pml.booking.web.graphql.dto.PaymentInitiationResponse;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * Starts the mobile-money prompt for a held reservation.
 *
 * <h2>Asking to be charged is not the same as asserting you paid</h2>
 * ET-TKT-001 §4 forbids a client-callable {@code confirmPurchase}, and this is
 * not one. It creates a payment intent and asks the provider to prompt the
 * buyer's handset; whether the money arrives is decided by the provider and
 * reaches the platform through ET-PAY-002's callback. Nothing this mutation can
 * be told will cause a ticket to be issued.
 *
 * <p>That distinction is what the deleted {@code completeReservation} got wrong.
 * It took a reservation id and a phone number and returned tickets, which meant
 * the caller's word was the evidence of payment.
 *
 * <h2>Why the response has no tickets in it</h2>
 * Because there are none yet, and pretending otherwise is what produced the old
 * {@code PENDING_PAYMENT} rows. A mobile-money confirmation takes between eight
 * seconds and four minutes and fails about one time in six. The client polls the
 * reservation, which moves to {@code CONFIRMED} when — and only when — the money
 * has arrived and the tickets exist.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ReservationPaymentMutationResolver {

    private final TicketReservationRepository reservationRepository;
    private final PaymentService paymentService;

    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<PaymentInitiationResponse> payReservation(@InputArgument PayReservationInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> reservationRepository.findById(input.reservationId())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "RESERVATION_UNKNOWN: " + input.reservationId())))
                        .flatMap(reservation -> charge(reservation, userId, input)))
                .onErrorResume(e -> {
                    log.warn("payReservation({}) refused: {}", input.reservationId(), e.getMessage());
                    return Mono.just(PaymentInitiationResponse.error(e.getMessage()));
                });
    }

    private Mono<PaymentInitiationResponse> charge(TicketReservation reservation,
                                                   String userId,
                                                   PayReservationInput input) {
        // Ownership before anything else. Without it, knowing a reservation id
        // would be enough to send someone else's phone a payment prompt.
        if (!userId.equals(reservation.getUserId())) {
            log.warn("Buyer {} attempted to pay reservation {} belonging to another buyer",
                    userId, reservation.getId());
            return Mono.just(PaymentInitiationResponse.error("RESERVATION_UNKNOWN: " + reservation.getId()));
        }

        if (reservation.getStatus() != ReservationStatus.HELD) {
            return Mono.just(PaymentInitiationResponse.error(
                    "RESERVATION_STATE_INVALID: reservation is " + reservation.getStatus()));
        }

        if (reservation.isExpired()) {
            // Refusing up front rather than letting the charge go out and
            // discovering it at confirmation. A prompt sent now would likely be
            // answered after the seats had already gone back.
            return Mono.just(PaymentInitiationResponse.error(
                    "RESERVATION_EXPIRED: the hold lapsed at " + reservation.getExpiresAt()));
        }

        // The amount is the reservation's quote, never a number from the
        // request. A client-supplied amount is a client-chosen price.
        return paymentService.createPaymentIntent(
                        reservation.getId(),
                        reservation.getEventId(),
                        userId,
                        reservation.getTotalAmount(),
                        reservation.getCurrency(),
                        input.phoneNumber())
                .flatMap(intent -> reservationRepository.findById(reservation.getId())
                        .flatMap(fresh -> {
                            fresh.setPaymentIntentId(intent.getId());
                            return reservationRepository.save(fresh);
                        })
                        .thenReturn(intent))
                .flatMap(intent -> {
                    if (intent.getStatus() != PaymentIntent.PaymentStatus.PENDING) {
                        // The prompt is already out on this reservation — the
                        // buyer tapped twice, or retried after a timeout. Sending
                        // a second one would put two debit requests on their
                        // handset for one purchase, so report the one in flight
                        // rather than starting another.
                        log.info("Reservation {} already has an in-flight payment ({}) — not re-prompting",
                                reservation.getId(), intent.getStatus());
                        return Mono.just(intent);
                    }
                    return paymentService.initiatePayment(intent.getId());
                })
                .map(intent -> PaymentInitiationResponse.pending(
                        intent.getId(),
                        intent.getTransactionRef(),
                        intent.getStatus().name(),
                        reservation.getId()));
    }
}
