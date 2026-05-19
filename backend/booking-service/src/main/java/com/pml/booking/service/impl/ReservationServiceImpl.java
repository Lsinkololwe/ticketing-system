package com.pml.booking.service.impl;

import com.pml.booking.web.graphql.dto.CompleteReservationInput;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.booking.web.graphql.dto.TicketSelectionInput;
import com.pml.booking.domain.enums.ReservationStatus;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketReservation;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.client.dto.InventoryReservationResult;
import com.pml.booking.repository.TicketReservationRepository;
import com.pml.booking.service.PaymentService;
import com.pml.booking.service.PromoCodeService;
import com.pml.booking.service.ReservationService;
import com.pml.booking.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reservation Service Implementation
 *
 * Business Intent: Manages temporary ticket reservations with TTL-based expiration.
 * Prevents inventory blocking from cart abandonment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final TicketReservationRepository reservationRepository;
    private final PromoCodeService promoCodeService;
    private final TicketService ticketService;
    private final PaymentService paymentService;
    private final CatalogServiceClient catalogServiceClient;

    @Value("${reservation.ttl.minutes:10}")
    private int reservationTtlMinutes;

    private static final Duration ROLLBACK_TIMEOUT = Duration.ofSeconds(30);

    @Override
    public Mono<TicketReservation> createReservation(String userId, ReserveTicketsInput input) {
        log.info("Creating reservation for user {} on event {}", userId, input.eventId());

        // Generate a unique reservation ID for inventory tracking
        String reservationId = UUID.randomUUID().toString();

        // Step 1: Reserve inventory FIRST (atomic operation)
        // This prevents overselling by holding inventory before creating the reservation
        return reserveInventoryForSelections(input.selections(), reservationId)
                .flatMap(reserveResult -> {
                    if (!reserveResult) {
                        return Mono.error(new IllegalStateException(
                                "Insufficient inventory for one or more ticket tiers"));
                    }

                    // Step 2: Build and save the reservation (inventory is now held)
                    return buildReservation(userId, input, reservationId)
                            .flatMap(reservationRepository::save)
                            .doOnSuccess(reservation ->
                                    log.info("Reservation created with inventory held: {}", reservation.getId()))
                            .onErrorResume(error -> {
                                // CRITICAL: Rollback inventory on reservation save failure
                                // Using blocking call to ensure rollback completes before returning error
                                log.error("Reservation save failed, rolling back inventory: {}", error.getMessage());
                                return rollbackInventoryBlocking(input.selections(), reservationId)
                                        .then(Mono.error(error));
                            });
                });
    }

    /**
     * Rollback inventory with blocking semantics.
     *
     * <p><b>CRITICAL</b>: This method uses blocking to ensure inventory is released
     * before the error is propagated. This prevents inventory leaks when reservation
     * save fails.</p>
     *
     * @param selections List of ticket selections to release
     * @param reservationId Reservation ID for tracking
     * @return Mono that completes after rollback
     */
    private Mono<Void> rollbackInventoryBlocking(List<TicketSelectionInput> selections, String reservationId) {
        return Mono.fromRunnable(() -> {
            try {
                log.info("Starting blocking inventory rollback for reservation: {}", reservationId);
                releaseInventoryForSelections(selections, reservationId)
                        .block(ROLLBACK_TIMEOUT);
                log.info("Inventory rollback completed successfully for reservation: {}", reservationId);
            } catch (Exception e) {
                // Log but don't fail - the original error should propagate
                log.error("CRITICAL: Inventory rollback failed for reservation {}: {}. " +
                          "Manual intervention may be required.", reservationId, e.getMessage(), e);
            }
        });
    }

    /**
     * Reserve inventory for all selections atomically.
     * If any reservation fails, releases all previously reserved inventory.
     *
     * @param selections List of ticket selections
     * @param reservationId Unique reservation identifier
     * @return Mono<Boolean> true if all reservations succeeded
     */
    private Mono<Boolean> reserveInventoryForSelections(List<TicketSelectionInput> selections, String reservationId) {
        List<String> successfullyReserved = new ArrayList<>();

        return Flux.fromIterable(selections)
                .concatMap(selection -> catalogServiceClient.reserveInventory(
                                selection.ticketTierId(),
                                selection.quantity(),
                                reservationId)
                        .flatMap(result -> {
                            if (result.success()) {
                                successfullyReserved.add(selection.ticketTierId());
                                return Mono.just(true);
                            } else {
                                log.warn("Inventory reservation failed for tier {}: {}",
                                        selection.ticketTierId(), result.errorMessage());
                                // Release all previously reserved
                                return releasePartialReservations(successfullyReserved, selections, reservationId)
                                        .then(Mono.just(false));
                            }
                        }))
                .all(success -> success);
    }

    /**
     * Release inventory for all selections.
     */
    private Mono<Void> releaseInventoryForSelections(List<TicketSelectionInput> selections, String reservationId) {
        return Flux.fromIterable(selections)
                .flatMap(selection -> catalogServiceClient.releaseInventory(
                        selection.ticketTierId(),
                        selection.quantity(),
                        reservationId))
                .then();
    }

    /**
     * Release partially reserved inventory on failure.
     */
    private Mono<Void> releasePartialReservations(List<String> reservedTierIds,
                                                   List<TicketSelectionInput> selections,
                                                   String reservationId) {
        return Flux.fromIterable(selections)
                .filter(s -> reservedTierIds.contains(s.ticketTierId()))
                .flatMap(selection -> catalogServiceClient.releaseInventory(
                        selection.ticketTierId(),
                        selection.quantity(),
                        reservationId))
                .then();
    }

    @Override
    public Mono<List<Ticket>> completeReservation(String reservationId, CompleteReservationInput input) {
        log.info("Completing reservation: {}", reservationId);

        return reservationRepository.findById(reservationId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Reservation not found")))
            .flatMap(reservation -> {
                // Validate reservation is active and not expired
                if (reservation.getStatus() != ReservationStatus.ACTIVE) {
                    return Mono.error(new IllegalStateException("Reservation is not active"));
                }
                if (reservation.isExpired()) {
                    return Mono.error(new IllegalStateException("Reservation has expired"));
                }

                // Apply promo code if provided
                Mono<BigDecimal> discountMono = input.promoCode() != null ?
                    applyPromoCode(input.promoCode(), reservation) :
                    Mono.just(BigDecimal.ZERO);

                return discountMono.flatMap(discount -> {
                    BigDecimal finalAmount = reservation.getTotalAmount().subtract(discount);

                    // Process payment
                    // TODO: Integrate with PaymentService to create payment intent

                    // Create tickets
                    return createTicketsFromReservation(reservation)
                        .collectList()
                        .flatMap(tickets -> {
                            // Update reservation status
                            reservation.setStatus(ReservationStatus.CONVERTED);
                            reservation.setConvertedAt(LocalDateTime.now());
                            reservation.setDiscountAmount(discount);

                            return reservationRepository.save(reservation)
                                .thenReturn(tickets);
                        });
                });
            });
    }

    @Override
    public Mono<Boolean> cancelReservation(String reservationId) {
        log.info("Cancelling reservation: {}", reservationId);

        return reservationRepository.findById(reservationId)
                .filter(reservation -> reservation.getStatus() == ReservationStatus.ACTIVE)
                .flatMap(reservation -> {
                    // Release inventory FIRST before marking cancelled
                    return releaseInventoryForReservation(reservation)
                            .then(Mono.defer(() -> {
                                reservation.setStatus(ReservationStatus.CANCELLED);
                                return reservationRepository.save(reservation);
                            }));
                })
                .map(reservation -> true)
                .defaultIfEmpty(false);
    }

    /**
     * Release all reserved inventory for a reservation.
     */
    private Mono<Void> releaseInventoryForReservation(TicketReservation reservation) {
        return Flux.fromIterable(reservation.getItems())
                .flatMap(item -> catalogServiceClient.releaseInventory(
                        item.getTicketTierId(),
                        item.getQuantity(),
                        reservation.getId())
                        .doOnSuccess(result -> {
                            if (result.success()) {
                                log.debug("Released {} tickets for tier {} (reservation: {})",
                                        item.getQuantity(), item.getTicketTierId(), reservation.getId());
                            } else {
                                log.warn("Failed to release inventory for tier {}: {}",
                                        item.getTicketTierId(), result.errorMessage());
                            }
                        }))
                .then();
    }

    @Override
    public Mono<TicketReservation> extendReservation(String reservationId, int minutes) {
        log.info("Extending reservation {} by {} minutes", reservationId, minutes);

        return reservationRepository.findById(reservationId)
            .flatMap(reservation -> {
                if (reservation.getStatus() != ReservationStatus.ACTIVE) {
                    return Mono.error(new IllegalStateException("Only active reservations can be extended"));
                }

                LocalDateTime newExpiry = reservation.getExpiresAt().plusMinutes(minutes);
                reservation.setExpiresAt(newExpiry);

                return reservationRepository.save(reservation);
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
    public Mono<Void> expireReservations() {
        log.debug("Running reservation expiration task");

        return reservationRepository.findExpiredReservations()
                .flatMap(reservation -> {
                    log.info("Expiring reservation: {} - releasing inventory", reservation.getId());

                    // CRITICAL: Release inventory BEFORE marking as expired
                    // This restores inventory to the available pool
                    return releaseInventoryForReservation(reservation)
                            .then(Mono.defer(() -> {
                                reservation.setStatus(ReservationStatus.EXPIRED);
                                return reservationRepository.save(reservation);
                            }))
                            .doOnSuccess(r -> log.info("Reservation {} expired, inventory released", r.getId()))
                            .doOnError(e -> log.error("Failed to expire reservation {}", reservation.getId(), e));
                })
                .then()
                .doOnSuccess(v -> log.debug("Reservation expiration task completed"));
    }

    // ========================================================================
    // PRIVATE HELPER METHODS
    // ========================================================================

    private Mono<TicketReservation> buildReservation(String userId, ReserveTicketsInput input, String reservationId) {
        // Fetch event data from catalog-service for organizer information
        return catalogServiceClient.getEventById(input.eventId())
                .flatMap(eventSummary -> {
                    // TODO: Fetch actual tier prices from eventSummary.ticketCategories
                    List<TicketReservation.ReservationItem> items = input.selections().stream()
                            .map(this::buildReservationItem)
                            .collect(Collectors.toList());

                    BigDecimal totalAmount = items.stream()
                            .map(TicketReservation.ReservationItem::getSubtotal)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(reservationTtlMinutes);

                    return Mono.just(TicketReservation.builder()
                            .id(reservationId)  // Use the same ID that was used for inventory reservation
                            .eventId(input.eventId())
                            .userId(userId)
                            .organizerId(eventSummary.getOrganizerId())
                            .organizationId(eventSummary.getOrganizationId())
                            .items(items)
                            .status(ReservationStatus.ACTIVE)
                            .expiresAt(expiresAt)
                            .createdAt(LocalDateTime.now())
                            .totalAmount(totalAmount)
                            .build());
                })
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Event not found: " + input.eventId())));
    }

    private TicketReservation.ReservationItem buildReservationItem(TicketSelectionInput selection) {
        // TODO: Fetch actual tier details and price from catalog-service
        BigDecimal unitPrice = new BigDecimal("100.00"); // Placeholder
        String tierName = "Standard"; // Placeholder

        return TicketReservation.ReservationItem.builder()
            .ticketTierId(selection.ticketTierId())
            .tierName(tierName)
            .quantity(selection.quantity())
            .unitPrice(unitPrice)
            .subtotal(unitPrice.multiply(new BigDecimal(selection.quantity())))
            .build();
    }

    private Mono<BigDecimal> applyPromoCode(String code, TicketReservation reservation) {
        List<String> tierIds = reservation.getItems().stream()
            .map(TicketReservation.ReservationItem::getTicketTierId)
            .collect(Collectors.toList());

        return promoCodeService.validatePromoCode(
                code,
                reservation.getEventId(),
                reservation.getTotalAmount(),
                tierIds
            )
            .flatMap(promoCode -> {
                BigDecimal discount = promoCode.calculateDiscount(reservation.getTotalAmount());

                // Increment usage count
                return promoCodeService.incrementUsage(promoCode.getId())
                    .thenReturn(discount);
            })
            .onErrorResume(e -> {
                log.warn("Promo code validation failed: {}", e.getMessage());
                return Mono.just(BigDecimal.ZERO);
            });
    }

    private Flux<Ticket> createTicketsFromReservation(TicketReservation reservation) {
        List<Mono<Ticket>> ticketMonos = new ArrayList<>();

        for (TicketReservation.ReservationItem item : reservation.getItems()) {
            for (int i = 0; i < item.getQuantity(); i++) {
                Mono<Ticket> ticketMono = createSingleTicket(reservation, item);
                ticketMonos.add(ticketMono);
            }
        }

        return Flux.concat(ticketMonos);
    }

    private Mono<Ticket> createSingleTicket(TicketReservation reservation, TicketReservation.ReservationItem item) {
        // TODO: Fetch event details from catalog-service

        Ticket ticket = Ticket.builder()
                .eventId(reservation.getEventId())
                .reservationId(reservation.getId())        // Link to reservation for inventory tracking
                .ticketTierId(item.getTicketTierId())      // Link to tier for inventory operations
                .buyerId(reservation.getUserId())
                .eventTitle("Event Title") // Placeholder - should come from catalog-service
                .eventDate("2024-12-31") // Placeholder - should come from catalog-service
                .ticketCategoryCode(item.getTicketTierId())
                .ticketCategoryName(item.getTierName())
                .price(item.getUnitPrice())
                .currency("ZMW")
                .purchaseDate(LocalDateTime.now())
                .build();

        return ticketService.createTicket(ticket);
    }
}
