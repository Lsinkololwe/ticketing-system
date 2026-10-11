package com.pml.booking.ticket;

import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.dto.NotificationReceipt;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.it.BookingFixture.World;
import com.pml.booking.service.TicketResendService;
import com.pml.booking.web.graphql.dto.ResendTicketResult;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.TestClock;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.pml.booking.it.BookingFixture.asAdmin;
import static com.pml.booking.it.BookingFixture.asCustomer;
import static com.pml.booking.it.BookingFixture.asOrganizer;
import static com.pml.booking.it.BookingFixture.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sending a ticket again reaches only the ticket's current holder, only for people entitled to ask,
 * is capped per ticket and per caller in real Redis, and never carries the QR or a raw contact.
 */
@Tag("L2")
@Tag("ET-TKT-002")
@DisplayName("ET-TKT-002 · resendTicket: holder or the event's attendee readers, rate limited, verified contact only")
class TicketResendTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    private World world;
    private TicketResendService resends;
    private String eventId;
    private String holder;

    @BeforeAll
    static void connect() {
        client = BookingFixture.newClient(MongoReplicaSet.connectionString());
        template = BookingFixture.template(client, "booking_resend");
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        world = new World();
        eventId = "ev-" + UUID.randomUUID();
        holder = "holder-" + UUID.randomUUID();
        world.event(eventId, "org-1");
        when(world.identity.notifyUser(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(NotificationReceipt.queued("WHATSAPP", "+260 97* *** *23", 1)));
        resends = new TicketResendService(template, world.access, world.identity, BookingFixture.limiter(), TestClock.frozenAt(NOW), noCurrentEvent());
    }

    private Ticket ticket(TicketStatus status) {
        return template.save(Ticket.builder().id(UUID.randomUUID().toString()).ticketNumber("TKT-" + UUID.randomUUID().toString().substring(0, 8))
                .eventId(eventId).eventTitle("Event").buyerId(holder).organizationId("org-1").price(BigDecimal.TEN).status(status)
                .qrCode("SECRET-QR-PAYLOAD").buyerEmail("holder@example.com").buyerPhone("+260971234567")
                .bookingNumber("BK-2026-000001").build()).block();
    }

    @Test
    @DisplayName("the holder's resend goes to the holder's account, without the QR or any raw contact")
    void holderResendCarriesNoSecret() {
        Ticket ticket = ticket(TicketStatus.ISSUED);

        ResendTicketResult result = asCustomer(holder, resends.resend(ticket.getId())).block();

        assertThat(result.ticketId()).isEqualTo(ticket.getId());
        assertThat(result.destination()).doesNotContain("971234567").doesNotContain("@example.com");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> params = ArgumentCaptor.forClass(Map.class);
        verify(world.identity).notifyUser(eq("ticket.resend"), anyString(), eq(holder), params.capture());
        assertThat(params.getValue().toString()).doesNotContain("SECRET-QR-PAYLOAD").doesNotContain("holder@example.com")
                .doesNotContain("971234567");
        assertThat(params.getValue()).containsKey("ticketNumber");
    }

    @Test
    @DisplayName("a stranger, and an organizer of another event, are told the ticket is unknown, as for an invented id")
    void strangersAreToldUnknown() {
        Ticket ticket = ticket(TicketStatus.ISSUED);
        world.event("ev-other", "org-2");
        world.grant("other-organizer", "ev-other");

        var stranger = refusal(asCustomer("stranger", resends.resend(ticket.getId())));
        var invented = refusal(asCustomer("stranger", resends.resend(UUID.randomUUID().toString())));
        var otherOrganizer = refusal(asOrganizer("other-organizer", "org-2", resends.resend(ticket.getId())));

        assertThat(stranger.errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(otherOrganizer.errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(invented.errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(stranger.details()).isEqualTo(invented.details());
        verify(world.identity, never()).notifyUser(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("an organizer of the event, or platform staff, may re-send, and it still goes to the holder")
    void entitledStaffResendToTheHolder() {
        Ticket ticket = ticket(TicketStatus.ISSUED);
        world.grant("event-organizer", eventId);

        asOrganizer("event-organizer", "org-1", resends.resend(ticket.getId())).block();
        asAdmin("admin-1", resends.resend(ticket.getId()), "ROLE_ADMIN").block();

        verify(world.identity, Mockito.times(2)).notifyUser(eq("ticket.resend"), anyString(), eq(holder), any());
    }

    @Test
    @DisplayName("only an issued ticket is sent again")
    void onlyIssued() {
        for (TicketStatus status : new TicketStatus[]{TicketStatus.REFUNDED, TicketStatus.CANCELLED, TicketStatus.REFUND_PENDING}) {
            Ticket ticket = ticket(status);
            assertThat(refusal(asCustomer(holder, resends.resend(ticket.getId()))).errorCode())
                    .as(status.name()).isEqualTo(ErrorCode.TICKET_STATE_INVALID);
        }
        verify(world.identity, never()).notifyUser(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("a ticket can be re-sent three times an hour; the fourth is refused with a retry hint")
    void perTicketLimit() {
        Ticket ticket = ticket(TicketStatus.ISSUED);
        for (int i = 0; i < TicketResendService_PER_TICKET; i++) {
            asCustomer(holder, resends.resend(ticket.getId())).block();
        }
        var refused = refusal(asCustomer(holder, resends.resend(ticket.getId())));
        assertThat(refused.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
        assertThat(refused.details()).containsKey("retryAfterSeconds");
        verify(world.identity, Mockito.times(TicketResendService_PER_TICKET)).notifyUser(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("one caller can trigger thirty re-sends an hour across tickets")
    void perCallerLimit() {
        String busy = "busy-organizer-" + UUID.randomUUID();
        world.grant(busy, eventId);
        for (int i = 0; i < 30; i++) {
            asOrganizer(busy, "org-1", resends.resend(ticket(TicketStatus.ISSUED).getId())).block();
        }
        assertThat(refusal(asOrganizer(busy, "org-1", resends.resend(ticket(TicketStatus.ISSUED).getId()))).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("a ticket that moved to a new holder is sent to the new holder, never the old one")
    void followsTheTicketToItsNewHolder() {
        Ticket ticket = ticket(TicketStatus.ISSUED);
        ticket.setBuyerId("new-holder");
        template.save(ticket).block();

        assertThat(refusal(asCustomer(holder, resends.resend(ticket.getId()))).errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        asCustomer("new-holder", resends.resend(ticket.getId())).block();
        verify(world.identity).notifyUser(eq("ticket.resend"), anyString(), eq("new-holder"), any());
    }

    @Test
    @DisplayName("identity being down is a clear refusal, and a recipient with no verified contact is reported as such")
    void identityOutcomes() {
        Ticket ticket = ticket(TicketStatus.ISSUED);
        when(world.identity.notifyUser(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new IllegalStateException("identity down")));
        assertThat(refusal(asCustomer(holder, resends.resend(ticket.getId()))).errorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_CHANNEL_UNAVAILABLE);

        when(world.identity.notifyUser(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(new NotificationReceipt("NO_VERIFIED_CONTACT", null, null, 0)));
        ResendTicketResult none = asCustomer(holder, resends.resend(ticket.getId())).block();
        assertThat(none.status()).isEqualTo("NO_VERIFIED_CONTACT");
        assertThat(none.destination()).isNull();
    }

    private static final int TicketResendService_PER_TICKET = 3;

    /** Catalog knows no event, so the ticket keeps what it carries. */
    private static com.pml.booking.service.CurrentEventDetails noCurrentEvent() {
        com.pml.booking.infrastructure.client.CatalogServiceClient catalog =
                org.mockito.Mockito.mock(com.pml.booking.infrastructure.client.CatalogServiceClient.class);
        org.mockito.Mockito.when(catalog.getEventById(org.mockito.ArgumentMatchers.any())).thenReturn(reactor.core.publisher.Mono.empty());
        return new com.pml.booking.service.CurrentEventDetails(catalog, java.time.Clock.systemUTC());
    }
}
