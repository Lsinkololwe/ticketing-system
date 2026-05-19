package com.pml.booking.event.listener;

import com.pml.booking.event.domain.PaymentCompletedEvent;
import com.pml.booking.event.domain.PaymentFailedEvent;
import com.pml.booking.event.domain.TicketPurchasedEvent;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.CommissionService;
import com.pml.booking.service.EscrowService;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Payment Event Listener
 *
 * Handles payment domain events and orchestrates the financial operations:
 * 1. PaymentCompleted → Credit escrow, create commission, update ticket, publish TicketPurchased
 * 2. PaymentFailed → Update ticket status to payment failed
 *
 * Uses Spring Modulith's @ApplicationModuleListener for reliable event processing
 * with automatic retry and dead-letter handling via Event Publication Registry.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final TicketRepository ticketRepository;
    private final EscrowService escrowService;
    private final CommissionService commissionService;
    private final AccountingService accountingService;
    private final ApplicationEventPublisher eventPublisher;
    private final CatalogServiceClient catalogServiceClient;

    private static final Duration BLOCK_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Handle successful payment completion.
     *
     * <p>This is the critical financial flow. Uses blocking pattern with Modulith
     * to ensure atomic execution - if any step fails, the event will be retried
     * via the Event Publication Registry.</p>
     *
     * <h2>Steps</h2>
     * <ol>
     *   <li>Create pending commission record (Two-Stage Model)</li>
     *   <li>Credit net amount to event's escrow account</li>
     *   <li>Record accounting journal entries (double-entry)</li>
     *   <li>Commit reserved inventory to sold (atomic)</li>
     *   <li>Update ticket status to PURCHASED</li>
     *   <li>Publish TicketPurchasedEvent for external systems</li>
     * </ol>
     *
     * <p><b>CRITICAL</b>: Uses blocking .block() with timeout to ensure Modulith
     * can track event completion and retry on failure. Fire-and-forget .subscribe()
     * would leave the event marked as incomplete, causing retries.</p>
     */
    @ApplicationModuleListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Processing PaymentCompletedEvent for ticket: {}", event.ticketId());

        try {
            Ticket ticket = processPaymentCompletion(event)
                    .block(BLOCK_TIMEOUT);

            if (ticket != null) {
                // Publish TicketPurchasedEvent (after successful processing)
                publishTicketPurchasedEvent(ticket, event);
                log.info("Payment processing completed for ticket: {}", ticket.getTicketNumber());
            }
        } catch (Exception e) {
            log.error("Failed to process payment completion for ticket: {}", event.ticketId(), e);
            // Re-throw to trigger Modulith retry via Event Publication Registry
            throw new RuntimeException("Payment completion processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process payment completion atomically.
     *
     * @param event Payment completed event
     * @return Updated ticket
     */
    private Mono<Ticket> processPaymentCompletion(PaymentCompletedEvent event) {
        return ticketRepository.findById(event.ticketId())
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Ticket not found: " + event.ticketId())))
                .flatMap(ticket -> {
                    // Step 1: Create pending commission
                    return commissionService.createPendingCommission(
                                    ticket.getId(),
                                    ticket.getEventId(),
                                    getOrganizerId(ticket),
                                    ticket.getOrganizationId(),
                                    ticket.getPrice()
                            ).flatMap(commission -> {
                                // Step 2: Credit escrow with net amount
                                BigDecimal netAmount = commissionService.calculateNetAmount(ticket.getPrice());
                                return escrowService.creditEscrow(
                                        ticket.getEventId(),
                                        netAmount,
                                        ticket.getId(),
                                        event.paymentIntentId(),
                                        "Ticket sale: " + ticket.getTicketNumber()
                                );
                            }).flatMap(escrow -> {
                                // Step 3: Record accounting entries
                                BigDecimal commission = commissionService.calculateCommission(ticket.getPrice());
                                BigDecimal net = commissionService.calculateNetAmount(ticket.getPrice());
                                return accountingService.recordTicketSale(
                                        event.paymentIntentId(),
                                        ticket.getId(),
                                        ticket.getEventId(),
                                        ticket.getPrice(),
                                        net,
                                        commission,
                                        null,
                                        ticket.getCurrency()
                                ).thenReturn(escrow);
                            }).flatMap(escrow -> {
                                // Step 4: Commit reserved inventory to sold
                                String tierId = ticket.getTicketTierId();
                                String reservationId = ticket.getReservationId();
                                if (tierId != null && reservationId != null) {
                                    return catalogServiceClient.commitInventoryToSold(tierId, 1, reservationId)
                                            .doOnSuccess(result -> {
                                                if (result.success()) {
                                                    log.debug("Inventory committed to sold for tier {}", tierId);
                                                } else {
                                                    log.warn("Inventory commit failed for tier {}: {}",
                                                            tierId, result.errorMessage());
                                                }
                                            })
                                            .thenReturn(escrow);
                                }
                                return Mono.just(escrow);
                            })
                            .flatMap(escrow -> {
                                // Step 5: Update ticket status
                                ticket.setStatus(TicketStatus.PURCHASED);
                                ticket.setPaymentReference(event.providerTransactionId());
                                ticket.setCommissionRate(commissionService.getCommissionRate());
                                ticket.setCommissionAmount(commissionService.calculateCommission(ticket.getPrice()));
                                ticket.setNetAmount(commissionService.calculateNetAmount(ticket.getPrice()));
                                ticket.setPurchaseDate(java.time.LocalDateTime.now());

                                Ticket.PaymentInfo paymentInfo = Ticket.PaymentInfo.builder()
                                        .paymentId(event.paymentIntentId())
                                        .transactionId(event.providerTransactionId())
                                        .paymentMethod(event.paymentProvider())
                                        .amount(event.amount())
                                        .currency(event.currency())
                                        .status(com.pml.shared.constants.TicketPaymentStatus.COMPLETED)
                                        .paymentDate(java.time.LocalDateTime.now())
                                        .build();
                                ticket.setPaymentInfo(paymentInfo);

                                return ticketRepository.save(ticket);
                            });
                });
    }

    /**
     * Publish TicketPurchasedEvent for external systems.
     */
    private void publishTicketPurchasedEvent(Ticket ticket, PaymentCompletedEvent event) {
        TicketPurchasedEvent ticketEvent = new TicketPurchasedEvent(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getEventId(),
                ticket.getEventTitle(),
                ticket.getBuyerId(),
                ticket.getBuyerName() != null ? ticket.getBuyerName() : "Unknown",
                ticket.getBuyerEmail(),
                ticket.getBuyerPhone() != null ? ticket.getBuyerPhone() : "",
                getOrganizerId(ticket),
                ticket.getTicketCategoryCode(),
                ticket.getTicketCategoryName(),
                1,
                ticket.getPrice(),
                ticket.getPrice(),
                ticket.getCommissionAmount() != null ? ticket.getCommissionAmount() : commissionService.calculateCommission(ticket.getPrice()),
                ticket.getCommissionRate() != null ? ticket.getCommissionRate() : commissionService.getCommissionRate(),
                ticket.getNetAmount() != null ? ticket.getNetAmount() : commissionService.calculateNetAmount(ticket.getPrice()),
                ticket.getCurrency(),
                event.paymentProvider(),
                event.providerTransactionId(),
                event.paymentIntentId()
        );
        eventPublisher.publishEvent(ticketEvent);
    }

    /**
     * Handle payment failure.
     *
     * <p>Updates ticket status to PAYMENT_FAILED and releases reserved inventory.
     * Uses blocking pattern for Modulith event tracking.</p>
     */
    @ApplicationModuleListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Processing PaymentFailedEvent for ticket: {}", event.ticketId());

        try {
            Ticket ticket = ticketRepository.findById(event.ticketId())
                    .flatMap(t -> {
                        // Release reserved inventory
                        String tierId = t.getTicketTierId();
                        String reservationId = t.getReservationId();
                        Mono<Void> releaseInventory = Mono.empty();

                        if (tierId != null && reservationId != null) {
                            releaseInventory = catalogServiceClient.releaseInventory(tierId, 1, reservationId)
                                    .doOnSuccess(result -> {
                                        if (result.success()) {
                                            log.debug("Released inventory for failed payment: tier={}", tierId);
                                        } else {
                                            log.warn("Inventory release failed for tier {}: {}",
                                                    tierId, result.errorMessage());
                                        }
                                    })
                                    .then();
                        }

                        return releaseInventory.then(Mono.defer(() -> {
                            // Update ticket status
                            t.setStatus(TicketStatus.PAYMENT_FAILED);
                            t.setPaymentReference(event.paymentIntentId());

                            Ticket.PaymentInfo paymentInfo = Ticket.PaymentInfo.builder()
                                    .paymentId(event.paymentIntentId())
                                    .paymentMethod(event.paymentProvider())
                                    .amount(event.amount())
                                    .status(com.pml.shared.constants.TicketPaymentStatus.FAILED)
                                    .build();
                            t.setPaymentInfo(paymentInfo);

                            return ticketRepository.save(t);
                        }));
                    })
                    .block(BLOCK_TIMEOUT);

            if (ticket != null) {
                log.info("Ticket marked as payment failed: {}", ticket.getTicketNumber());
            }
        } catch (Exception e) {
            log.error("Failed to process payment failure for ticket: {}", event.ticketId(), e);
            throw new RuntimeException("Payment failure processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get organizer ID from ticket.
     * In a full implementation, this would come from the event/catalog service.
     */
    private String getOrganizerId(Ticket ticket) {
        // This should be populated when the ticket is created
        // For now, use metadata or a default
        if (ticket.getMetadata() != null && ticket.getMetadata().containsKey("organizerId")) {
            return ticket.getMetadata().get("organizerId").toString();
        }
        return "UNKNOWN";
    }
}
