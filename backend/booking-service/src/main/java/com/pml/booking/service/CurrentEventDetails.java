package com.pml.booking.service;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.shared.dto.EventSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An event's name and date as catalog holds them now.
 *
 * <p>The event is catalog's. Tickets, transfers and the messages sent about them show its current
 * name and date, so a rename or a reschedule reaches the buyer without anything being rewritten.
 * Nothing is copied onto the ticket. A lookup is kept for {@value #TTL_SECONDS} seconds, so a list of
 * tickets for one event asks catalog once. When catalog cannot answer the stored value, if any, is
 * used and nothing is cached, so the next read asks again.
 */
@Slf4j
@Component
public class CurrentEventDetails {

    static final long TTL_SECONDS = 30;
    private static final int MAX_ENTRIES = 1_000;
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);

    /** What a ticket shows of its event. */
    public record Details(String title, Instant startsAt) {
    }

    private record Entry(Details details, Instant expiresAt) {
    }

    private final CatalogServiceClient catalog;
    private final Clock clock;
    private final ConcurrentMap<String, Entry> recent = new ConcurrentHashMap<>();

    public CurrentEventDetails(CatalogServiceClient catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    /** The event's current name and date; empty when the id is blank, unknown, or catalog is down. */
    public Mono<Details> of(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Mono.empty();
        }
        Entry kept = recent.get(eventId);
        if (kept != null && kept.expiresAt().isAfter(clock.instant())) {
            return Mono.just(kept.details());
        }
        return catalog.getEventById(eventId)
                .map(CurrentEventDetails::detailsOf)
                .doOnNext(details -> remember(eventId, details))
                .onErrorResume(error -> {
                    log.warn("Event lookup failed for {}: {}", eventId, error.toString());
                    return Mono.empty();
                });
    }

    /**
     * The ticket with its event's current name and date set, in memory only. A ticket whose event
     * cannot be had keeps what it carries.
     */
    public Mono<Ticket> current(Ticket ticket) {
        return of(ticket.getEventId())
                .map(details -> {
                    if (details.title() != null && !details.title().isBlank()) {
                        ticket.setEventTitle(details.title());
                    }
                    if (details.startsAt() != null) {
                        ticket.setEventDate(details.startsAt().toString());
                    }
                    return ticket;
                })
                .defaultIfEmpty(ticket);
    }

    public Flux<Ticket> current(Flux<Ticket> tickets) {
        return tickets.concatMap(this::current);
    }

    private void remember(String eventId, Details details) {
        if (recent.size() >= MAX_ENTRIES) {
            recent.clear();
        }
        recent.put(eventId, new Entry(details, clock.instant().plus(TTL)));
    }

    private static Details detailsOf(EventSummaryDto event) {
        return new Details(event.getTitle(), event.getStartDate());
    }
}
