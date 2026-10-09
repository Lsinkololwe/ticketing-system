package com.pml.booking.service.impl;

import com.pml.booking.domain.ReservationStateMachine;
import com.pml.booking.domain.ReservationTransitions;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.exception.ReservationExpiredException;
import com.pml.booking.exception.ReservationNotFoundException;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.CommissionService;
import com.pml.booking.service.EscrowService;
import com.pml.booking.service.PurchaseService;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.event.EventEnvelopes;
import com.pml.shared.event.EventType;
import com.pml.shared.event.Outbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Purchase confirmation: tickets, escrow, commission and status commit together, or none do.
 *
 * <p>Nothing exists until the money does. The reservation holds the inventory; tickets are written
 * inside the same transaction that credits the escrow, records the commission, moves the reservation
 * to {@code CONFIRMED} and stages one {@code booking.TicketPurchased} per ticket in the outbox. If any
 * part of that fails, none of it happened.
 *
 * <h2>Why the catalog commit runs before the transaction</h2>
 * The catalog is another service. A network call inside a MongoDB transaction holds locks for a round
 * trip and is not rolled back with it, so the commit runs first. It is idempotent per reservation at
 * the catalog: if the local transaction then fails, the reservation is still {@code HELD}, and the
 * retry — an activity retry or a repeated provider answer — commits again as a no-op before it
 * writes the tickets.
 *
 * <h2>Why the reservation is claimed first</h2>
 * The compare-and-set from {@code HELD} to {@code CONFIRMED} runs <em>before</em>
 * the tickets are written, not after as a naive ordering would have it. Two provider callbacks for one payment are ordinary, and if both
 * checked the status before either wrote, both would pass the check and both
 * would issue tickets. Claiming first makes the second caller lose the race
 * while the first still holds the document, and the transaction means a claim
 * whose follow-up work fails is rolled back with it.
 *
 * <h2>Why the transaction is explicit</h2>
 * {@link TransactionalOperator} rather than {@code @Transactional}, so the boundary sits visibly on
 * the exact operators it covers and cannot quietly widen to include the catalog call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private final TicketReservationRepository reservationRepository;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final TicketRepository ticketRepository;
    private final ReservationTransitions transitions;
    private final EscrowService escrowService;
    private final CommissionService commissionService;
    private final AccountingService accountingService;
    private final CatalogServiceClient catalogServiceClient;
    private final Outbox outbox;
    private final TransactionalOperator transactionalOperator;

    @Override
    public Mono<List<Ticket>> confirm(String reservationId, String paymentIntentId, String providerTxnId) {
        return reservationRepository.findById(reservationId)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException(reservationId)))
                .flatMap(reservation -> switch (reservation.getStatus()) {
                    // Already done. A duplicate callback, which is routine rather
                    // than exceptional — return what the first confirmation wrote.
                    case CONFIRMED -> alreadyConfirmed(reservation);

                    case HELD -> confirmHeld(reservation, paymentIntentId, providerTxnId);

                    // Terminal and not confirmed: the hold is gone and the
                    // inventory with it. Refusing is the only safe answer — the
                    // seats may already belong to somebody else.
                    default -> Mono.error(new ReservationStateInvalid(reservation));
                });
    }

    private Mono<List<Ticket>> alreadyConfirmed(TicketReservation reservation) {
        return ticketRepository.findByReservationId(reservation.getId())
                .collectList()
                .doOnNext(tickets -> log.info(
                        "Reservation {} is already CONFIRMED — returning its {} existing ticket(s)",
                        reservation.getId(), tickets.size()));
    }

    private Mono<List<Ticket>> confirmHeld(TicketReservation reservation,
                                           String paymentIntentId,
                                           String providerTxnId) {
        if (reservation.isExpired(clock.instant())) {
            // The money arrived after the hold lapsed. The inventory went back
            // and may have been sold to someone else, so issuing tickets here
            // would oversell. The payment has to be refunded instead.
            return Mono.error(new ReservationExpiredException(reservation.getId()));
        }

        Mono<List<Ticket>> confirmation = transitions.compareAndSetFromHeld(
                        reservation.getId(), ReservationStateMachine.Action.CONFIRM, null)
                .flatMap(claimed -> {
                    if (!claimed) {
                        // Somebody else confirmed it between our read and our
                        // write. Their tickets are the real ones.
                        return alreadyConfirmed(reservation);
                    }
                    return issueTickets(reservation, paymentIntentId, providerTxnId)
                            .flatMap(tickets -> recordFinancials(reservation, tickets, paymentIntentId)
                                    .then(stagePurchased(reservation, tickets))
                                    .thenReturn(tickets));
                })
                .as(transactionalOperator::transactional);

        return commitInventory(reservation).then(confirmation);
    }

    /**
     * Moves the tier counters from reserved to sold, outside the transaction.
     *
     * <p>First because it is the step most likely to refuse: the catalog is the only participant that
     * can say the inventory is not what booking believes. The catalog records the commit against this
     * reservation, so calling it again for the same reservation changes nothing — which is what makes
     * running it before the transaction, and again on a retry, safe.
     */
    private Mono<Void> commitInventory(TicketReservation reservation) {
        return Flux.fromIterable(reservation.getItems())
                .concatMap(item -> catalogServiceClient.commitInventoryToSold(
                                item.getTicketTierId(), item.getQuantity(), reservation.getId())
                        .flatMap(result -> result.success()
                                ? Mono.just(result)
                                : Mono.error(new IllegalStateException(
                                        "Inventory commit refused for tier " + item.getTicketTierId()
                                                + ": " + result.errorMessage()))))
                .then();
    }

    /** One row per seat: a ticket has no quantity field. */
    private Mono<List<Ticket>> issueTickets(TicketReservation reservation,
                                            String paymentIntentId,
                                            String providerTxnId) {
        List<Ticket> tickets = new ArrayList<>();
        for (TicketReservation.ReservationItem item : reservation.getItems()) {
            for (int seat = 0; seat < item.getQuantity(); seat++) {
                tickets.add(buildTicket(reservation, item, paymentIntentId, providerTxnId));
            }
        }
        return ticketRepository.saveAll(tickets).collectList();
    }

    private Ticket buildTicket(TicketReservation reservation,
                               TicketReservation.ReservationItem item,
                               String paymentIntentId,
                               String providerTxnId) {
        BigDecimal price = item.getUnitPrice();
        return Ticket.builder()
                .ticketNumber(Ticket.generateTicketNumber())
                .eventId(reservation.getEventId())
                .reservationId(reservation.getId())
                .bookingId(reservation.getId())
                .bookingNumber(reservation.getBookingNumber())
                .ticketTierId(item.getTicketTierId())
                .ticketCategoryCode(item.getTicketTierId())
                .ticketCategoryName(item.getTierName())
                .buyerId(reservation.getUserId())
                .organizerId(reservation.getOrganizerId())
                .organizationId(reservation.getOrganizationId())
                .price(price)
                .currency(reservation.getCurrency())
                .status(TicketStatus.ISSUED)
                .quantity(1)
                .commissionRate(commissionService.getCommissionRate())
                .commissionAmount(commissionService.calculateCommission(price))
                .netAmount(commissionService.calculateNetAmount(price))
                .qrCode(UUID.randomUUID().toString())
                .paymentReference(providerTxnId != null ? providerTxnId : paymentIntentId)
                .purchaseDate(clock.instant())
                .isActive(true)
                .build();
    }

    /**
     * Escrow credit, commission record and journal entries — per ticket.
     *
     * <p>Per ticket rather than once for the reservation because refunds are per
     * ticket (a refund debits a single seat's net). A single reservation-sized
     * credit would leave every partial refund without a matching credit line, and
     * reconciliation walks those pairs.
     */
    private Mono<Void> recordFinancials(TicketReservation reservation,
                                        List<Ticket> tickets,
                                        String paymentIntentId) {
        return Flux.fromIterable(tickets)
                .concatMap(ticket -> commissionService.createPendingCommission(
                                ticket.getId(),
                                ticket.getEventId(),
                                reservation.getOrganizerId(),
                                reservation.getOrganizationId(),
                                ticket.getPrice())
                        .then(escrowService.creditEscrow(
                                ticket.getEventId(),
                                ticket.getNetAmount(),
                                ticket.getId(),
                                paymentIntentId,
                                "Ticket sale: " + ticket.getTicketNumber()))
                        .then(accountingService.recordTicketSale(
                                paymentIntentId,
                                ticket.getId(),
                                ticket.getEventId(),
                                ticket.getPrice(),
                                ticket.getNetAmount(),
                                ticket.getCommissionAmount(),
                                null,
                                ticket.getCurrency())))
                .then();
    }

    /**
     * One {@code booking.TicketPurchased} per ticket, staged inside the confirmation transaction.
     *
     * <p>The tickets and their announcement commit together: the outbox drain
     * publishes the envelopes after commit, and a rollback leaves no envelope promising tickets that
     * do not exist. The payload is identifiers only — no buyer name, email or phone crosses the bus.</p>
     */
    private Mono<Void> stagePurchased(TicketReservation reservation, List<Ticket> tickets) {
        Instant now = clock.instant();
        return Flux.fromIterable(tickets)
                .concatMap(ticket -> outbox.stage(EventEnvelopes.of(
                        EventType.BOOKING_TICKET_PURCHASED,
                        now,
                        reservation.getId(),
                        Map.of("ticketId", ticket.getId(),
                                "eventId", ticket.getEventId(),
                                "tierId", ticket.getTicketTierId(),
                                "ownerId", ticket.getBuyerId(),
                                "quantity", 1))))
                .then();
    }

    @Override
    public Mono<TicketReservation> release(String reservationId,
                                           ReservationStateMachine.Action terminalAction,
                                           String reason) {
        return reservationRepository.findById(reservationId)
                .switchIfEmpty(Mono.error(new ReservationNotFoundException(reservationId)))
                .flatMap(reservation -> {
                    if (reservation.getStatus() != ReservationStatus.HELD) {
                        // Already resolved. Not an error: the TTL index, the
                        // expiry timer and a failed-payment callback all reach the same
                        // row, and every release path has to be a
                        // no-op the second time.
                        log.debug("Reservation {} was already {} — release is a no-op",
                                reservationId, reservation.getStatus());
                        return Mono.just(reservation);
                    }
                    return transitions.compareAndSetFromHeld(reservationId, terminalAction, reason)
                            .flatMap(moved -> moved
                                    // Only the winner returns the inventory. A
                                    // second release here would credit the tier
                                    // twice and manufacture seats.
                                    ? releaseInventory(reservation)
                                            .then(reservationRepository.findById(reservationId))
                                    : reservationRepository.findById(reservationId));
                });
    }

    private Mono<Void> releaseInventory(TicketReservation reservation) {
        return Flux.fromIterable(reservation.getItems())
                .concatMap(item -> catalogServiceClient.releaseInventory(
                                item.getTicketTierId(), item.getQuantity(), reservation.getId())
                        .doOnNext(result -> {
                            if (!result.success()) {
                                // Loud, because this is an inventory leak: the
                                // reservation is terminal but the tier still has
                                // the seats held. Nobody can buy them and nothing
                                // else will try again.
                                log.error("INVENTORY LEAK: reservation {} released but tier {} "
                                                + "did not give back {} seats: {}",
                                        reservation.getId(), item.getTicketTierId(),
                                        item.getQuantity(), result.errorMessage());
                            }
                        }))
                .then();
    }

    /** Raised when an operation is attempted against a terminal reservation. */
    public static class ReservationStateInvalid extends RuntimeException {
        private final ReservationStatus currentStatus;

        ReservationStateInvalid(TicketReservation reservation) {
            super("RESERVATION_STATE_INVALID: reservation " + reservation.getId()
                    + " is " + reservation.getStatus());
            this.currentStatus = reservation.getStatus();
        }

        public ReservationStatus getCurrentStatus() {
            return currentStatus;
        }
    }
}
