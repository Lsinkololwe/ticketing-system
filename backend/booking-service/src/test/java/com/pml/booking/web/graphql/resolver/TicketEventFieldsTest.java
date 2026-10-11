package com.pml.booking.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketTransfer;
import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.service.CurrentEventDetails;
import com.pml.shared.dto.EventSummaryDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A ticket shows its event's current name and date. Issuance copies neither, and a copy would
 * show the old name after a rename. A catalog outage never fails the list. Flat methods: F-055.
 */
@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("Ticket.eventTitle and eventDate are the event's current name and date")
class TicketEventFieldsTest {

    private static final String EVENT = "6aca0c803ff088c1f746c8fa";
    private static final Instant STARTS = Instant.parse("2026-12-20T17:00:00Z");

    private CatalogServiceClient catalog;
    private TicketEventFields fields;

    @BeforeEach
    void setUp() {
        catalog = Mockito.mock(CatalogServiceClient.class);
        fields = new TicketEventFields(new CurrentEventDetails(catalog, Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC)));
    }

    private static DgsDataFetchingEnvironment source(Object source) {
        DgsDataFetchingEnvironment dfe = Mockito.mock(DgsDataFetchingEnvironment.class);
        Mockito.when(dfe.getSource()).thenReturn(source);
        return dfe;
    }

    private static Ticket ticket() {
        Ticket ticket = new Ticket();
        ticket.setId("t1");
        ticket.setEventId(EVENT);
        return ticket;
    }

    @Test
    @DisplayName("the title is the event's name now, not a stored copy of an older one")
    void currentNameWinsOverAStoredCopy() {
        Mockito.when(catalog.getEventById(EVENT))
                .thenReturn(Mono.just(EventSummaryDto.builder().title("Lusaka Jazz Night (Late Show)").startDate(STARTS).build()));
        Ticket ticket = ticket();
        ticket.setEventTitle("Lusaka Jazz Night");

        assertThat(fields.eventTitle(source(ticket)).block()).isEqualTo("Lusaka Jazz Night (Late Show)");
    }

    @Test
    @DisplayName("the date is the event's start now, as an ISO instant, after a reschedule too")
    void currentDateWinsOverAStoredCopy() {
        Mockito.when(catalog.getEventById(EVENT))
                .thenReturn(Mono.just(EventSummaryDto.builder().title("Lusaka Jazz Night").startDate(STARTS).build()));
        Ticket ticket = ticket();
        ticket.setEventDate("2026-11-01T17:00:00Z");

        assertThat(fields.eventDate(source(ticket)).block()).isEqualTo("2026-12-20T17:00:00Z");
    }

    @Test
    @DisplayName("a catalog outage answers the stored value, and Unknown when there is none")
    void outageFallsBack() {
        Mockito.when(catalog.getEventById(EVENT)).thenReturn(Mono.error(new IllegalStateException("down")));
        Ticket stored = ticket();
        stored.setEventTitle("Lusaka Jazz Night");

        assertThat(fields.eventTitle(source(stored)).block()).isEqualTo("Lusaka Jazz Night");
        assertThat(fields.eventTitle(source(ticket())).block()).isEqualTo("Unknown");
        assertThat(fields.eventDate(source(ticket())).block()).isNull();
    }

    @Test
    @DisplayName("a transfer shows the event's current name, falling back to the one it recorded")
    void transferShowsTheCurrentName() {
        TicketTransfer transfer = new TicketTransfer();
        transfer.setEventId(EVENT);
        transfer.setEventTitle("Old Name");
        Mockito.when(catalog.getEventById(EVENT))
                .thenReturn(Mono.just(EventSummaryDto.builder().title("New Name").startDate(STARTS).build()));
        assertThat(fields.transferEventTitle(source(transfer)).block()).isEqualTo("New Name");

        Mockito.when(catalog.getEventById(EVENT)).thenReturn(Mono.empty());
        CurrentEventDetails cold = new CurrentEventDetails(catalog, Clock.systemUTC());
        assertThat(new TicketEventFields(cold).transferEventTitle(source(transfer)).block()).isEqualTo("Old Name");
    }
}
