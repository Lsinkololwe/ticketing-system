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
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The drain turns, and keeps its promise across a crash.
 *
 * <h2>What this guards</h2>
 * {@link Outbox} stages, claims and marks; {@link EventBridge} publishes one envelope. Without a
 * drain that actually calls them, the mechanism is complete, proven on a replica set, and
 * unreachable — publishers send directly and keep the window the outbox exists to close: the
 * document commits, the bus send fails, a WARN line is written, and nothing retries because
 * nothing recorded that it should.
 *
 * <h2>The property, not the plumbing</h2>
 * A test that stages a row and watches it publish proves only that two methods can be called in
 * order. What the outbox promises is stronger and is what is asserted here: <b>a committed write is never
 * silently unaccompanied by its message</b>, including when the process publishing it dies. So the
 * cases below are a failing bus, an abandoned claim, and a row that must not be sent twice.
 */
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R1 · the drain publishes what was staged, and survives a crash")
class OutboxDrainTest {

    private static final String OUTBOX = "drain_outbox";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static TestClock clock;
    private static Outbox outbox;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "drain_harness"));
        clock = TestClock.frozenAt(Instant.parse("2026-09-02T09:00:00Z"));
        outbox = new Outbox(template, OUTBOX, clock);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void reset() {
        template.remove(new Query(), OUTBOX).block();
    }

    private static EventEnvelope envelope(String ticketId) {
        return EventEnvelopes.of(EventType.BOOKING_TICKET_PURCHASED, clock.instant(),
                "correlation-1", Map.of("ticketId", ticketId, "eventId", "event-1",
                        "tierId", "tier-1", "ownerId", "user-1", "quantity", 1));
    }

    /** A drain wired to a sink the test controls, standing in for the bus. */
    private OutboxDrain drainInto(List<EventEnvelope> sink) {
        return drainThat(envelope -> {
            sink.add(envelope);
            return Mono.empty();
        });
    }

    private OutboxDrain drainThat(java.util.function.Function<EventEnvelope, Mono<Void>> sink) {
        // EventBridge is final in its collaborator role here, so the drain is built against a
        // subclass that overrides publish. What is under test is the drain's own logic —
        // reclaim, batch, error handling — not Service Bus's client.
        return new OutboxDrain(outbox, new EventBridge(null, "unused") {
            @Override
            public Mono<Void> publish(EventEnvelope envelope) {
                return sink.apply(envelope);
            }
        });
    }

    private long rowsWith(String status) {
        return template.count(Query.query(Criteria.where("status").is(status)),
                Document.class, OUTBOX).block();
    }

    @Test
    @DisplayName("a staged envelope reaches the bus and the row is marked SENT")
    void aStagedEnvelopeIsPublished() {
        outbox.stage(envelope("TKT-1")).block();
        List<EventEnvelope> delivered = new CopyOnWriteArrayList<>();

        Long published = drainInto(delivered).publishPending().block();

        assertThat(published).isEqualTo(1L);
        assertThat(delivered).hasSize(1);
        assertThat(delivered.get(0).payload()).containsEntry("ticketId", "TKT-1");
        assertThat(rowsWith(Outbox.SENT)).isEqualTo(1);
        assertThat(rowsWith(Outbox.PENDING)).isZero();
    }

    @Test
    @DisplayName("a failing bus leaves the row PENDING, so the next pass retries it")
    void aFailingBusLeavesTheRowOwed() {
        outbox.stage(envelope("TKT-2")).block();

        Long published = drainThat(e -> Mono.error(new IllegalStateException("bus unreachable")))
                .publishPending().block();

        assertThat(published).isZero();
        // The whole point. A dropped row would be a message the platform promised and forgot;
        // PENDING is the record that it is still owed.
        assertThat(rowsWith(Outbox.PENDING))
                .as("a failed publish must return the row to PENDING, not leave it claimed")
                .isEqualTo(1);
        assertThat(rowsWith(Outbox.SENT)).isZero();

        // And the retry actually works — otherwise "still PENDING" would be cold comfort.
        List<EventEnvelope> delivered = new CopyOnWriteArrayList<>();
        assertThat(drainInto(delivered).publishPending().block()).isEqualTo(1L);
        assertThat(delivered).hasSize(1);
    }

    @Test
    @DisplayName("a claim abandoned by a dead process is reclaimed, not stranded")
    void anAbandonedClaimIsReclaimed() {
        outbox.stage(envelope("TKT-3")).block();

        // Exactly what a process dying mid-publish leaves behind: PUBLISHING, with a claim
        // timestamp and nobody coming back for it. The claim filter matches only PENDING, so
        // without reclaim this row is not lost and is never sent either — which is the harder
        // failure to notice, because its status says somebody is handling it.
        template.updateFirst(new Query(),
                new Update().set("status", Outbox.PUBLISHING)
                        .set("claimedAt", java.util.Date.from(
                                clock.instant().minus(Duration.ofHours(1)))),
                OUTBOX).block();

        List<EventEnvelope> delivered = new CopyOnWriteArrayList<>();
        Long published = drainInto(delivered).publishPending().block();

        assertThat(published)
                .as("the drain must reclaim before it claims, or a dead process's row waits forever")
                .isEqualTo(1L);
        assertThat(delivered).hasSize(1);
        assertThat(rowsWith(Outbox.SENT)).isEqualTo(1);
    }

    @Test
    @DisplayName("a claim that is still fresh is left alone")
    void aFreshClaimIsNotStolen() {
        outbox.stage(envelope("TKT-4")).block();
        // Claimed thirty seconds ago: unambiguously fresh against a five-minute timeout, and
        // unambiguously in the past. Stamping it at exactly `now` was this test's first version and
        // it proved nothing — the cutoff comparison is strict, so a timeout of ZERO would not have
        // stolen it either, and the test passed against a drain that reclaims everything. The
        // mutation is what said so.
        template.updateFirst(new Query(),
                new Update().set("status", Outbox.PUBLISHING)
                        .set("claimedAt", java.util.Date.from(
                                clock.instant().minus(Duration.ofSeconds(30)))),
                OUTBOX).block();

        List<EventEnvelope> delivered = new CopyOnWriteArrayList<>();
        Long published = drainInto(delivered).publishPending().block();

        // The other bound, and it is the one that keeps the reclaim honest. A reclaim that took
        // every PUBLISHING row would publish messages another drain is mid-way through sending —
        // a duplicate the platform caused itself, on a timer it chose.
        assertThat(published).isZero();
        assertThat(delivered).isEmpty();
        assertThat(rowsWith(Outbox.PUBLISHING)).isEqualTo(1);
    }

    @Test
    @DisplayName("a published row is not published again")
    void aSentRowIsNotResent() {
        outbox.stage(envelope("TKT-5")).block();
        List<EventEnvelope> delivered = new CopyOnWriteArrayList<>();
        OutboxDrain drain = drainInto(delivered);

        drain.publishPending().block();
        drain.publishPending().block();
        drain.publishPending().block();

        assertThat(delivered)
                .as("at-least-once is the bus's guarantee; the drain must not add duplicates of "
                        + "its own on top of it")
                .hasSize(1);
    }

    @Test
    @DisplayName("the batch is bounded, and the backlog drains over successive passes")
    void theBatchIsBounded() {
        int staged = OutboxDrain.BATCH + 25;
        for (int i = 0; i < staged; i++) {
            outbox.stage(envelope("TKT-B" + i)).block();
        }

        AtomicInteger seen = new AtomicInteger();
        OutboxDrain drain = drainThat(e -> {
            seen.incrementAndGet();
            return Mono.empty();
        });

        assertThat(drain.publishPending().block())
                .as("one pass must take at most BATCH, or a large backlog produces a pass that "
                        + "runs for minutes holding claims")
                .isEqualTo((long) OutboxDrain.BATCH);
        assertThat(drain.publishPending().block()).isEqualTo(25L);
        assertThat(seen.get()).isEqualTo(staged);
        assertThat(drain.pendingCount().block()).isZero();
    }

    @Test
    @DisplayName("envelopes go out oldest first")
    void oldestFirst() {
        outbox.stage(envelope("TKT-first")).block();
        clock.advance(Duration.ofSeconds(30));
        outbox.stage(envelope("TKT-second")).block();

        List<EventEnvelope> delivered = new CopyOnWriteArrayList<>();
        drainInto(delivered).publishPending().block();

        // Not an ordering guarantee to consumers — cross-service delivery is unordered. This is
        // about the outbox not starving its own oldest row, which is what a
        // scan with no sort does the moment the backlog exceeds one batch.
        assertThat(delivered).hasSize(2);
        assertThat(delivered.get(0).payload()).containsEntry("ticketId", "TKT-first");
    }
}
