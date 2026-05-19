package com.pml.booking.event.listener;

import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.RefundRequestRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.CommissionService;
import com.pml.booking.service.EscrowService;
import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Catalog Event Listener
 *
 * Handles events from the Catalog Service received via Azure Service Bus:
 * 1. EventPublished → Create escrow account for the event
 * 2. EventCompleted → Lock escrow, start 7-day hold period
 * 3. EventCancelled → Initiate automatic refunds
 * 4. EventRescheduled → Open 7-day refund window
 *
 * These events are consumed from Azure Service Bus topics via Spring Cloud Stream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogEventListener {

    private final EscrowService escrowService;
    private final CommissionService commissionService;
    private final TicketRepository ticketRepository;
    private final RefundRequestRepository refundRequestRepository;

    /**
     * Handle event publication.
     * Creates the escrow account that will hold organizer funds.
     *
     * Note: This would normally be triggered by Azure Service Bus message,
     * configured via Spring Cloud Stream bindings.
     */
    public Mono<Void> onEventPublished(
            String eventId,
            String eventTitle,
            String organizerId,
            String organizerName,
            LocalDateTime eventDateTime
    ) {
        log.info("Processing EventPublished for event: {}", eventId);

        return escrowService.createEscrowAccount(
                eventId,
                eventTitle,
                organizerId,
                organizerName,
                eventDateTime
        ).then()
                .doOnSuccess(v -> log.info("Escrow account created for event: {}", eventId))
                .doOnError(error -> log.error("Failed to create escrow for event: {}", eventId, error));
    }

    /**
     * Handle event completion.
     * Locks the escrow and starts the 7-day hold period.
     */
    public Mono<Void> onEventCompleted(String eventId) {
        log.info("Processing EventCompleted for event: {}", eventId);

        return escrowService.lockEscrow(eventId)
                .then()
                .doOnSuccess(v -> log.info("Escrow locked for event: {}", eventId))
                .doOnError(error -> log.error("Failed to lock escrow for event: {}", eventId, error));
    }

    /**
     * Handle event cancellation.
     * Initiates automatic refunds for all tickets.
     */
    public Mono<Long> onEventCancelled(String eventId, String reason) {
        log.info("Processing EventCancelled for event: {}", eventId);

        return ticketRepository.findByEventIdAndStatus(eventId, TicketStatus.PURCHASED)
                .flatMap(ticket -> initiateAutomaticRefund(ticket, reason))
                .count()
                .doOnSuccess(count -> log.info("Initiated {} automatic refunds for event: {}", count, eventId))
                .doOnError(error -> log.error("Failed to process event cancellation: {}", eventId, error));
    }

    /**
     * Handle event rescheduling.
     * Opens a 7-day refund window for ticket holders.
     */
    public Mono<Void> onEventRescheduled(
            String eventId,
            LocalDateTime originalDate,
            LocalDateTime newDate
    ) {
        log.info("Processing EventRescheduled for event: {} ({} -> {})",
                eventId, originalDate, newDate);

        // Mark tickets as eligible for refund due to rescheduling
        return ticketRepository.findByEventIdAndStatus(eventId, TicketStatus.PURCHASED)
                .flatMap(ticket -> {
                    if (ticket.getMetadata() == null) {
                        ticket.setMetadata(new java.util.HashMap<>());
                    }
                    ticket.getMetadata().put("refundEligibleDueToReschedule", true);
                    ticket.getMetadata().put("refundEligibleUntil",
                            LocalDateTime.now().plusDays(7).toString());
                    ticket.getMetadata().put("originalEventDate", originalDate.toString());
                    ticket.getMetadata().put("newEventDate", newDate.toString());
                    return ticketRepository.save(ticket);
                })
                .then()
                .doOnSuccess(v -> log.info("Refund window opened for event: {}", eventId))
                .doOnError(error -> log.error("Failed to process event reschedule: {}", eventId, error));
    }

    /**
     * Initiate automatic refund for a ticket.
     */
    private Mono<RefundRequest> initiateAutomaticRefund(Ticket ticket, String reason) {
        log.debug("Initiating automatic refund for ticket: {}", ticket.getTicketNumber());

        RefundRequest refundRequest = RefundRequest.builder()
                .ticketId(ticket.getId())
                .eventId(ticket.getEventId())
                .buyerId(ticket.getBuyerId())
                .requestReason("EVENT_CANCELLED: " + reason)
                .refundAmount(ticket.getPrice())
                .currency(ticket.getCurrency())
                .status(RefundRequestStatus.PENDING)
                .isAutomatic(true)
                .requestedAt(java.time.Instant.now())
                .build();

        return refundRequestRepository.save(refundRequest)
                .doOnSuccess(r -> log.debug("Automatic refund request created: {}", r.getId()));
    }
}
