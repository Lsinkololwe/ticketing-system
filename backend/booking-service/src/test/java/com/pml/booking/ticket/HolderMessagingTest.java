package com.pml.booking.ticket;

import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.booking.domain.model.HolderMessage;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.dto.NotificationReceipt;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.it.BookingFixture.World;
import com.pml.booking.service.HolderMessaging;
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
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.pml.booking.it.BookingFixture.asAdmin;
import static com.pml.booking.it.BookingFixture.asCustomer;
import static com.pml.booking.it.BookingFixture.asOrganizer;
import static com.pml.booking.it.BookingFixture.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An organizer reaches the holders of their own event's tickets, within caps, without ever seeing a contact. */
@Tag("L2")
@Tag("ET-TKT-002")
@DisplayName("ET-TKT-002 · messageTicketHolders: the event's organizer only, capped, ids out and counts back")
class HolderMessagingTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");
    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    private World world;
    private HolderMessaging messaging;
    private String eventId;
    private String organizer;

    @BeforeAll
    static void connect() {
        client = BookingFixture.newClient(MongoReplicaSet.connectionString());
        template = BookingFixture.template(client, "booking_holders");
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        world = new World();
        eventId = "ev-" + UUID.randomUUID();
        organizer = "organizer-" + UUID.randomUUID();
        world.event(eventId, "org-" + UUID.randomUUID());
        world.grant(organizer, eventId);
        when(world.identity.notifyUsers(anyString(), anyString(), anyCollection(), any()))
                .thenAnswer(call -> Mono.just(NotificationReceipt.queued("WHATSAPP", null, ((Collection<?>) call.getArgument(2)).size())));
        messaging = new HolderMessaging(template, world.access, world.identity, BookingFixture.limiter(), TestClock.frozenAt(NOW));
    }

    private void tickets(String eventId, String holder, TicketStatus status, int count, String tier, String transfer) {
        for (int i = 0; i < count; i++) {
            template.save(Ticket.builder().id(UUID.randomUUID().toString()).ticketNumber("T-" + UUID.randomUUID())
                    .eventId(eventId).buyerId(holder).ticketTierId(tier).status(status).price(BigDecimal.TEN)
                    .activeTransferId(transfer).buyerEmail(holder + "@example.com").build()).block();
        }
    }

    private long storedMessages() {
        return template.count(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("eventId").is(eventId)), HolderMessage.class).block();
    }

    private HolderMessage send(String subject, String body, HolderMessage.Segment segment, String tier) {
        return asOrganizer(organizer, "org-x", messaging.send(eventId, subject, body, segment, tier)).block();
    }

    @Test
    @DisplayName("the message goes to each distinct current holder once, as account ids, and the organizer is told only a count")
    void sendsToDistinctHolders() {
        tickets(eventId, "h1", TicketStatus.ISSUED, 3, "gold", null);
        tickets(eventId, "h2", TicketStatus.VALIDATED, 1, "gold", null);
        tickets(eventId, "h3", TicketStatus.REFUNDED, 1, "gold", null);
        tickets(eventId, "h4", TicketStatus.ISSUED, 1, "gold", "transfer-open");
        tickets("another-event", "h5", TicketStatus.ISSUED, 1, "gold", null);

        HolderMessage message = send("Doors open at 6", "Please bring the ticket on your phone and arrive early.", null, null);

        assertThat(message.getRecipientCount()).isEqualTo(2);
        assertThat(message.getDeliveredCount()).isEqualTo(2);
        assertThat(message.getStatus()).isEqualTo(HolderMessage.Status.SENT);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(world.identity).notifyUsers(anyString(), anyString(), ids.capture(), any());
        assertThat(ids.getValue()).containsExactlyInAnyOrder("h1", "h2");
        assertThat(ids.getValue().toString()).doesNotContain("@example.com");
        assertThat(message.toString()).doesNotContain("@example.com");
    }

    @Test
    @DisplayName("segment and tier narrow the audience; the audience count agrees with what is sent")
    void segmentsAndTiers() {
        tickets(eventId, "a1", TicketStatus.ISSUED, 1, "gold", null);
        tickets(eventId, "a2", TicketStatus.VALIDATED, 1, "gold", null);
        tickets(eventId, "a3", TicketStatus.ISSUED, 1, "silver", null);

        assertThat(asOrganizer(organizer, "org-x", messaging.audience(eventId, HolderMessage.Segment.ALL, null)).block()).isEqualTo(3);
        assertThat(asOrganizer(organizer, "org-x", messaging.audience(eventId, HolderMessage.Segment.ADMITTED, null)).block()).isEqualTo(1);
        assertThat(asOrganizer(organizer, "org-x", messaging.audience(eventId, HolderMessage.Segment.NOT_ADMITTED, "gold")).block()).isEqualTo(1);
        assertThat(send("Gold lounge", "The gold lounge opens at five o'clock sharp.", HolderMessage.Segment.NOT_ADMITTED, "gold")
                .getRecipientCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("an organizer of another event cannot read the audience, send, or read history; the refusal is the unknown-event one")
    void otherOrganizersAreRefused() {
        tickets(eventId, "h1", TicketStatus.ISSUED, 1, "gold", null);
        String intruder = "intruder-" + UUID.randomUUID();
        world.event("ev-intruder", "org-intruder");
        world.grant(intruder, "ev-intruder");

        var send = refusal(asOrganizer(intruder, "org-intruder", messaging.send(eventId, "Hello all", "A message the intruder wants to send.", null, null)));
        var audience = refusal(asOrganizer(intruder, "org-intruder", messaging.audience(eventId, null, null)));
        var history = refusal(asOrganizer(intruder, "org-intruder", messaging.history(eventId, null)));
        var invented = refusal(asOrganizer(intruder, "org-intruder", messaging.audience("ev-never", null, null)));

        assertThat(send.errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN);
        assertThat(audience.errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN);
        assertThat(history.errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN);
        assertThat(send.details()).isEqualTo(invented.details());
        verify(world.identity, never()).notifyUsers(anyString(), anyString(), anyCollection(), any());
        assertThat(storedMessages()).as("a refused send stores no message").isZero();
    }

    @Test
    @DisplayName("a customer holds no permission on the event, and platform staff may message any event")
    void customersAndStaff() {
        tickets(eventId, "h1", TicketStatus.ISSUED, 1, "gold", null);
        assertThat(refusal(asCustomer("h1", messaging.send(eventId, "Hello all", "A message from a ticket holder to others.", null, null)))
                .errorCode()).isEqualTo(ErrorCode.EVENT_UNKNOWN);
        assertThat(asAdmin("admin-1", messaging.send(eventId, "From support", "Support is looking into the issue.", null, null), "ROLE_ADMIN")
                .block().getRecipientCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("content limits: subject 3-80, body 10-500, control characters stripped, nothing stored on refusal")
    void contentLimits() {
        tickets(eventId, "h1", TicketStatus.ISSUED, 1, "gold", null);

        assertThat(refusal(asOrganizer(organizer, "o", messaging.send(eventId, "ab", "A perfectly fine message body.", null, null)))
                .errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(asOrganizer(organizer, "o", messaging.send(eventId, "Fine subject", "short", null, null)))
                .errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(asOrganizer(organizer, "o", messaging.send(eventId, "x".repeat(81), "A perfectly fine message body.", null, null)))
                .errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(asOrganizer(organizer, "o", messaging.send(eventId, "Fine subject", "y".repeat(501), null, null)))
                .errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(asOrganizer(organizer, "o", messaging.send(eventId, "\u0007\u0007\u0007", "A perfectly fine message body.", null, null)))
                .errorCode()).as("a subject of only control characters is empty").isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(storedMessages()).isZero();

        HolderMessage ok = send("Gate\u001b[31m change", "Use gate B tonight.\u0000 The main gate is closed.", null, null);
        assertThat(ok.getSubject()).isEqualTo("Gate[31m change");
        assertThat(ok.getBody()).doesNotContain("\u0000");
    }

    @Test
    @DisplayName("an event can be messaged three times a day; the fourth is refused with a retry hint and sends nothing")
    void perEventDailyCap() {
        tickets(eventId, "h1", TicketStatus.ISSUED, 1, "gold", null);
        for (int i = 0; i < 3; i++) {
            send("Update " + i, "Here is the update number " + i + " for you.", null, null);
        }
        var refused = refusal(asOrganizer(organizer, "o", messaging.send(eventId, "Update 4", "Here is the update number four.", null, null)));
        assertThat(refused.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
        assertThat(refused.details()).containsKey("retryAfterSeconds");
        verify(world.identity, org.mockito.Mockito.times(3)).notifyUsers(anyString(), anyString(), anyCollection(), any());
    }

    @Test
    @DisplayName("one sender can send ten a day across events")
    void perSenderDailyCap() {
        String prolific = "prolific-" + UUID.randomUUID();
        String organization = "org-p-" + UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            String event = "ev-p-" + UUID.randomUUID();
            world.event(event, organization);
            world.grant(prolific, event);
            tickets(event, "h" + i, TicketStatus.ISSUED, 1, "t", null);
            asOrganizer(prolific, organization, messaging.send(event, "Update", "A message that is long enough.", null, null)).block();
        }
        String last = "ev-p-last-" + UUID.randomUUID();
        world.event(last, organization);
        world.grant(prolific, last);
        tickets(last, "hx", TicketStatus.ISSUED, 1, "t", null);
        assertThat(refusal(asOrganizer(prolific, organization, messaging.send(last, "Update", "A message that is long enough.", null, null)))
                .errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("no holders matching the audience is refused, not sent as an empty message")
    void emptyAudience() {
        assertThat(refusal(asOrganizer(organizer, "o", messaging.send(eventId, "Hello all", "Nobody holds a ticket yet, sadly.", null, null)))
                .errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    @Test
    @DisplayName("an audience beyond the recipient cap is refused rather than sent in part")
    void oversizedAudience() {
        List<Ticket> many = new ArrayList<>();
        for (int i = 0; i <= 5000; i++) {
            many.add(Ticket.builder().id(UUID.randomUUID().toString()).ticketNumber("T-" + UUID.randomUUID()).eventId(eventId)
                    .buyerId("crowd-" + i).status(TicketStatus.ISSUED).price(BigDecimal.ONE).build());
        }
        template.insertAll(many).collectList().block();
        assertThat(refusal(asOrganizer(organizer, "o", messaging.send(eventId, "Hello crowd", "This message is for a very large crowd.", null, null)))
                .errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        verify(world.identity, never()).notifyUsers(anyString(), anyString(), anyCollection(), any());
    }

    @Test
    @DisplayName("delivery is counted from what identity says it reached, so a partial reach is PARTIAL and none is FAILED")
    void deliveryIsCountedFromIdentity() {
        tickets(eventId, "h1", TicketStatus.ISSUED, 1, "gold", null);
        tickets(eventId, "h2", TicketStatus.ISSUED, 1, "gold", null);
        tickets(eventId, "h3", TicketStatus.ISSUED, 1, "gold", null);
        when(world.identity.notifyUsers(anyString(), anyString(), anyCollection(), any()))
                .thenReturn(Mono.just(NotificationReceipt.queued("WHATSAPP", null, 1)));
        HolderMessage partial = send("Update one", "Only one of the holders can be reached.", null, null);
        assertThat(partial.getRecipientCount()).isEqualTo(3);
        assertThat(partial.getDeliveredCount()).isEqualTo(1);
        assertThat(partial.getStatus()).isEqualTo(HolderMessage.Status.PARTIAL);

        when(world.identity.notifyUsers(anyString(), anyString(), anyCollection(), any()))
                .thenReturn(Mono.error(new IllegalStateException("down")));
        HolderMessage failed = send("Update two", "Identity is unreachable for this one.", null, null);
        assertThat(failed.getStatus()).isEqualTo(HolderMessage.Status.FAILED);
        assertThat(failed.getDeliveredCount()).isZero();
        assertThat(Map.of("a", 1)).isNotEmpty();
    }
}
