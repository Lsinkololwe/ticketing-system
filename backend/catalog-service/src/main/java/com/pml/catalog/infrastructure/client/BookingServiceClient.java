package com.pml.catalog.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import com.pml.shared.security.InternalServiceWebClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Booking Service Client
 *
 * <p>Reads booking's ticket and payout state for the two catalog decisions that must not trust
 * catalog's own denormalised counters: the unpublish check and the cancellation check. Both
 * calls let a failure propagate rather than defaulting to an answer: these gate money and
 * inventory, so an unreachable booking-service must refuse the write and let the calling
 * Temporal activity retry rather than have a blip quietly read as "nothing sold" or "no payout
 * in flight".
 */
@Slf4j
@Component
public class BookingServiceClient {

    private final WebClient bookingServiceWebClient;

    public BookingServiceClient(InternalServiceWebClients clients,
                 @Value("${services.booking.url:http://localhost:8082}") String baseUrl) {
        this.bookingServiceWebClient = clients.to(baseUrl);
    }

    /**
     * The event's sold, non-refunded ticket count — booking's tickets are the authority, not
     * catalog's own denormalised {@code Event.soldTickets}. The unpublish check reads it.
     */
    public Mono<Long> soldTicketCount(String eventId) {
        return bookingServiceWebClient.get()
                .uri("/api/internal/tickets/sold-count/by-event/{eventId}", eventId)
                .retrieve()
                .bodyToMono(Long.class)
                .doOnError(e -> log.error("Failed to read sold ticket count for event {}: {}", eventId, e.getMessage()));
    }

    /**
     * Whether the event has a payout request booking has not yet resolved; an event with one
     * cannot be cancelled.
     */
    public Mono<Boolean> hasOpenPayoutRequest(String eventId) {
        return bookingServiceWebClient.get()
                .uri("/api/internal/payouts/by-event/{eventId}/open", eventId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .doOnError(e -> log.error("Failed to read payout state for event {}: {}", eventId, e.getMessage()));
    }
}
