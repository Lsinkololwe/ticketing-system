package com.pml.shared.event;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.MongoReplicaSet;
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
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The document and its message commit together, or neither does.
 *
 * <h2>The two halves</h2>
 * <ol>
 *   <li>A rolled-back transaction leaves <b>no document and no outbox row</b>. If the row could
 *       survive a rollback, the platform would publish {@code TicketPurchased} for a ticket that
 *       does not exist — and every consumer would act on it.</li>
 *   <li>A process killed between commit and publish yields <b>exactly one</b> message once it
 *       restarts. Zero is the failure the outbox exists to remove; two is the failure claiming
 *       exists to remove.</li>
 * </ol>
 *
 * <h2>On a replica set, necessarily</h2>
 * Against a standalone {@code mongod} the transaction is silently inert, so the
 * rollback assertion would pass by writing nothing rather than by rolling anything back.
 */
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R1 · the write and its message are atomic")
class OutboxTest {

    private static final String OUTBOX = "booking_outbox";
    private static final String BUSINESS = "booking_tickets";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TransactionalOperator transaction;
    private static Clock clock;
    private static Outbox outbox;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        SimpleReactiveMongoDatabaseFactory factory =
                new SimpleReactiveMongoDatabaseFactory(client, "outbox_harness");
        template = new ReactiveMongoTemplate(factory);
        transaction = TransactionalOperator.create(new ReactiveMongoTransactionManager(factory));
        clock = TestClock.frozenAt(Instant.parse("2026-08-18T09:00:00Z"));
        outbox = new Outbox(template, OUTBOX, clock);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void reset() {
        template.remove(new Query(), OUTBOX).block();
        template.remove(new Query(), BUSINESS).block();
    }

    @Test
    @DisplayName("a committed write leaves the document and exactly one staged row")
    void commitStagesBoth() {
        issueTicket("TKT-1").as(transaction::transactional).block();

        assertThat(count(BUSINESS)).isEqualTo(1);
        assertThat(count(OUTBOX)).isEqualTo(1);
        assertThat(outbox.pendingCount().block()).isEqualTo(1);
    }

    @Test
    @DisplayName("a rolled-back write leaves neither the document nor the row")
    void rollbackLeavesNothing() {
        Mono<Void> failsAfterStaging = issueTicket("TKT-2")
                .then(Mono.error(new IllegalStateException("payment declined after staging")));

        assertThat(failsAfterStaging.as(transaction::transactional)
                .onErrorResume(error -> Mono.empty())
                .then(Mono.just("done"))
                .block()).isEqualTo("done");

        assertThat(count(BUSINESS))
                .as("the business write must roll back")
                .isZero();
        assertThat(count(OUTBOX))
                .as("""
                    An outbox row surviving a rollback publishes TicketPurchased for a ticket \
                    that does not exist, and every consumer acts on it — escrow opens, a \
                    receipt is sent, a counter moves.""")
                .isZero();
    }

    @Test
    @DisplayName("a kill between commit and publish still yields exactly one message")
    void killBetweenCommitAndPublishYieldsOne() {
        // Commit succeeds; the process dies before anything is published. The staged row is the
        // only record that the message is owed.
        issueTicket("TKT-3").as(transaction::transactional).block();

        List<EventEnvelope> delivered = new CopyOnWriteArrayList<>();

        // Restart: the drain finds the row and publishes it.
        assertThat(outbox.drain(collect(delivered), 10).block()).isEqualTo(1);
        assertThat(delivered).hasSize(1);
        assertThat(delivered.get(0).eventType()).isEqualTo(EventType.BOOKING_TICKET_PURCHASED.wireName());

        // A second drain must not send it again — the row is SENT, not PENDING.
        assertThat(outbox.drain(collect(delivered), 10).block()).isZero();
        assertThat(delivered)
                .as("exactly one, not at-least-one: the claim and the SENT transition are what "
                        + "stop a second drain resending a message already on the bus")
                .hasSize(1);
    }

    @Test
    @DisplayName("a failed publish returns the row to PENDING rather than dropping it")
    void failedPublishIsRetriable() {
        issueTicket("TKT-4").as(transaction::transactional).block();

        Long published = outbox.drain(
                envelope -> Mono.error(new IllegalStateException("bus unreachable")), 10).block();

        assertThat(published).isZero();
        assertThat(outbox.pendingCount())
                .as("a row left in PUBLISHING is never retried — the claim filter only matches "
                        + "PENDING, so the message is not lost but is never sent either")
                .satisfies(pending -> assertThat(pending.block()).isEqualTo(1));

        List<EventEnvelope> delivered = new CopyOnWriteArrayList<>();
        assertThat(outbox.drain(collect(delivered), 10).block()).isEqualTo(1);
        assertThat(delivered).hasSize(1);
    }

    @Test
    @DisplayName("a claim abandoned mid-publish is reclaimed, not stranded")
    void abandonedClaimsAreReclaimed() {
        issueTicket("TKT-5").as(transaction::transactional).block();

        // What a process that died between claiming and publishing leaves behind.
        template.updateFirst(new Query(),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("status", Outbox.PUBLISHING)
                        .set("claimedAt", java.util.Date.from(clock.instant().minus(Duration.ofHours(1)))),
                OUTBOX).block();

        assertThat(outbox.pendingCount().block()).isZero();

        assertThat(outbox.reclaimStale(Duration.ofMinutes(5)).block()).isEqualTo(1);
        assertThat(outbox.pendingCount().block()).isEqualTo(1);
    }

    @Test
    @DisplayName("staging the same envelope twice is a duplicate key, not two messages")
    void stagingIsIdempotentOnEventId() {
        EventEnvelope envelope = purchaseEnvelope("TKT-6");

        outbox.stage(envelope).block();

        Throwable second = outbox.stage(envelope)
                .then(Mono.<Throwable>empty())
                .onErrorResume(Mono::just)
                .block();

        assertThat(second)
                .as("_id is the eventId, so a retried stage collides rather than queueing a "
                        + "second copy of the same event")
                .isNotNull();
        assertThat(count(OUTBOX)).isEqualTo(1);
    }

    // --------------------------------------------------------------------- fixture

    /** The publication shape: the document and the outbox row in one transaction. */
    private static Mono<Void> issueTicket(String ticketId) {
        return template.insert(new Document("_id", ticketId).append("status", "ISSUED"), BUSINESS)
                .then(outbox.stage(purchaseEnvelope(ticketId)));
    }

    private static EventEnvelope purchaseEnvelope(String ticketId) {
        return EventEnvelopes.of(EventType.BOOKING_TICKET_PURCHASED, clock.instant(), "correlation-1",
                Map.of("ticketId", ticketId, "eventId", "event-1", "tierId", "tier-1",
                        "ownerId", "user-1", "quantity", 1));
    }

    private static java.util.function.Function<EventEnvelope, Mono<Void>> collect(
            List<EventEnvelope> sink) {
        return envelope -> {
            sink.add(envelope);
            return Mono.empty();
        };
    }

    private static long count(String collection) {
        return template.count(new Query(), collection).block();
    }
}
