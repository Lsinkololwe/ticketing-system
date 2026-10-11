package com.pml.booking.service.impl;

import com.pml.booking.domain.TicketStateMachine;
import com.pml.booking.domain.TicketStateMachine.Action;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.repository.dto.RevenueResult;
import com.pml.booking.repository.dto.SpentResult;
import com.pml.booking.service.TicketService;
import com.pml.booking.web.graphql.dto.AdminTicketUpdateInput;
import com.pml.booking.web.graphql.dto.BulkOperationResponse;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Ticket Service Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final com.pml.booking.service.BookingStore bookings;

    @Override
    public Mono<Ticket> findById(String id) {
        return ticketRepository.findById(id);
    }

    @Override
    public Mono<Ticket> findByTicketNumber(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber);
    }

    @Override
    public Flux<Ticket> findAll() {
        return ticketRepository.findAll();
    }

    @Override
    public Flux<Ticket> findByEventId(String eventId) {
        return ticketRepository.findByEventId(eventId);
    }

    @Override
    public Flux<Ticket> findByBuyerId(String buyerId) {
        return ticketRepository.findByBuyerId(buyerId);
    }

    @Override
    public Flux<Ticket> findByBuyerIdAndStatus(String buyerId, TicketStatus status) {
        return ticketRepository.findByBuyerIdAndStatus(buyerId, status);
    }

    @Override
    public Flux<Ticket> findByEventIdAndStatus(String eventId, TicketStatus status) {
        return ticketRepository.findByEventIdAndStatus(eventId, status);
    }

    @Override
    public Flux<Ticket> findByStatus(TicketStatus status) {
        return ticketRepository.findByStatus(status);
    }

    @Override
    public Flux<Ticket> findByOrganizerId(String organizerId) {
        return ticketRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Mono<Ticket> validateTicket(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .flatMap(ticket -> transition(ticket, Action.VALIDATE, t -> {
                    t.setValidatedAt(clock.instant());
                }))
                .doOnSuccess(t -> log.info("Ticket validated: {}", ticketNumber));
    }

    @Override
    public Mono<Ticket> refundTicket(String ticketNumber, String reason, String processedBy) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .flatMap(ticket -> transition(ticket, Action.SETTLE_REFUND, t -> {
                    t.setRefundedAt(clock.instant());
                    t.setRefundReason(reason);
                }))
                .doOnSuccess(t -> log.info("Ticket refunded: {}", ticketNumber));
    }

    @Override
    public Mono<Ticket> requestRefund(String ticketNumber, String reason) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .flatMap(ticket -> transition(ticket, Action.REQUEST_REFUND, t ->
                        t.setRefundReason(reason)))
                .doOnSuccess(t -> log.info("Refund requested for ticket: {}", ticketNumber));
    }

    @Override
    public Mono<Ticket> cancelTicket(String ticketNumber, String reason, String processedBy) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .flatMap(ticket -> transition(ticket, Action.CANCEL, t -> {
                    t.setCancelledAt(clock.instant());
                    t.setCancellationReason(reason);
                }))
                .flatMap(cancelled -> bookings.ticketCancelled(cancelled.getReservationId()).thenReturn(cancelled))
                .doOnSuccess(t -> log.info("Ticket cancelled: {}", ticketNumber));
    }

    /**
     * Apply one of the {@link TicketStateMachine} transitions, or refuse.
     *
     * <p>Every status write on this class goes through here. Per-method {@code if}
     * chains drift apart — one refuses a state another allows, and a cancel slips
     * through for an already expired ticket. The table is the only opinion.
     */
    private Mono<Ticket> transition(Ticket ticket, Action action, java.util.function.Consumer<Ticket> stamp) {
        TicketStatus to;
        try {
            to = TicketStateMachine.require(ticket.getStatus(), action);
        } catch (TicketStateMachine.IllegalTransitionException e) {
            return Mono.error(e);
        }
        ticket.setStatus(to);
        stamp.accept(ticket);
        ticket.setUpdatedAt(clock.instant());
        return ticketRepository.save(ticket);
    }

    @Override
    public Mono<Long> countByEventId(String eventId) {
        return ticketRepository.countByEventId(eventId);
    }

    @Override
    public Mono<Long> countByBuyerId(String buyerId) {
        return ticketRepository.countByBuyerId(buyerId);
    }

    /** {@link TicketStatus#isSold()}'s states, derived rather than listed so this never drifts from it. */
    private static final List<TicketStatus> SOLD_STATUSES = java.util.Arrays.stream(TicketStatus.values())
            .filter(TicketStatus::isSold)
            .toList();

    @Override
    public Mono<Long> countSoldByEventId(String eventId) {
        return ticketRepository.countByEventIdAndStatusIn(eventId, SOLD_STATUSES);
    }

    // ========================================================================
    // FEDERATION EXTENSION METHODS
    // ========================================================================

    @Override
    public Mono<Long> countByEventIdAndStatusIn(String eventId, Collection<TicketStatus> statuses) {
        log.debug("Counting tickets for event {} with statuses {}", eventId, statuses);
        return ticketRepository.countByEventIdAndStatusIn(eventId, statuses);
    }

    @Override
    public Mono<Long> countByBuyerIdAndStatusIn(String buyerId, Collection<TicketStatus> statuses) {
        log.debug("Counting tickets for buyer {} with statuses {}", buyerId, statuses);
        return ticketRepository.countByBuyerIdAndStatusIn(buyerId, statuses);
    }

    @Override
    public Mono<RevenueResult> calculateRevenueByEventId(String eventId) {
        log.debug("Calculating revenue for event {}", eventId);
        return ticketRepository.calculateRevenueByEventId(eventId);
    }

    @Override
    public Mono<SpentResult> calculateTotalSpentByBuyerId(String buyerId) {
        log.debug("Calculating total spent by buyer {}", buyerId);
        return ticketRepository.calculateTotalSpentByBuyerId(buyerId);
    }

    // ========================================================================
    // ADMIN TICKET OPERATIONS
    // ========================================================================

    @Override
    @Transactional
    public Mono<Ticket> adminUpdateTicket(String ticketId, AdminTicketUpdateInput input) {
        log.info("Admin updating ticket: {}", ticketId);

        // Reached only from the ADMIN-only adminUpdateTicket mutation: read the real request
        // scope rather than assuming platform authority.
        return ticketForCaller(ticketId)
                .flatMap(ticket -> {
                    // Only update non-null fields
                    if (input.buyerName() != null) {
                        ticket.setBuyerName(input.buyerName());
                    }
                    if (input.buyerEmail() != null) {
                        ticket.setBuyerEmail(input.buyerEmail());
                    }
                    if (input.buyerPhone() != null) {
                        ticket.setBuyerPhone(input.buyerPhone());
                    }
                    if (input.ticketCategoryCode() != null) {
                        ticket.setTicketCategoryCode(input.ticketCategoryCode());
                    }
                    if (input.notes() != null) {
                        // Store notes in metadata
                        if (ticket.getMetadata() == null) {
                            ticket.setMetadata(new java.util.HashMap<>());
                        }
                        ticket.getMetadata().put("adminNotes", input.notes());
                    }

                    ticket.setUpdatedAt(clock.instant());
                    return ticketRepository.save(ticket);
                })
                .doOnSuccess(t -> log.info("Admin updated ticket: {}", ticketId));
    }

    @Override
    @Transactional
    public Mono<Ticket> regenerateTicketQrCode(String ticketId) {
        log.info("Regenerating QR code for ticket: {}", ticketId);

        // Reached only from the ADMIN-only regenerateTicketQrCode mutation: read the real
        // request scope rather than assuming platform authority.
        return ticketForCaller(ticketId)
                .flatMap(ticket -> {
                    // A validated ticket cannot be re-issued — it
                    // has already been used — and neither can a terminal one.
                    if (!ticket.getStatus().isAdmissible()) {
                        return Mono.error(new TicketStateMachine.IllegalTransitionException(
                                ticket.getStatus(), Action.ISSUE));
                    }

                    // Generate new QR code (simplified - in production this would call a QR service)
                    String newQrCode = generateQrCodeData(ticket);
                    ticket.setQrCode(newQrCode);

                    // Also regenerate barcode if present
                    if (ticket.getBarcode() != null) {
                        ticket.setBarcode(generateBarcodeData(ticket));
                    }

                    ticket.setUpdatedAt(clock.instant());

                    // Store regeneration info in metadata
                    if (ticket.getMetadata() == null) {
                        ticket.setMetadata(new java.util.HashMap<>());
                    }
                    ticket.getMetadata().put("qrRegeneratedAt", clock.instant().toString());

                    return ticketRepository.save(ticket);
                })
                .doOnSuccess(t -> log.info("QR code regenerated for ticket: {}", ticketId));
    }

    @Override
    @Transactional
    public Mono<BulkOperationResponse> bulkCancelTickets(List<String> ticketIds, String reason, String processedBy) {
        log.info("Bulk cancelling {} tickets by: {}", ticketIds.size(), processedBy);

        List<String> errors = new ArrayList<>();

        return Flux.fromIterable(ticketIds)
                .flatMap(ticketId ->
                    ticketForCaller(ticketId)
                        .onErrorResume(refused -> Mono.empty())
                        .flatMap(ticket -> {
                            // The same table the single-ticket path uses, so a
                            // bulk cancel cannot admit what a single one refuses.
                            if (TicketStateMachine.next(ticket.getStatus(), Action.CANCEL).isEmpty()) {
                                errors.add("Ticket " + ticketId + " cannot be cancelled (status: " + ticket.getStatus() + ")");
                                return Mono.just(false);
                            }

                            ticket.setStatus(TicketStatus.CANCELLED);
                            ticket.setCancelledAt(clock.instant());
                            ticket.setCancellationReason(reason);
                            ticket.setUpdatedAt(clock.instant());

                            // Store who cancelled it
                            if (ticket.getMetadata() == null) {
                                ticket.setMetadata(new java.util.HashMap<>());
                            }
                            ticket.getMetadata().put("cancelledBy", processedBy);
                            ticket.getMetadata().put("bulkCancellation", true);

                            return ticketRepository.save(ticket)
                                    .map(t -> true)
                                    .onErrorResume(e -> {
                                        errors.add("Failed to cancel ticket " + ticketId + ": " + e.getMessage());
                                        return Mono.just(false);
                                    });
                        })
                        .switchIfEmpty(Mono.defer(() -> {
                            errors.add("Ticket not found: " + ticketId);
                            return Mono.just(false);
                        }))
                )
                .collectList()
                .map(results -> {
                    int processedCount = (int) results.stream().filter(b -> b).count();
                    int failedCount = results.size() - processedCount;

                    String message = String.format("Bulk cancel completed: %d processed, %d failed",
                            processedCount, failedCount);

                    log.info(message);

                    return BulkOperationResponse.partial(message, processedCount, failedCount, errors);
                });
    }

    private String generateQrCodeData(Ticket ticket) {
        // Generate a unique QR code identifier
        // In production, this would encode ticket validation URL or data
        return "QR-" + ticket.getTicketNumber() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateBarcodeData(Ticket ticket) {
        // Generate a unique barcode
        // In production, this would be a proper barcode format
        return "BC-" + ticket.getTicketNumber() + "-" + clock.millis();
    }

    /**
     * The ticket the caller is entitled to act on, or {@code TICKET_UNKNOWN}. For the ADMIN-only
     * ticket-admin mutations, which have no upstream guard to rely on the way the resolver's
     * {@code reads.*ForCaller} methods give other services.
     */
    private Mono<Ticket> ticketForCaller(String id) {
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                ticketRepository.findById(id),
                organizationIds -> ticketRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                ErrorCode.TICKET_UNKNOWN,
                "ticket " + id));
    }
}
