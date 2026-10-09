package com.pml.booking.service;

import com.pml.booking.repository.TicketRepository;
import com.pml.shared.constants.TicketStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Applies catalog lifecycle facts to booking's escrow, from the event-finance workflow's activities.
 *
 * <ul>
 *   <li>{@code catalog.EventPublished} opens the event's escrow account.</li>
 *   <li>{@code catalog.EventRescheduled} opens the refund window.</li>
 * </ul>
 * Completion and cancellation are the event-finance and cancellation-refund workflows' own steps.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogLifecycleService {

    private final EscrowService escrowService;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final TicketRepository ticketRepository;

    /**
     * {@code catalog.EventPublished} · opens the escrow account that will hold the organization's funds.
     *
     * <p>Takes exactly the identifiers the envelope carries on the wire. Idempotent: an escrow that
     * already exists for the event is returned unchanged, so a redelivery opens nothing twice.</p>
     */
    public Mono<Void> onEventPublished(String eventId, String organizationId, Instant startsAt) {
        log.info("Processing EventPublished for event: {}", eventId);

        return escrowService.openEscrowForPublishedEvent(eventId, organizationId, startsAt)
                .then()
                .doOnSuccess(v -> log.info("Escrow account open for event: {}", eventId))
                .doOnError(error -> log.error("Failed to open escrow for event: {}", eventId, error));
    }

    /**
     * Handle event rescheduling.
     * Opens a 7-day refund window for ticket holders.
     */
    public Mono<Void> onEventRescheduled(
            String eventId,
            Instant originalDate,
            Instant newDate
    ) {
        log.info("Processing EventRescheduled for event: {} ({} -> {})",
                eventId, originalDate, newDate);

        // Mark tickets as eligible for refund due to rescheduling
        return ticketRepository.findByEventIdAndStatus(eventId, TicketStatus.ISSUED)
                .flatMap(ticket -> {
                    if (ticket.getMetadata() == null) {
                        ticket.setMetadata(new java.util.HashMap<>());
                    }
                    ticket.getMetadata().put("refundEligibleDueToReschedule", true);
                    ticket.getMetadata().put("refundEligibleUntil",
                            clock.instant().plus(Duration.ofDays(7)).toString());
                    ticket.getMetadata().put("originalEventDate", originalDate.toString());
                    ticket.getMetadata().put("newEventDate", newDate.toString());
                    return ticketRepository.save(ticket);
                })
                .then()
                .doOnSuccess(v -> log.info("Refund window opened for event: {}", eventId))
                .doOnError(error -> log.error("Failed to process event reschedule: {}", eventId, error));
    }
}
