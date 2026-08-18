package com.pml.booking.service.impl;

import com.pml.booking.domain.ReservationStateMachine;
import com.pml.booking.domain.ReservationTransitions;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.event.domain.TicketPurchasedEvent;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ET-TKT-001 R7's confirmation: one transaction, or none of it.
 *
 * <h2>What changed, and why it was worth changing</h2>
 * Until now a ticket was written in {@code PENDING_PAYMENT} <em>before</em> the
 * buyer was charged, and the payment callback flipped its status. That produced
 * a row in {@code booking_tickets} for money the platform had not received —
 * counted by the organiser's sales figures, visible in the buyer's wallet, and
 * indistinguishable at a glance from a ticket that was actually paid for. Every
 * abandoned checkout left one behind.
 *
 * <p>Here nothing exists until the money does. The reservation holds the
 * inventory; the ticket is written inside the same transaction that credits the
 * escrow and records the commission. If any part of that fails, none of it
 * happened.
 *
 * <h2>Why the reservation is claimed first</h2>
 * The compare-and-set from {@code HELD} to {@code CONFIRMED} runs <em>before</em>
 * the tickets are written, not after as a naive reading of the spec's sketch
 * suggests. Two provider callbacks for one payment are ordinary, and if both
 * checked the status before either wrote, both would pass the check and both
 * would issue tickets. Claiming first makes the second caller lose the race
 * while the first still holds the document, and the transaction means a claim
 * whose follow-up work fails is rolled back with it.
 *
 * <h2>Why the transaction is explicit</h2>
 * {@link TransactionalOperator} rather than {@code @Transactional}: this service
 * shares a context with Spring Modulith's JDBC transaction manager, which is
 * {@code @Primary}. An annotation would silently bind the money-moving code to
 * the Postgres manager and MongoDB would never see a transaction at all — the
 * kind of failure that looks like nothing is wrong until a partial confirmation
 * survives a crash.
 *
 * @see <a href="file:../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseServiceImpl implements PurchaseService {

    private final TicketReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final ReservationTransitions transitions;
    private final EscrowService escrowService;
    private final CommissionService commissionService;
    private final AccountingService accountingService;
    private final CatalogServiceClient catalogServiceClient;
    private final ApplicationEventPublisher eventPublisher;
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
        if (reservation.isExpired()) {
            // The money arrived after the hold lapsed. The inventory went back
            // and may have been sold to someone else, so issuing tickets here
            // would oversell. The payment has to be refunded instead.
            return Mono.error(new ReservationExpiredException(reservation.getId()));
        }

        return transitions.compareAndSetFromHeld(
                        reservation.getId(), ReservationStateMachine.Action.CONFIRM, null)
                .flatMap(claimed -> {
                    if (!claimed) {
                        // Somebody else confirmed it between our read and our
                        // write. Their tickets are the real ones.
                        return alreadyConfirmed(reservation);
                    }
                    return commitInventory(reservation)
                            .then(issueTickets(reservation, paymentIntentId, providerTxnId))
                            .flatMap(tickets -> recordFinancials(reservation, tickets, paymentIntentId)
                                    .thenReturn(tickets))
                            .doOnNext(tickets -> publishPurchased(reservation, tickets, paymentIntentId, providerTxnId));
                })
                .as(transactionalOperator::transactional);
    }

    /**
     * Moves the tier counters from reserved to sold.
     *
     * <p>Done first because it is the step most likely to refuse: the catalog is
     * a separate service and the only participant that can tell us the inventory
     * is not what we think it is. Discovering that after writing tickets would
     * mean rolling back work that has already been announced.
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

    /** One row per seat. ET-TKT-002 is explicit that a ticket has no quantity field. */
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
                .purchaseDate(LocalDateTime.now())
                .isActive(true)
                .build();
    }

    /**
     * Escrow credit, commission record and journal entries — per ticket.
     *
     * <p>Per ticket rather than once for the reservation because refunds are per
     * ticket (ET-FIN-004 debits a single seat's net). A single reservation-sized
     * credit would leave every partial refund without a matching credit line, and
     * ET-FIN-005's reconciliation walks those pairs.
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
     * Published inside the transaction, on purpose.
     *
     * <p>Spring Modulith writes the publication row with the same commit, so the
     * event cannot survive a rollback and cannot be lost by one. The bus is
     * reached from the listener that consumes it, after commit — never from in
     * here (ET-PLT-003 §2).
     */
    private void publishPurchased(TicketReservation reservation,
                                  List<Ticket> tickets,
                                  String paymentIntentId,
                                  String providerTxnId) {
        for (Ticket ticket : tickets) {
            eventPublisher.publishEvent(new TicketPurchasedEvent(
                    ticket.getId(),
                    ticket.getTicketNumber(),
                    ticket.getEventId(),
                    ticket.getEventTitle(),
                    ticket.getBuyerId(),
                    ticket.getBuyerName() != null ? ticket.getBuyerName() : "Unknown",
                    ticket.getBuyerEmail(),
                    ticket.getBuyerPhone() != null ? ticket.getBuyerPhone() : "",
                    reservation.getOrganizerId(),
                    ticket.getTicketCategoryCode(),
                    ticket.getTicketCategoryName(),
                    1,
                    ticket.getPrice(),
                    ticket.getPrice(),
                    ticket.getCommissionAmount(),
                    ticket.getCommissionRate(),
                    ticket.getNetAmount(),
                    ticket.getCurrency(),
                    null,
                    providerTxnId,
                    paymentIntentId));
        }
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
                        // sweep and a failed-payment callback all reach the same
                        // row, and the spec requires every release path to be a
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
