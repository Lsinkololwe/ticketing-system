package com.pml.booking.event;

import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.infrastructure.messaging.CatalogConsumerConfig;
import com.pml.shared.constants.EscrowStatus;
import com.pml.shared.event.ConsumerDispatch;
import com.pml.shared.event.ConsumerGuard;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booking's catalog consumer deduplicates per message, not per catalog event.
 *
 * <h2>The property that matters</h2>
 * One catalog event produces several messages over its life — published, completed, perhaps
 * rescheduled or cancelled. Each is a separate envelope with its own {@code eventId}. A consumer that
 * keyed deduplication on the catalog event's id would treat the completion as a replay of the
 * publication and never lock the escrow. So the cases below deliver different messages about one
 * event and assert each is handled, and redeliver one message and assert it is not — against a real
 * Redis, with booking's per-wire-name durable check behind it.
 */
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R5 · catalog messages deduplicate per message, not per catalog event")
class CatalogEventRoutingTest {

    private static final String EVENT = "event-routing-probe";
    private static final String ORG = "org-routing-probe";

    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;
    private static Clock clock;

    private final AtomicReference<EventEscrowAccount> escrow = new AtomicReference<>();
    private ConsumerDispatch dispatch;

    @BeforeAll
    static void connect() {
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
        clock = TestClock.frozenAt(Instant.parse("2026-11-01T09:00:00Z"));
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
        escrow.set(null);
        ConsumerGuard guard = new ConsumerGuard(redis,
                CatalogConsumerConfig.durablyHandled(eventId -> Mono.justOrEmpty(escrow.get())),
                CatalogConsumerConfig.CONSUMER);
        dispatch = new ConsumerDispatch(guard, RetryBudget.DEFAULT, letter -> Mono.empty(),
                new DeadLetterDepth(new SimpleMeterRegistry()),
                CatalogConsumerConfig.CONSUMER, CatalogConsumerConfig.SUBSCRIPTION);
    }

    private static EventEnvelope published() {
        return EventEnvelopes.of(EventType.CATALOG_EVENT_PUBLISHED, clock.instant(), EVENT,
                Map.of("eventId", EVENT, "organizationId", ORG, "startsAt", "2026-12-01T18:00:00Z"));
    }

    private static EventEnvelope completed() {
        return EventEnvelopes.of(EventType.CATALOG_EVENT_COMPLETED, clock.instant(), EVENT,
                Map.of("eventId", EVENT, "completedAt", "2026-12-02T01:00:00Z"));
    }

    private Mono<Void> openEscrow() {
        return Mono.fromRunnable(() -> escrow.set(EventEscrowAccount.builder()
                .eventId(EVENT).organizationId(ORG).status(EscrowStatus.ACTIVE).build()));
    }

    private Mono<Void> holdEscrow() {
        return Mono.fromRunnable(() -> escrow.set(escrow.get().toBuilder().status(EscrowStatus.HOLD).build()));
    }

    @Test
    @DisplayName("publication and completion of one event are two messages, and both are handled")
    void publishedThenCompletedAreBothHandled() {
        assertThat(dispatch.deliver(published(), openEscrow()).block())
                .isEqualTo(ConsumerDispatch.Outcome.HANDLED);
        assertThat(dispatch.deliver(completed(), holdEscrow()).block())
                .as("the completion names the same catalog event but is a different message")
                .isEqualTo(ConsumerDispatch.Outcome.HANDLED);
        assertThat(escrow.get().getStatus()).isEqualTo(EscrowStatus.HOLD);
    }

    @Test
    @DisplayName("a redelivered completion is recognised as a duplicate")
    void aRedeliveredCompletionIsADuplicate() {
        dispatch.deliver(published(), openEscrow()).block();
        EventEnvelope completion = completed();

        assertThat(dispatch.deliver(completion, holdEscrow()).block()).isEqualTo(ConsumerDispatch.Outcome.HANDLED);
        assertThat(dispatch.deliver(completion, holdEscrow()).block()).isEqualTo(ConsumerDispatch.Outcome.DUPLICATE);
    }

    @Test
    @DisplayName("with Redis flushed, the escrow's own status still recognises a handled completion")
    void aCompletionIsRecognisedAfterARedisFlush() {
        dispatch.deliver(published(), openEscrow()).block();
        EventEnvelope completion = completed();
        dispatch.deliver(completion, holdEscrow()).block();

        redis.getConnectionFactory().getReactiveConnection().serverCommands().flushAll().block();

        assertThat(dispatch.deliver(completion, holdEscrow()).block())
                .as("an escrow that has left ACTIVE is the durable answer")
                .isEqualTo(ConsumerDispatch.Outcome.DUPLICATE);
    }
}
