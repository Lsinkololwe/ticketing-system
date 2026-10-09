package com.pml.catalog.workflow.lifecycle;

import com.pml.catalog.testing.CatalogWiring;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.exception.InvalidEventStateException;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.impl.EventServiceImpl;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.event.Outbox;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.Persistence;
import com.pml.shared.testing.TestClock;
import org.assertj.core.api.InstanceOfAssertFactories;
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
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The writes the lifecycle activities make, against a real replica set.
 *
 * <p>Every one runs twice here, because a Temporal activity can: a worker lost after the commit
 * retries the attempt. The property asserted is one transition and one envelope, however many times
 * the activity runs.
 */
@Tag("L2")
@Tag("ET-CAT-001")
@DisplayName("ET-CAT-001-R4/R5/R7 · lifecycle writes move once, stage once, and refuse what the state machine refuses")
class EventLifecycleWritesTest {

    private static final String EVENT = "event-lifecycle-writes";
    private static final Instant STARTS_AT = Instant.parse("2026-12-01T18:00:00Z");
    private static final Duration LENGTH = Duration.ofHours(4);

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static Outbox outbox;
    private static EventServiceImpl service;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "catalog_lifecycle_writes"));
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

    @Test
    @DisplayName("R5 · a reschedule keeps the duration, records the previous start and counts, with one envelope however often it runs")
    void aRescheduleMovesOnce() {
        seed(EventStatus.PUBLISHED, 0, 0);
        Instant moved = STARTS_AT.plus(Duration.ofDays(7));

        service.rescheduleEvent(EVENT, moved, "Headliner delayed").block();
        service.rescheduleEvent(EVENT, moved, "Headliner delayed").block();

        Event stored = stored();
        assertThat(stored.getEventDateTime()).isEqualTo(moved);
        assertThat(stored.getEndDateTime()).isEqualTo(moved.plus(LENGTH));
        assertThat(stored.getPreviousStartsAt()).isEqualTo(STARTS_AT);
        assertThat(stored.getRescheduleCount()).isEqualTo(1);
        assertThat(stored.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(drained()).isEqualTo(1L);
    }

    @Test
    @DisplayName("§4 · a fourth reschedule is refused and stages nothing")
    void theFourthRescheduleIsRefused() {
        seed(EventStatus.PUBLISHED, 0, LifecycleRules.MAX_RESCHEDULES);

        assertThatThrownBy(() -> service.rescheduleEvent(EVENT, STARTS_AT.plus(Duration.ofDays(7)), "Again").block())
                .isInstanceOf(InvalidEventStateException.class);

        assertThat(stored().getEventDateTime()).isEqualTo(STARTS_AT);
        Persistence.assertNothingPersisted(template, CatalogCollections.OUTBOX);
    }

    @Test
    @DisplayName("R4 · one sold ticket refuses unpublishing with the sold count")
    void aSoldTicketBlocksUnpublishing() {
        seed(EventStatus.PUBLISHED, 1, 0);

        assertThatThrownBy(() -> service.unpublishEvent(EVENT, 1L).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal -> {
                    assertThat(refusal.errorCode()).isEqualTo(ErrorCode.EVENT_STATE_INVALID);
                    assertThat(refusal).extracting("details").asInstanceOf(InstanceOfAssertFactories.MAP)
                            .containsEntry("soldTickets", 1L);
                });

        assertThat(stored().getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    @DisplayName("R4 · with nothing sold an event returns to APPROVED and leaves discovery, and a retry changes nothing")
    void unpublishingReturnsToApproved() {
        seed(EventStatus.PUBLISHED, 0, 0);

        service.unpublishEvent(EVENT, 0L).block();
        service.unpublishEvent(EVENT, 0L).block();

        Event stored = stored();
        assertThat(stored.getStatus()).isEqualTo(EventStatus.APPROVED);
        assertThat(stored.isPublished()).isFalse();
        assertThat(stored.getPublishedAt()).isNull();
        Persistence.assertNothingPersisted(template, CatalogCollections.OUTBOX);
    }

    @Test
    @DisplayName("R6 · completing twice completes once and stages one catalog.EventCompleted")
    void completionIsIdempotent() {
        seed(EventStatus.PUBLISHED, 0, 0);

        service.completeEvent(EVENT).block();
        service.completeEvent(EVENT).block();

        assertThat(stored().getStatus()).isEqualTo(EventStatus.COMPLETED);
        assertThat(drained()).isEqualTo(1L);
    }

    @Test
    @DisplayName("R7 · cancelling twice cancels once and stages one catalog.EventCancelled")
    void cancellationIsIdempotent() {
        seed(EventStatus.PUBLISHED, 0, 0);

        service.cancelEventWithReason(EVENT, "Venue flooded").block();
        service.cancelEventWithReason(EVENT, "Venue flooded").block();

        assertThat(stored().getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(drained()).isEqualTo(1L);
    }

    @Test
    @DisplayName("R7 · a draft is deleted, not cancelled")
    void aDraftIsNotCancellable() {
        seed(EventStatus.DRAFT, 0, 0);

        assertThatThrownBy(() -> service.cancelEventWithReason(EVENT, "Changed my mind").block())
                .isInstanceOf(InvalidEventStateException.class);

        assertThat(stored().getStatus()).isEqualTo(EventStatus.DRAFT);
        Persistence.assertNothingPersisted(template, CatalogCollections.OUTBOX);
    }

    @Test
    @DisplayName("R4 · publishing twice publishes once and stages one catalog.EventPublished")
    void publicationIsIdempotent() {
        seed(EventStatus.APPROVED, 0, 0);

        service.publishEvent(EVENT).block();
        service.publishEvent(EVENT).block();

        assertThat(stored().getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(drained()).isEqualTo(1L);
    }

    private static void seed(EventStatus status, int sold, int reschedules) {
        template.save(Event.builder()
                .id(EVENT)
                .title("Lifecycle writes probe")
                .organizationId("org-lifecycle-writes")
                .organizerId("user-owner")
                .status(status)
                .published(status == EventStatus.PUBLISHED)
                .isActive(true)
                .isDeleted(false)
                .totalCapacity(500)
                .soldTickets(sold)
                .rescheduleCount(reschedules)
                .eventDateTime(STARTS_AT)
                .endDateTime(STARTS_AT.plus(LENGTH))
                .build()).block();
    }

    private static Event stored() {
        Event event = template.findById(EVENT, Event.class).block();
        assertThat(event).isNotNull();
        return event;
    }

    /** Drains through the real outbox claim, counting what the bus would receive. */
    private static long drained() {
        Long count = outbox.drain(envelope -> Mono.empty(), 50).block();
        return count == null ? 0L : count;
    }
}
