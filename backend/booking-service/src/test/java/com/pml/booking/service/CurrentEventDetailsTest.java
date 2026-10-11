package com.pml.booking.service;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.shared.dto.EventSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One lookup serves a list of tickets for the same event for a short while, a failure is never
 * kept, and a ticket is only changed in memory. Flat methods: F-055.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Current event details are looked up once per event for a short while")
class CurrentEventDetailsTest {

    private static final String EVENT = "6aca0c803ff088c1f746c8fa";

    private CatalogServiceClient catalog;
    private MovableClock clock;
    private CurrentEventDetails details;

    @BeforeEach
    void setUp() {
        catalog = Mockito.mock(CatalogServiceClient.class);
        clock = new MovableClock(Instant.parse("2026-10-10T12:00:00Z"));
        details = new CurrentEventDetails(catalog, clock);
    }

    private static EventSummaryDto event(String title) {
        return EventSummaryDto.builder().title(title).startDate(Instant.parse("2026-12-20T17:00:00Z")).build();
    }

    @Test
    @DisplayName("a second read inside the window does not ask catalog again; one after it does")
    void keptForTheWindowOnly() {
        Mockito.when(catalog.getEventById(EVENT)).thenReturn(Mono.just(event("Jazz")));

        assertThat(details.of(EVENT).block().title()).isEqualTo("Jazz");
        assertThat(details.of(EVENT).block().title()).isEqualTo("Jazz");
        Mockito.verify(catalog, Mockito.times(1)).getEventById(EVENT);

        clock.advance(Duration.ofSeconds(CurrentEventDetails.TTL_SECONDS + 1));
        Mockito.when(catalog.getEventById(EVENT)).thenReturn(Mono.just(event("Jazz (renamed)")));
        assertThat(details.of(EVENT).block().title()).isEqualTo("Jazz (renamed)");
    }

    @Test
    @DisplayName("a failure is not kept, so the next read asks catalog again")
    void failureIsNotKept() {
        Mockito.when(catalog.getEventById(EVENT)).thenReturn(Mono.error(new IllegalStateException("down")));
        assertThat(details.of(EVENT).block()).isNull();

        Mockito.when(catalog.getEventById(EVENT)).thenReturn(Mono.just(event("Jazz")));
        assertThat(details.of(EVENT).block().title()).isEqualTo("Jazz");
    }

    @Test
    @DisplayName("a ticket without an event id is left alone and catalog is not asked")
    void noEventNoLookup() {
        Ticket ticket = new Ticket();
        ticket.setEventTitle("kept");

        assertThat(details.current(ticket).block().getEventTitle()).isEqualTo("kept");
        Mockito.verifyNoInteractions(catalog);
    }

    /** A clock a test can move. */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
