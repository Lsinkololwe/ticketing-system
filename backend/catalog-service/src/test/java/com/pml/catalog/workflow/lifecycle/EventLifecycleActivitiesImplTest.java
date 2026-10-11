package com.pml.catalog.workflow.lifecycle;

import com.pml.catalog.testing.CatalogWiring;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.infrastructure.client.BookingServiceClient;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.impl.EventServiceImpl;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.event.Outbox;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The two lifecycle decisions that ask booking rather than trust catalog's own counters, against a
 * real replica set.
 *
 * <p>{@code EventLifecycleWritesTest} covers the state-machine rules {@link EventServiceImpl} owns.
 * This is the layer above it: {@link EventLifecycleActivitiesImpl} asking
 * {@link BookingServiceClient} first, and refusing or proceeding on booking's answer rather than on
 * {@code Event.soldTickets} or any local flag.
 */
@Tag("L2")
@Tag("ET-CAT-001")
@DisplayName("ET-CAT-001-R4/R7 · unpublish and cancel decide from booking's answer, not catalog's own counters")
class EventLifecycleActivitiesImplTest {

    private static final String EVENT = "event-lifecycle-activities";
    private static final Instant STARTS_AT = Instant.parse("2026-12-01T18:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventServiceImpl eventService;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "catalog_lifecycle_activities"));
        Clock clock = TestClock.frozenAt(Instant.parse("2026-11-01T09:00:00Z"));
        EventRepository events = new ReactiveMongoRepositoryFactory(template).getRepository(EventRepository.class);
        Outbox outbox = new Outbox(template, CatalogCollections.OUTBOX, clock);
        eventService = new EventServiceImpl(events, clock, outbox,
                TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory())),
                CatalogWiring.venues(template, clock), CatalogWiring.tierFactory(), CatalogWiring.tiers(template),
                CatalogWiring.mirror(template, clock), CatalogWiring.categories(template),
                org.mockito.Mockito.mock(com.pml.catalog.infrastructure.client.IdentityServiceClient.class));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), Event.class).block();
        template.remove(new Query(), org.bson.Document.class, CatalogCollections.OUTBOX).block();
        template.save(Event.builder()
                .id(EVENT)
                .title("Lifecycle activities probe")
                .organizationId("org-lifecycle-activities")
                .organizerId("user-owner")
                .status(EventStatus.PUBLISHED)
                .published(true)
                .isActive(true)
                .isDeleted(false)
                .totalCapacity(500)
                .soldTickets(0) // deliberately disagrees with booking in the tests below
                .eventDateTime(STARTS_AT)
                .build()).block();
    }

    private static Event stored() {
        return template.findById(EVENT, Event.class).block();
    }

    private EventLifecycleActivitiesImpl activitiesWith(BookingServiceClient booking) {
        return new EventLifecycleActivitiesImpl(eventService, booking);
    }

    @Test
    @DisplayName("R4 · unpublish refuses on booking's sold count even when the local counter says zero")
    void unpublishTrustsBookingOverTheLocalCounter() {
        BookingServiceClient booking = mock(BookingServiceClient.class);
        when(booking.soldTicketCount(EVENT)).thenReturn(Mono.just(3L));

        assertThatThrownBy(() -> activitiesWith(booking).unpublish(EVENT))
                .satisfies(error -> assertThat(com.pml.shared.workflow.Refusals.typeOf(error, null))
                        .isEqualTo(ErrorCode.EVENT_STATE_INVALID.name()));
        assertThat(stored().getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("R4 · unpublish proceeds when booking reports nothing sold")
    void unpublishProceedsWhenBookingReportsNothingSold() {
        BookingServiceClient booking = mock(BookingServiceClient.class);
        when(booking.soldTicketCount(EVENT)).thenReturn(Mono.just(0L));

        activitiesWith(booking).unpublish(EVENT);

        assertThat(stored().getStatus()).isEqualTo(EventStatus.APPROVED);
    }

    @Test
    @DisplayName("R7 · a cancellation is refused while booking reports an open payout, and nothing is written")
    void cancellationRefusedWhilePayoutIsOpen() {
        BookingServiceClient booking = mock(BookingServiceClient.class);
        when(booking.hasOpenPayoutRequest(EVENT)).thenReturn(Mono.just(true));

        assertThatThrownBy(() -> activitiesWith(booking).cancel(EVENT, "organizer requested"))
                .satisfies(error -> assertThat(com.pml.shared.workflow.Refusals.typeOf(error, null))
                        .isEqualTo(ErrorCode.EVENT_STATE_INVALID.name()));
        assertThat(stored().getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("R7 · a cancellation proceeds once booking reports no open payout")
    void cancellationProceedsWithNoOpenPayout() {
        BookingServiceClient booking = mock(BookingServiceClient.class);
        when(booking.hasOpenPayoutRequest(EVENT)).thenReturn(Mono.just(false));

        activitiesWith(booking).cancel(EVENT, "organizer requested");

        assertThat(stored().getStatus()).isEqualTo(EventStatus.CANCELLED);
    }
}
