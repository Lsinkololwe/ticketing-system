package com.pml.catalog.event;

import com.pml.catalog.testing.CatalogWiring;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.impl.EventServiceImpl;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.event.EventEnvelope;
import com.pml.shared.event.EventType;
import com.pml.shared.event.Outbox;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.Persistence;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
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
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Catalog's lifecycle transitions and their registered envelopes commit together.
 *
 * <h2>What booking depends on</h2>
 * booking opens escrow on {@code catalog.EventPublished}, locks it on {@code EventCompleted}, refunds on
 * {@code EventCancelled} and opens a refund window on {@code EventRescheduled}. Each of those facts is
 * staged in {@code catalog_outbox} by the real {@link EventServiceImpl}, in the transaction that changes
 * the event, and drained with its registered wire name and payload keys.
 *
 * <h2>The rollback is the case with teeth</h2>
 * A transition that fails after the save must leave neither the status change nor an envelope. An
 * envelope for a publication that rolled back would open escrow for an event nobody can buy.
 */
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R1 · catalog lifecycle changes and their envelopes commit together, or neither does")
class CatalogLifecycleOutboxTest {

    private static final String EVENT = "event-lifecycle-probe";
    private static final String ORG = "org-lifecycle-probe";
    private static final Instant STARTS_AT = Instant.parse("2026-12-01T18:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static Outbox outbox;
    private static EventServiceImpl service;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "catalog_lifecycle_outbox"));
        Clock clock = TestClock.frozenAt(Instant.parse("2026-11-01T09:00:00Z"));
        EventRepository events = new ReactiveMongoRepositoryFactory(template).getRepository(EventRepository.class);
        outbox = new Outbox(template, CatalogCollections.OUTBOX, clock);
        service = new EventServiceImpl(events, clock, outbox,
                TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory())),
                CatalogWiring.venues(template, clock), CatalogWiring.tierFactory(), CatalogWiring.tiers(template),
                CatalogWiring.mirror(template, clock), CatalogWiring.categories(template));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void reset() {
        template.remove(new Query(), Event.class).block();
        template.remove(new Query(), Document.class, CatalogCollections.OUTBOX).block();
    }

    private static void seed(EventStatus status, String organizationId, String organizerId) {
        template.save(Event.builder()
                .id(EVENT)
                .title("Lifecycle probe")
                .organizationId(organizationId)
                .organizerId(organizerId)
                .status(status)
                .published(status == EventStatus.PUBLISHED)
                .isActive(true)
                .isDeleted(false)
                .totalCapacity(500)
                .eventDateTime(STARTS_AT)
                .build()).block();
    }

    @Test
    @DisplayName("publishing stages catalog.EventPublished with the §4 keys")
    void publishStagesEventPublished() {
        seed(EventStatus.APPROVED, ORG, "user-owner");

        service.publishEvent(EVENT).block();

        assertThat(status()).isEqualTo(EventStatus.PUBLISHED);
        EventEnvelope staged = drainOne();
        assertThat(staged.eventType()).isEqualTo(EventType.CATALOG_EVENT_PUBLISHED.wireName());
        assertThat(staged.sourceService()).isEqualTo("catalog");
        assertThat(staged.payload())
                .containsEntry("eventId", EVENT)
                .containsEntry("organizationId", ORG)
                .containsEntry("startsAt", STARTS_AT.toString());
    }

    @Test
    @DisplayName("a refused publication changes nothing and stages nothing")
    void aRefusedPublicationStagesNothing() {
        seed(EventStatus.DRAFT, ORG, "user-owner");

        assertThatThrownBy(() -> service.publishEvent(EVENT).block()).isInstanceOf(RuntimeException.class);

        assertThat(status()).isEqualTo(EventStatus.DRAFT);
        Persistence.assertNothingPersisted(template, CatalogCollections.OUTBOX);
    }

    @Test
    @DisplayName("a publication whose envelope cannot be built rolls the status back")
    void anEnvelopeFailureRollsThePublicationBack() {
        // No organization and no organizer: the envelope refuses a blank organizationId after the save has run,
        // inside the transaction — which is exactly the moment a rollback has to undo.
        seed(EventStatus.APPROVED, null, null);

        assertThatThrownBy(() -> service.publishEvent(EVENT).block()).isInstanceOf(RuntimeException.class);

        assertThat(status())
                .as("the save ran before the envelope failed; the transaction must undo it")
                .isEqualTo(EventStatus.APPROVED);
        Persistence.assertNothingPersisted(template, CatalogCollections.OUTBOX);
    }

    @Test
    @DisplayName("completing stages catalog.EventCompleted")
    void completeStagesEventCompleted() {
        seed(EventStatus.PUBLISHED, ORG, "user-owner");

        service.completeEvent(EVENT).block();

        assertThat(status()).isEqualTo(EventStatus.COMPLETED);
        EventEnvelope staged = drainOne();
        assertThat(staged.eventType()).isEqualTo(EventType.CATALOG_EVENT_COMPLETED.wireName());
        assertThat(staged.payload()).containsEntry("eventId", EVENT).containsKey("completedAt");
    }

    @Test
    @DisplayName("cancelling stages catalog.EventCancelled with its reason")
    void cancelStagesEventCancelled() {
        seed(EventStatus.PUBLISHED, ORG, "user-owner");

        service.cancelEventWithDetails(EVENT, "Venue flooded", true, true).block();

        assertThat(status()).isEqualTo(EventStatus.CANCELLED);
        EventEnvelope staged = drainOne();
        assertThat(staged.eventType()).isEqualTo(EventType.CATALOG_EVENT_CANCELLED.wireName());
        assertThat(staged.payload()).containsEntry("eventId", EVENT).containsEntry("reason", "Venue flooded");
    }

    @Test
    @DisplayName("rescheduling stages catalog.EventRescheduled with both start times")
    void rescheduleStagesEventRescheduled() {
        seed(EventStatus.PUBLISHED, ORG, "user-owner");
        Instant moved = STARTS_AT.plusSeconds(7 * 24 * 3600);

        service.rescheduleEvent(EVENT, moved, "Headliner delayed").block();

        EventEnvelope staged = drainOne();
        assertThat(staged.eventType()).isEqualTo(EventType.CATALOG_EVENT_RESCHEDULED.wireName());
        assertThat(staged.payload())
                .containsEntry("eventId", EVENT)
                .containsEntry("previousStartsAt", STARTS_AT.toString())
                .containsEntry("newStartsAt", moved.toString());
    }

    private static EventStatus status() {
        Event event = template.findById(EVENT, Event.class).block();
        assertThat(event).isNotNull();
        return event.getStatus();
    }

    /** Drains through the real outbox claim, so the envelope asserted is the one the bus receives. */
    private static EventEnvelope drainOne() {
        List<EventEnvelope> published = new CopyOnWriteArrayList<>();
        Long count = outbox.drain(envelope -> {
            published.add(envelope);
            return Mono.empty();
        }, 10).block();
        assertThat(count).as("exactly one envelope staged").isEqualTo(1L);
        return published.get(0);
    }
}
