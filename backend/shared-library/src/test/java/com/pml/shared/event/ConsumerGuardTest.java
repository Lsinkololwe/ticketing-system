package com.pml.shared.event;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A consumer handles each {@code eventId} once, whatever the bus does.
 *
 * <h2>What at-least-once actually costs</h2>
 * Azure Service Bus redelivers by design: a consumer that crashes after doing the work but
 * before acknowledging will see the same message again. Without a guard,
 * {@code PaymentCompleted} issues a second ticket — and nothing downstream can tell it from a
 * legitimate second purchase, so the duplicate is permanent.
 *
 * <h2>The flush case is the one worth testing</h2>
 * Redis is the fast path and MongoDB is the authority. A guard that trusted only Redis would
 * pass a plain double-delivery test and still lose every marker on a {@code FLUSHALL}, after
 * which every redelivered message is processed a second time. So the flush is asserted here,
 * not assumed away.
 */
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R5 · a consumer handles each event exactly once")
class ConsumerGuardTest {

    /**
     * The consumer's own collection — its write is the durable marker.
     *
     * <p>Modelled on a real consumer: opening an escrow account for an event. The check is
     * "does the account this message would create already exist", which is atomic with the
     * effect because it <em>is</em> the effect.</p>
     */
    private static final String ESCROW = "booking_escrow_accounts";

    private static MongoClient mongoClient;
    private static ReactiveMongoTemplate mongo;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static Clock clock;

    @BeforeAll
    static void connect() {
        mongoClient = MongoClients.create(MongoReplicaSet.connectionString());
        mongo = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(mongoClient, "consumer_guard_harness"));

        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);

        clock = TestClock.frozenAt(Instant.parse("2026-08-18T09:00:00Z"));
    }

    @AfterAll
    static void disconnect() {
        mongoClient.close();
        redisFactory.destroy();
    }

    @BeforeEach
    void reset() {
        mongo.remove(new Query(), ESCROW).block();
        flushRedis();
    }

    @Test
    @DisplayName("the same delivery twice runs the work once")
    void doubleDeliveryRunsOnce() {
        ConsumerGuard guard = guardFor("escrow-opener");
        EventEnvelope envelope = paymentCompleted();
        AtomicInteger ran = new AtomicInteger();

        assertThat(guard.runOnce(envelope, openEscrow("escrow-opener", envelope, ran)).block()).isTrue();
        assertThat(guard.runOnce(envelope, openEscrow("escrow-opener", envelope, ran)).block()).isFalse();

        assertThat(ran.get())
                .as("a redelivered PaymentCompleted that issues a second ticket is "
                        + "indistinguishable from a legitimate second purchase")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("the marker survives a Redis flush — Mongo is the authority")
    void survivesACacheFlush() {
        ConsumerGuard guard = guardFor("escrow-opener");
        EventEnvelope envelope = paymentCompleted();
        AtomicInteger ran = new AtomicInteger();

        assertThat(guard.runOnce(envelope, openEscrow("escrow-opener", envelope, ran)).block()).isTrue();

        flushRedis();

        assertThat(guard.runOnce(envelope, openEscrow("escrow-opener", envelope, ran)).block())
                .as("""
                    A guard that trusts only Redis passes a plain double-delivery test and still \
                    loses every marker on a FLUSHALL. ET-PLT-002 R7: Redis holds no business \
                    state, so a cache miss is not an answer.""")
                .isFalse();
        assertThat(ran.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("two consumers each handle the same event once")
    void markersAreScopedPerConsumer() {
        EventEnvelope envelope = paymentCompleted();
        AtomicInteger escrow = new AtomicInteger();
        AtomicInteger receipts = new AtomicInteger();

        // PaymentCompleted goes to identity for the receipt and to other consumers besides. A
        // marker keyed on eventId alone would let whichever consumer ran first silence the rest.
        assertThat(guardFor("escrow-opener").runOnce(envelope, openEscrow("escrow-opener", envelope, escrow)).block()).isTrue();
        assertThat(guardFor("receipt-sender").runOnce(envelope, openEscrow("receipt-sender", envelope, receipts)).block()).isTrue();

        assertThat(escrow.get()).isEqualTo(1);
        assertThat(receipts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("work that fails is not marked, so the redelivery retries it")
    void failedWorkIsNotMarked() {
        ConsumerGuard guard = guardFor("escrow-opener");
        EventEnvelope envelope = paymentCompleted();

        // Marking before the work would record a message as handled that never was, and the
        // redelivery — the bus's own recovery mechanism — would be skipped.
        Throwable failure = guard.runOnce(envelope, Mono.error(new IllegalStateException("downstream down")))
                .then(Mono.<Throwable>empty())
                .onErrorResume(Mono::just)
                .block();

        assertThat(failure).isNotNull();
        assertThat(mongo.count(new Query(), ESCROW).block())
                .as("no marker for work that did not happen")
                .isZero();

        AtomicInteger ran = new AtomicInteger();
        assertThat(guard.runOnce(envelope, openEscrow("escrow-opener", envelope, ran)).block()).isTrue();
        assertThat(ran.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("out-of-order deliveries are each handled once")
    void reverseOrderIsStillOnceEach() {
        ConsumerGuard guard = guardFor("escrow-opener");
        EventEnvelope first = paymentCompleted();
        EventEnvelope second = paymentCompleted();
        AtomicInteger ran = new AtomicInteger();

        // The bus does not promise order across entities, so a consumer sees them however they
        // arrive. The guard is keyed on eventId, so order changes nothing about how many times
        // each runs — which is the property, rather than any claim about sequencing.
        assertThat(guard.runOnce(second, openEscrow("escrow-opener", second, ran)).block()).isTrue();
        assertThat(guard.runOnce(first, openEscrow("escrow-opener", first, ran)).block()).isTrue();
        assertThat(guard.runOnce(second, openEscrow("escrow-opener", second, ran)).block()).isFalse();
        assertThat(guard.runOnce(first, openEscrow("escrow-opener", first, ran)).block()).isFalse();

        assertThat(ran.get()).isEqualTo(2);
    }

    // --------------------------------------------------------------------- fixture

    private static ConsumerGuard guardFor(String consumer) {
        return new ConsumerGuard(redis, envelope -> mongo.exists(
                Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id")
                        .is(consumer + ":" + envelope.eventId())),
                ESCROW), consumer);
    }

    /** What the consumer does: its write carries the event it came from. */
    private static Mono<Void> openEscrow(String consumer, EventEnvelope envelope,
                                         AtomicInteger counter) {
        return mongo.insert(new org.bson.Document("_id", consumer + ":" + envelope.eventId())
                        .append("eventId", envelope.eventId()), ESCROW)
                .doOnSuccess(saved -> counter.incrementAndGet())
                .then();
    }

    private static EventEnvelope paymentCompleted() {
        return EventEnvelopes.of(EventType.BOOKING_PAYMENT_COMPLETED, clock.instant(), "correlation-1",
                Map.of("paymentIntentId", "pi-1", "reservationId", "res-1", "userId", "u-1",
                        "amount", new java.math.BigDecimal("150.00"), "currency", "ZMW"));
    }

    private static Mono<Void> count(AtomicInteger counter) {
        return Mono.fromRunnable(counter::incrementAndGet);
    }

    private static void flushRedis() {
        org.springframework.data.redis.connection.ReactiveRedisConnection connection =
                redisFactory.getReactiveConnection();
        try {
            connection.serverCommands().flushAll().block();
        } finally {
            connection.close();
        }
    }
}
