package com.pml.booking.service.impl;

import com.pml.booking.domain.ReservationStateMachine;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.booking.service.PurchaseService;
import com.pml.booking.service.ReservationService;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.booking.web.graphql.dto.TicketSelectionInput;
import com.pml.shared.constants.ReservationStatus;
import com.pml.shared.dto.EventSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Takes the inventory out of circulation, and gives it back.
 *
 * <h2>Reserve first, pay second</h2>
 * The hold costs ten minutes of inventory and buys certainty about what is being
 * sold. The alternative — charge, then look for a seat — is how a platform ends
 * up owing refunds for tickets it never had.
 *
 * <h2>The quote does not move under the buyer</h2>
 * Unit price and total are computed here, once, from the event's mirrored tier
 * prices, and written onto the reservation. A tier price change during those ten
 * minutes does not change what this buyer pays: the number they agreed to is the
 * number the payment intent charges and the number the ticket records. Re-reading
 * the price at confirmation would be simpler and would silently overcharge
 * anyone who was mid-checkout when an organiser edited a tier.
 *
 * @see <a href="file:../../../../../../specs/ticketing/001-reservation-and-hold/spec.md">ET-TKT-001</a>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final TicketReservationRepository reservationRepository;
    private final CatalogServiceClient catalogServiceClient;
    private final PurchaseService purchaseService;

    @Value("${booking.reservation.ttl-minutes:10}")
    private int reservationTtlMinutes;

    /** ET-PLT-002's money scale. Applied once, to the total. */
    private static final int MONEY_SCALE = 2;

    @Override
    public Mono<TicketReservation> createReservation(String userId, ReserveTicketsInput input) {
        String idempotencyKey = input.idempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Mono.error(new IllegalArgumentException(
                    "IDEMPOTENCY_KEY_REQUIRED: reserveTickets needs a client-supplied key"));
        }

        // Checked before anything moves. A repeat of a request whose response
        // the client never saw must not take a second block of inventory —
        // that is the entire failure mode the key exists to prevent.
        return reservationRepository.findByIdempotencyKey(idempotencyKey)
                .doOnNext(existing -> log.info(
                        "Idempotency key {} already produced reservation {} — returning it unchanged",
                        idempotencyKey, existing.getId()))
                .switchIfEmpty(Mono.defer(() -> existingHoldFor(userId, input)
                        .switchIfEmpty(Mono.defer(() -> reserveAfresh(userId, input, idempotencyKey)))));
    }

    /**
     * A live hold this buyer already has on one of the requested tiers (R5).
     *
     * <p>Returned unchanged rather than added to. A buyer on a flaky connection
     * who taps <em>reserve</em> four times would otherwise accumulate four holds
     * and lock out four other buyers while paying for one.
     */
    private Mono<TicketReservation> existingHoldFor(String userId, ReserveTicketsInput input) {
        List<String> requestedTiers = input.selections().stream()
                .map(TicketSelectionInput::ticketTierId)
                .toList();

        return reservationRepository.findByUserIdAndStatus(userId, ReservationStatus.HELD)
                .filter(held -> !held.isExpired())
                .filter(held -> held.getItems() != null && held.getItems().stream()
                        .anyMatch(item -> requestedTiers.contains(item.getTicketTierId())))
                .next()
                .doOnNext(held -> log.info(
                        "Buyer {} already holds reservation {} on a requested tier — returning it "
                                + "rather than stacking a second hold",
                        userId, held.getId()));
    }

    private Mono<TicketReservation> reserveAfresh(String userId,
                                                  ReserveTicketsInput input,
                                                  String idempotencyKey) {
        // Generated up front because the catalog needs it as the hold's owner
        // before the reservation document exists. If the save then fails, this
        // is the handle the rollback uses to find what to give back.
        String reservationId = UUID.randomUUID().toString();

        return catalogServiceClient.getEventById(input.eventId())
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "EVENT_UNKNOWN: " + input.eventId())))
                .flatMap(event -> takeInventory(input.selections(), reservationId)
                        .then(Mono.defer(() -> reservationRepository.save(
                                        quote(userId, input, event, reservationId, idempotencyKey)))
                                .onErrorResume(error -> giveBack(input.selections(), reservationId)
                                        .then(Mono.error(translate(error, idempotencyKey))))))
                .doOnSuccess(saved -> log.info("Reservation {} held for buyer {} until {}",
                        saved.getId(), userId, saved.getExpiresAt()));
    }

    /**
     * A duplicate key here means one of R5's or R6's unique indexes refused the
     * write — a second buyer request that raced past the read-side checks above.
     *
     * <p>Translated rather than propagated because a raw
     * {@code DuplicateKeyException} reaching the resolver tells the buyer nothing
     * and looks like a server fault, when in fact the platform did exactly what
     * it should: refused to create a second hold.
     */
    private Throwable translate(Throwable error, String idempotencyKey) {
        if (error instanceof DuplicateKeyException) {
            return new IllegalStateException(
                    "RESERVATION_ALREADY_EXISTS: a concurrent request already created a hold "
                            + "for this buyer and tier (key " + idempotencyKey + ")", error);
        }
        return error;
    }

    /**
     * Takes every tier's inventory, giving back what was already taken if any
     * one of them refuses.
     *
     * <p>Sequential, not parallel: a partial failure has to know exactly which
     * tiers succeeded so it can return precisely those. Fanning out and
     * collecting would be faster and would make the rollback set ambiguous
     * whenever two tiers failed at once.
     */
    private Mono<Void> takeInventory(List<TicketSelectionInput> selections, String reservationId) {
        List<TicketSelectionInput> taken = new ArrayList<>();

        return Flux.fromIterable(selections)
                .concatMap(selection -> catalogServiceClient.reserveInventory(
                                selection.ticketTierId(), selection.quantity(), reservationId)
                        .flatMap(result -> {
                            if (result.success()) {
                                taken.add(selection);
                                return Mono.just(result);
                            }
                            String message = result.errorMessage() == null
                                    ? "TIER_SOLD_OUT: " + selection.ticketTierId()
                                    : result.errorMessage();
                            return giveBack(taken, reservationId)
                                    .then(Mono.error(new IllegalStateException(message)));
                        }))
                .then();
    }

    private Mono<Void> giveBack(List<TicketSelectionInput> selections, String reservationId) {
        return Flux.fromIterable(selections)
                .concatMap(selection -> catalogServiceClient.releaseInventory(
                                selection.ticketTierId(), selection.quantity(), reservationId)
                        .doOnNext(result -> {
                            if (!result.success()) {
                                log.error("INVENTORY LEAK: could not return {} seats of tier {} "
                                                + "after reservation {} failed: {}",
                                        selection.quantity(), selection.ticketTierId(),
                                        reservationId, result.errorMessage());
                            }
                        }))
                .then();
    }

    /**
     * Prices the reservation from the event's mirrored tier prices.
     *
     * <p>Rounding is applied once, to the total, at scale 2. Rounding each line
     * and then summing produces a different number for the same basket — a
     * discrepancy of a few ngwee that reconciliation will chase for hours.
     */
    private TicketReservation quote(String userId,
                                    ReserveTicketsInput input,
                                    EventSummaryDto event,
                                    String reservationId,
                                    String idempotencyKey) {
        List<TicketReservation.ReservationItem> items = input.selections().stream()
                .map(selection -> line(selection, event))
                .toList();

        BigDecimal subtotal = items.stream()
                .map(TicketReservation.ReservationItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return TicketReservation.builder()
                .id(reservationId)
                .eventId(input.eventId())
                .userId(userId)
                .organizerId(event.getOrganizerId())
                .organizationId(event.getOrganizationId())
                .items(items)
                .status(ReservationStatus.HELD)
                .idempotencyKey(idempotencyKey)
                .promoCode(input.promoCode())
                .subtotal(subtotal)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(subtotal.setScale(MONEY_SCALE, RoundingMode.HALF_UP))
                .currency("ZMW")
                .expiresAt(LocalDateTime.now().plusMinutes(reservationTtlMinutes))
                .build();
    }

    private TicketReservation.ReservationItem line(TicketSelectionInput selection, EventSummaryDto event) {
        EventSummaryDto.TicketCategoryDto tier = tierOf(event, selection.ticketTierId());
        BigDecimal unitPrice = tier.getPrice();
        return TicketReservation.ReservationItem.builder()
                .ticketTierId(selection.ticketTierId())
                .tierName(tier.getName())
                .quantity(selection.quantity())
                .unitPrice(unitPrice)
                .subtotal(unitPrice.multiply(BigDecimal.valueOf(selection.quantity())))
                .build();
    }

    /**
     * The tier's mirrored definition, or a refusal.
     *
     * <p>Refusing is the point. The code this replaced defaulted to a hard-coded
     * {@code new BigDecimal("100.00")} when it could not find a price, so an
     * unknown tier produced a reservation quoting a number nobody had ever set —
     * and the buyer was charged it.
     */
    private EventSummaryDto.TicketCategoryDto tierOf(EventSummaryDto event, String tierId) {
        List<EventSummaryDto.TicketCategoryDto> tiers = event.getTicketCategories();
        if (tiers != null) {
            for (EventSummaryDto.TicketCategoryDto tier : tiers) {
                if (Objects.equals(tier.getCode(), tierId)) {
                    if (tier.getPrice() == null) {
                        throw new IllegalStateException(
                                "TIER_PRICE_UNAVAILABLE: tier " + tierId + " has no price");
                    }
                    return tier;
                }
            }
        }
        throw new IllegalStateException("TIER_UNKNOWN: " + tierId + " is not a tier of event " + event.getId());
    }

    @Override
    public Mono<Boolean> cancelReservation(String reservationId) {
        return purchaseService.release(
                        reservationId, ReservationStateMachine.Action.CANCEL, null)
                .map(reservation -> reservation.getStatus() == ReservationStatus.RELEASED)
                .onErrorResume(error -> {
                    log.warn("Cancel refused for reservation {}: {}", reservationId, error.getMessage());
                    return Mono.just(false);
                });
    }

    @Override
    public Mono<TicketReservation> findById(String id) {
        return reservationRepository.findById(id);
    }

    @Override
    public Flux<TicketReservation> findActiveByUserId(String userId) {
        return reservationRepository.findActiveByUserId(userId);
    }

    @Override
    public Flux<TicketReservation> findByEventId(String eventId) {
        return reservationRepository.findByEventId(eventId);
    }

    @Override
    public Flux<TicketReservation> findExpiredByEventId(String eventId, LocalDateTime since) {
        return reservationRepository.findByEventIdAndStatusAndExpiresAtBefore(
                eventId, ReservationStatus.EXPIRED, since);
    }

    @Override
    public Flux<TicketReservation> findExpiredSince(LocalDateTime since) {
        return reservationRepository.findByStatusAndExpiresAtBefore(ReservationStatus.EXPIRED, since);
    }

    @Override
    public Mono<Long> expireReservations() {
        return reservationRepository.findExpiredReservations()
                .concatMap(reservation -> purchaseService.release(
                                reservation.getId(), ReservationStateMachine.Action.EXPIRE, null)
                        // One reservation that cannot be released must not stop
                        // the sweep: the rest of the batch is holding inventory
                        // that other buyers are waiting for.
                        .onErrorResume(error -> {
                            log.error("Could not expire reservation {}: {}",
                                    reservation.getId(), error.getMessage());
                            return Mono.empty();
                        })
                        .filter(released -> released.getStatus() == ReservationStatus.EXPIRED))
                .count()
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Expiry sweep released {} reservation(s)", count);
                    }
                });
    }
}
