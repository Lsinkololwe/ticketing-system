package com.pml.booking.service.impl;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.repository.dto.RevenueResult;
import com.pml.booking.repository.dto.SpentResult;
import com.pml.booking.service.TicketService;
import com.pml.booking.web.graphql.dto.AdminTicketUpdateInput;
import com.pml.booking.web.graphql.dto.BulkOperationResponse;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
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
    public Mono<Ticket> createTicket(Ticket ticket) {
        log.info("Creating new ticket for event: {}", ticket.getEventId());
        if (ticket.getTicketNumber() == null) {
            ticket.setTicketNumber(Ticket.generateTicketNumber());
        }
        ticket.setStatus(TicketStatus.PENDING_PAYMENT);
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setUpdatedAt(LocalDateTime.now());
        return ticketRepository.save(ticket)
                .doOnSuccess(t -> log.info("Ticket created: {}", t.getTicketNumber()));
    }

    @Override
    public Mono<Ticket> updateTicket(String id, Ticket ticket) {
        return ticketRepository.findById(id)
                .flatMap(existing -> {
                    existing.setEventTitle(ticket.getEventTitle());
                    existing.setEventDate(ticket.getEventDate());
                    existing.setTicketCategory(ticket.getTicketCategory());
                    existing.setPrice(ticket.getPrice());
                    existing.setStatus(ticket.getStatus());
                    existing.setBuyerName(ticket.getBuyerName());
                    existing.setBuyerEmail(ticket.getBuyerEmail());
                    existing.setBuyerPhone(ticket.getBuyerPhone());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return ticketRepository.save(existing);
                });
    }

    @Override
    public Mono<Ticket> validateTicket(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .flatMap(ticket -> {
                    if (ticket.getStatus() == TicketStatus.PURCHASED ||
                        ticket.getStatus() == TicketStatus.CONFIRMED) {
                        ticket.setStatus(TicketStatus.VALIDATED);
                        ticket.setValidatedAt(LocalDateTime.now());
                        ticket.setUpdatedAt(LocalDateTime.now());
                        return ticketRepository.save(ticket);
                    }
                    return Mono.error(new IllegalStateException(
                            "Ticket cannot be validated. Current status: " + ticket.getStatus()));
                })
                .doOnSuccess(t -> log.info("Ticket validated: {}", ticketNumber));
    }

    @Override
    public Mono<Ticket> useTicket(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .flatMap(ticket -> {
                    if (ticket.getStatus() == TicketStatus.VALIDATED) {
                        ticket.setStatus(TicketStatus.USED);
                        ticket.setUsedAt(LocalDateTime.now());
                        ticket.setUpdatedAt(LocalDateTime.now());
                        return ticketRepository.save(ticket);
                    }
                    return Mono.error(new IllegalStateException(
                            "Ticket cannot be used. Current status: " + ticket.getStatus()));
                })
                .doOnSuccess(t -> log.info("Ticket used: {}", ticketNumber));
    }

    @Override
    public Mono<Ticket> refundTicket(String ticketNumber, String reason, String processedBy) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .flatMap(ticket -> {
                    if (ticket.getStatus() == TicketStatus.PURCHASED ||
                        ticket.getStatus() == TicketStatus.CONFIRMED ||
                        ticket.getStatus() == TicketStatus.VALIDATED) {
                        ticket.setStatus(TicketStatus.REFUNDED);
                        ticket.setRefundedAt(LocalDateTime.now());
                        ticket.setRefundReason(reason);
                        ticket.setUpdatedAt(LocalDateTime.now());
                        return ticketRepository.save(ticket);
                    }
                    return Mono.error(new IllegalStateException(
                            "Ticket cannot be refunded. Current status: " + ticket.getStatus()));
                })
                .doOnSuccess(t -> log.info("Ticket refunded: {}", ticketNumber));
    }

    @Override
    public Mono<Ticket> cancelTicket(String ticketNumber, String reason, String processedBy) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .flatMap(ticket -> {
                    if (ticket.getStatus() != TicketStatus.USED &&
                        ticket.getStatus() != TicketStatus.REFUNDED &&
                        ticket.getStatus() != TicketStatus.CANCELLED) {
                        ticket.setStatus(TicketStatus.CANCELLED);
                        ticket.setCancelledAt(LocalDateTime.now());
                        ticket.setCancellationReason(reason);
                        ticket.setUpdatedAt(LocalDateTime.now());
                        return ticketRepository.save(ticket);
                    }
                    return Mono.error(new IllegalStateException(
                            "Ticket cannot be cancelled. Current status: " + ticket.getStatus()));
                })
                .doOnSuccess(t -> log.info("Ticket cancelled: {}", ticketNumber));
    }

    @Override
    public Mono<Ticket> transferTicket(String ticketId, String newBuyerId, String reason) {
        return ticketRepository.findById(ticketId)
                .flatMap(ticket -> {
                    if (ticket.getStatus() == TicketStatus.PURCHASED ||
                        ticket.getStatus() == TicketStatus.CONFIRMED) {
                        ticket.setOriginalBuyerId(ticket.getBuyerId());
                        ticket.setTransferredToId(newBuyerId);
                        ticket.setBuyerId(newBuyerId);
                        ticket.setTransferredAt(LocalDateTime.now());
                        ticket.setTransferReason(reason);
                        ticket.setUpdatedAt(LocalDateTime.now());
                        return ticketRepository.save(ticket);
                    }
                    return Mono.error(new IllegalStateException(
                            "Ticket cannot be transferred. Current status: " + ticket.getStatus()));
                })
                .doOnSuccess(t -> log.info("Ticket transferred: {} to {}", ticketId, newBuyerId));
    }

    @Override
    public Mono<Void> deleteTicket(String id) {
        return ticketRepository.deleteById(id);
    }

    @Override
    public Mono<Long> countByEventId(String eventId) {
        return ticketRepository.countByEventId(eventId);
    }

    @Override
    public Mono<Long> countByBuyerId(String buyerId) {
        return ticketRepository.countByBuyerId(buyerId);
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

        return ticketRepository.findById(ticketId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket not found: " + ticketId)))
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

                    ticket.setUpdatedAt(LocalDateTime.now());
                    return ticketRepository.save(ticket);
                })
                .doOnSuccess(t -> log.info("Admin updated ticket: {}", ticketId));
    }

    @Override
    @Transactional
    public Mono<Ticket> regenerateTicketQrCode(String ticketId) {
        log.info("Regenerating QR code for ticket: {}", ticketId);

        return ticketRepository.findById(ticketId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Ticket not found: " + ticketId)))
                .flatMap(ticket -> {
                    // Only allow QR regeneration for active tickets
                    if (ticket.getStatus() == TicketStatus.CANCELLED ||
                        ticket.getStatus() == TicketStatus.REFUNDED ||
                        ticket.getStatus() == TicketStatus.USED) {
                        return Mono.error(new IllegalStateException(
                                "Cannot regenerate QR code for ticket with status: " + ticket.getStatus()));
                    }

                    // Generate new QR code (simplified - in production this would call a QR service)
                    String newQrCode = generateQrCodeData(ticket);
                    ticket.setQrCode(newQrCode);

                    // Also regenerate barcode if present
                    if (ticket.getBarcode() != null) {
                        ticket.setBarcode(generateBarcodeData(ticket));
                    }

                    ticket.setUpdatedAt(LocalDateTime.now());

                    // Store regeneration info in metadata
                    if (ticket.getMetadata() == null) {
                        ticket.setMetadata(new java.util.HashMap<>());
                    }
                    ticket.getMetadata().put("qrRegeneratedAt", LocalDateTime.now().toString());

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
                    ticketRepository.findById(ticketId)
                        .flatMap(ticket -> {
                            // Check if ticket can be cancelled
                            if (ticket.getStatus() == TicketStatus.USED ||
                                ticket.getStatus() == TicketStatus.REFUNDED ||
                                ticket.getStatus() == TicketStatus.CANCELLED) {
                                errors.add("Ticket " + ticketId + " cannot be cancelled (status: " + ticket.getStatus() + ")");
                                return Mono.just(false);
                            }

                            ticket.setStatus(TicketStatus.CANCELLED);
                            ticket.setCancelledAt(LocalDateTime.now());
                            ticket.setCancellationReason(reason);
                            ticket.setUpdatedAt(LocalDateTime.now());

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
        return "BC-" + ticket.getTicketNumber() + "-" + System.currentTimeMillis();
    }
}
