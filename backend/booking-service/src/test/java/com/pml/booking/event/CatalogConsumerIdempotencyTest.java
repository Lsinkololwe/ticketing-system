package com.pml.booking.event;

import com.pml.booking.infrastructure.messaging.CatalogConsumerConfig;
import com.pml.shared.event.ConsumerDispatch;
import com.pml.shared.event.ConsumerGuard;
import com.pml.shared.event.DeadLetter;
import com.pml.shared.event.DeadLetterDepth;
import com.pml.shared.event.EventEnvelope;
import com.pml.shared.event.EventEnvelopes;
import com.pml.shared.event.EventType;
import com.pml.shared.event.RetryBudget;
import com.pml.shared.testing.RedisNode;
import com.pml.shared.testing.TestClock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booking's catalog subscription survives redelivery and reordering.
 *
 * <h2>What this closes</h2>
 * `ConsumerGuard` and `ConsumerDispatch` were built, tested against a real Redis container, and
 * <b>wired to nothing</b>. The one consumer bean the platform has — booking's catalog subscription —
 * caught exceptions and declined to checkpoint.
 *
 * <p>That is not a small gap. The bus is at-least-once, so redelivery is normal traffic. A
 * redelivered `EventPublished` re-ran `createEscrowAccount`; the unique index on
 * `escrow_accounts.eventId` refused the second write; the exception was logged and the message not
 * acknowledged; Service Bus redelivered; it failed identically; and after `maxDeliveryCount` it
 * dead-lettered. <b>The bus behaving exactly as documented produced a dead letter.</b></p>
 *
 * <h2>Both properties</h2>
 * Double delivery <em>and</em> reverse order, because they fail differently. A guard fixes the
 * first. The second is about a consumer assuming a sequence the bus never promised — and the reason
 * it is asserted here is that passing it must not be luck.
 */
// L2, not L5: this stands up a Redis container and nothing else. The corpus reserves L5 for
// Testcontainers *plus* WireMock, and TestLayerLintTest derives the layer from what a class
// actually references — which is how it caught this one being over-tagged.
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003 R5/R6 · the catalog consumer is idempotent and order-independent")
class CatalogConsumerIdempotencyTest {

    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static Clock clock;

    /** Booking's durable state, standing in for "does an escrow exist for this event". */
    private static final AtomicBoolean escrowExists = new AtomicBoolean(false);

    @BeforeAll
    static void connect() {
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        clock = TestClock.frozenAt(Instant.parse("2026-09-02T09:00:00Z"));
    }

    @AfterAll
    static void disconnect() {
        if (redisFactory != null) {
            redisFactory.destroy();
        }
    }

    @BeforeEach
    void reset() {
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();
        escrowExists.set(false);
    }

    private ConsumerDispatch dispatch(List<DeadLetter> deadLetters) {
        ConsumerGuard guard = new ConsumerGuard(redis,
                envelope -> Mono.just(escrowExists.get()),
                CatalogConsumerConfig.CONSUMER);
        return new ConsumerDispatch(guard, RetryBudget.DEFAULT,
                letter -> {
                    deadLetters.add(letter);
                    return Mono.empty();
                },
                new DeadLetterDepth(new SimpleMeterRegistry()),
                CatalogConsumerConfig.CONSUMER, CatalogConsumerConfig.SUBSCRIPTION);
    }

    private static EventEnvelope published(String eventId) {
        return EventEnvelopes.of(EventType.CATALOG_EVENT_PUBLISHED, clock.instant(), eventId,
                Map.of("eventId", eventId, "organizationId", "org-1",
                        "startsAt", "2026-10-01T18:00:00Z"));
    }

    @Test
    @DisplayName("the same delivery twice creates the escrow once")
    void doubleDeliveryRunsTheWorkOnce() {
        List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();
        ConsumerDispatch dispatch = dispatch(deadLetters);
        EventEnvelope envelope = published("event-1");
        List<String> created = new CopyOnWriteArrayList<>();

        Mono<Void> createEscrow = Mono.fromRunnable(() -> {
            created.add("event-1");
            escrowExists.set(true);
        });

        assertThat(dispatch.deliver(envelope, createEscrow).block())
                .isEqualTo(ConsumerDispatch.Outcome.HANDLED);

        // The identical envelope again — same eventId, which is what redelivery means.
        assertThat(dispatch.deliver(envelope, createEscrow).block())
                .as("a redelivery must be recognised, not re-run and refused by the unique index")
                .isEqualTo(ConsumerDispatch.Outcome.DUPLICATE);

        assertThat(created).hasSize(1);
        assertThat(deadLetters)
                .as("an ordinary redelivery must not produce a dead letter — that was the defect")
                .isEmpty();
    }

    @Test
    @DisplayName("two different events are both handled, so this is not deduplicating everything")
    void differentEventsAreBothHandled() {
        List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();
        ConsumerDispatch dispatch = dispatch(deadLetters);
        List<String> created = new CopyOnWriteArrayList<>();

        // Without this, a guard that answered DUPLICATE to everything would pass the test above
        // perfectly while silently discarding every message the platform receives.
        assertThat(dispatch.deliver(published("event-A"),
                Mono.fromRunnable(() -> created.add("A"))).block())
                .isEqualTo(ConsumerDispatch.Outcome.HANDLED);
        assertThat(dispatch.deliver(published("event-B"),
                Mono.fromRunnable(() -> created.add("B"))).block())
                .isEqualTo(ConsumerDispatch.Outcome.HANDLED);

        assertThat(created).containsExactly("A", "B");
    }

    @Test
    @DisplayName("the durable check answers when Redis has been flushed")
    void idempotencySurvivesARedisFlush() {
        List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();
        ConsumerDispatch dispatch = dispatch(deadLetters);
        EventEnvelope envelope = published("event-flush");
        List<String> created = new CopyOnWriteArrayList<>();

        dispatch.deliver(envelope, Mono.fromRunnable(() -> {
            created.add("once");
            escrowExists.set(true);
        })).block();

        // Redis is the fast path and is allowed to be empty — after a flush, a restart, an
        // eviction. The escrow row is the authority, and it is atomic with the effect because it
        // IS the effect.
        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();

        assertThat(dispatch.deliver(envelope, Mono.fromRunnable(() -> created.add("twice"))).block())
                .as("with the cache gone the durable check must still recognise the duplicate")
                .isEqualTo(ConsumerDispatch.Outcome.DUPLICATE);
        assertThat(created).containsExactly("once");
    }

    @Test
    @DisplayName("a persistently failing handler dead-letters with a reason instead of retrying forever")
    void aPoisonousMessageIsDeadLettered() {
        List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();
        ConsumerDispatch dispatch = dispatch(deadLetters);

        // Built once and reused. EventEnvelopes.of mints a fresh eventId per call, so calling
        // published() twice yields two different envelopes — which is exactly what the first
        // version of this test compared, and why it failed on ids that were both correct.
        EventEnvelope poison = published("event-poison");

        ConsumerDispatch.Outcome outcome = dispatch.deliver(poison,
                Mono.error(new IllegalStateException("escrow service is refusing"))).block();

        assertThat(outcome).isEqualTo(ConsumerDispatch.Outcome.DEAD_LETTERED);
        assertThat(deadLetters).hasSize(1);
        assertThat(deadLetters.get(0).reason())
                .as("R6 — a dead letter carries why, or an operator is left with an id and a guess")
                .isNotBlank();
        assertThat(deadLetters.get(0).eventId()).isEqualTo(poison.eventId());
    }

    @Test
    @DisplayName("delivery order does not decide the outcome")
    void reverseOrderIsHandled() {
        List<DeadLetter> deadLetters = new CopyOnWriteArrayList<>();
        ConsumerDispatch dispatch = dispatch(deadLetters);
        List<String> applied = new CopyOnWriteArrayList<>();

        EventEnvelope first = published("event-order-1");
        EventEnvelope second = published("event-order-2");

        // Cross-service delivery is unordered by design. So the consumer must not
        // depend on arrival order: delivering the later envelope first must handle both, not
        // discard one as out-of-sequence.
        assertThat(dispatch.deliver(second, Mono.fromRunnable(() -> applied.add("second"))).block())
                .isEqualTo(ConsumerDispatch.Outcome.HANDLED);
        assertThat(dispatch.deliver(first, Mono.fromRunnable(() -> applied.add("first"))).block())
                .isEqualTo(ConsumerDispatch.Outcome.HANDLED);

        assertThat(applied).containsExactly("second", "first");
    }
}
