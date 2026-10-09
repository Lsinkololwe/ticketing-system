package com.pml.shared.event;

import com.pml.shared.testing.RedisNode;
import io.micrometer.core.instrument.MeterRegistry;
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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A failing consumer retries, and a poisonous one is visible.
 *
 * <h2>The failure this is really guarding against</h2>
 * Not "the retry did not happen" — that shows up quickly. It is the dead letter that reaches an
 * operator saying <em>Retries exhausted</em> and nothing else. The message is preserved, the
 * alert fires, and the person holding the pager still cannot tell a provider outage from a
 * deserialisation bug without reading the message body and guessing which of the subscription's
 * handlers rejected it. A dead letter carries a reason and the consumer's name because that is the
 * difference between a queue that can be triaged and one that can only be drained.
 *
 * <h2>Why the reason has its own test</h2>
 * Reactor's {@code Retry.backoff} wraps the final failure in {@code RetryExhaustedException} by
 * default. A dispatch written without {@code onRetryExhaustedThrow} passes every test about
 * retrying, dead-letters correctly, exports the metric — and records that reason for every
 * failure the platform ever has.
 */
@Tag("L2")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R6 · retry, then dead-letter with a reason")
class ConsumerDispatchTest {

    private static final String CONSUMER = "escrow-opener";
    private static final String SUBSCRIPTION = "booking-events/catalog-sub";

    /** Fast backoff: this asserts the shape of the policy, not that waiting works. */
    private static final RetryBudget BUDGET =
            new RetryBudget(3, Duration.ofMillis(5), Duration.ofMillis(20), 2.0);

    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;

    /** The consumer's own durable state — the authority the guard falls back to. */
    private final Set<String> handled = ConcurrentHashMap.newKeySet();
    private final List<DeadLetter> deadLettered = new ArrayList<>();
    private MeterRegistry meters;

    @BeforeAll
    static void connect() {
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
    }

    @AfterAll
    static void disconnect() {
        redisFactory.destroy();
    }

    /**
     * A per-run suffix on every event id, instead of clearing Redis between tests.
     *
     * <p>The guard's cache key carries a seven-day TTL, so a marker written by one run is still
     * there for the next one — and a test that expects to handle {@code evt-twice} for the first
     * time would see a duplicate instead. Deleting the keys in {@code @BeforeEach} fixes that and
     * introduces a worse problem: a scan-and-delete against a shared container is an unbounded
     * blocking call in test setup, and when it does not complete the suite hangs rather than
     * fails.</p>
     *
     * <p>Fresh ids sidestep both. Nothing has to be cleaned up because nothing collides.</p>
     */
    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void reset() {
        handled.clear();
        deadLettered.clear();
        meters = new SimpleMeterRegistry();
    }

    // ------------------------------------------------------------------ retrying

    @Test
    @DisplayName("a transient failure is retried within budget and the message is handled")
    void transientFailureIsRetried() {
        AtomicInteger attempts = new AtomicInteger();
        EventEnvelope envelope = envelope("evt-transient");

        ConsumerDispatch.Outcome outcome = dispatch().deliver(envelope, Mono.defer(() ->
                attempts.incrementAndGet() < 3
                        ? Mono.error(new IllegalStateException("provider unreachable"))
                        : succeed(envelope))).block();

        assertThat(attempts).as("two failures then a success, inside a budget of three").hasValue(3);
        assertThat(outcome).isEqualTo(ConsumerDispatch.Outcome.HANDLED);
        assertThat(deadLettered).as("it recovered — nothing should have been given up on").isEmpty();
    }

    @Test
    @DisplayName("a permanent failure is not retried at all")
    void permanentFailureSkipsTheBudget() {
        AtomicInteger attempts = new AtomicInteger();

        ConsumerDispatch.Outcome outcome = dispatch().deliver(envelope("evt-permanent"), Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new MalformedPayload("amount is not a number"));
        })).block();

        assertThat(attempts)
                .as("retrying a malformed payload produces the same failure three times and a "
                        + "slower path to the dead letter that was always coming")
                .hasValue(1);
        assertThat(outcome).isEqualTo(ConsumerDispatch.Outcome.DEAD_LETTERED);
        assertThat(deadLettered).singleElement()
                .extracting(DeadLetter::reason).isEqualTo(DeadLetter.PERMANENT_FAILURE);
    }

    // ------------------------------------------------------------------ giving up

    @Test
    @DisplayName("exhausting the budget dead-letters with the failure that happened, not with 'retries exhausted'")
    void deadLetterCarriesTheRealReason() {
        EventEnvelope envelope = envelope("evt-poison");

        ConsumerDispatch.Outcome outcome = dispatch().deliver(envelope,
                Mono.error(new IllegalStateException("escrow account is locked by another writer"))).block();

        assertThat(outcome).isEqualTo(ConsumerDispatch.Outcome.DEAD_LETTERED);
        assertThat(deadLettered).singleElement().satisfies(letter -> {
            assertThat(letter.reason()).isEqualTo(DeadLetter.RETRIES_EXHAUSTED);
            assertThat(letter.consumerName())
                    .as("one subscription carries several handlers; the reason is only actionable "
                            + "once you know which of them said it")
                    .isEqualTo(CONSUMER);
            assertThat(letter.subscription()).isEqualTo(SUBSCRIPTION);
            assertThat(letter.eventId()).isEqualTo(envelope.eventId());
            assertThat(letter.eventType()).isEqualTo(envelope.eventType());
            assertThat(letter.description())
                    .as("""
                        Reactor wraps the cause in RetryExhaustedException unless the dispatch \
                        overrides it. Without that override every dead letter the platform ever \
                        writes reads 'Retries exhausted' — technically true, operationally useless.""")
                    .contains("escrow account is locked by another writer")
                    .doesNotContain("Retries exhausted");
        });
    }

    @Test
    @DisplayName("giving up never signals an error back to the binder")
    void dispatchNeverRethrows() {
        assertThatCode(() -> dispatch().deliver(envelope("evt-quiet"),
                Mono.error(new IllegalStateException("still broken"))).block())
                .as("""
                    R6: no bus consumer rethrows. An exception reaching the binder redelivers the \
                    message and eventually dead-letters it with the broker's own reason, \
                    MaxDeliveryCountExceeded, which names no consumer and describes no failure.""")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a message given up on is not marked handled, so a replay re-runs it")
    void deadLetteredWorkIsNotMarkedHandled() {
        EventEnvelope envelope = envelope("evt-replayable");

        dispatch().deliver(envelope, Mono.error(new IllegalStateException("down"))).block();

        AtomicInteger onReplay = new AtomicInteger();
        ConsumerDispatch.Outcome replayed = dispatch().deliver(envelope, Mono.defer(() -> {
            onReplay.incrementAndGet();
            return succeed(envelope);
        })).block();

        assertThat(onReplay)
                .as("ET-ADM-003 replays dead letters. A marker written on failure would make "
                        + "every replay a silent no-op — the queue drains and nothing happens")
                .hasValue(1);
        assertThat(replayed).isEqualTo(ConsumerDispatch.Outcome.HANDLED);
    }

    // ------------------------------------------------------------------ the metric

    @Test
    @DisplayName("dead-letter depth is exported, tagged by subscription, consumer and reason")
    void depthIsExported() {
        dispatch().deliver(envelope("evt-metric-a"), Mono.error(new IllegalStateException("down"))).block();
        dispatch().deliver(envelope("evt-metric-b"), Mono.error(new MalformedPayload("bad"))).block();

        assertThat(meters.find(DeadLetterDepth.PRODUCED_METRIC)
                .tag("subscription", SUBSCRIPTION)
                .tag("consumer", CONSUMER)
                .tag("reason", DeadLetter.RETRIES_EXHAUSTED)
                .counter().count()).isEqualTo(1.0);

        assertThat(meters.find(DeadLetterDepth.PRODUCED_METRIC)
                .tag("reason", DeadLetter.PERMANENT_FAILURE)
                .counter().count())
                .as("a poison message and an outage need different responses, so they need "
                        + "different series — one alert covering both tells you only that "
                        + "something is wrong")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("an observed depth registers a gauge; a subscription never polled has none")
    void depthGaugeIsAbsentUntilObserved() {
        DeadLetterDepth depth = new DeadLetterDepth(meters);

        assertThat(meters.find(DeadLetterDepth.DEPTH_METRIC).gauge())
                .as("a gauge reading zero because nothing polled it is quietest exactly when the "
                        + "platform is worst — absent is the honest state")
                .isNull();

        depth.observedDepth(SUBSCRIPTION, 4);
        assertThat(meters.find(DeadLetterDepth.DEPTH_METRIC)
                .tag("subscription", SUBSCRIPTION).gauge().value()).isEqualTo(4.0);

        depth.observedDepth(SUBSCRIPTION, 0);
        assertThat(meters.find(DeadLetterDepth.DEPTH_METRIC)
                .tag("subscription", SUBSCRIPTION).gauge().value())
                .as("a drained queue must read zero, or the alert never clears").isEqualTo(0.0);
    }

    // ------------------------------------------------------------------ duplicates

    @Test
    @DisplayName("a redelivery of handled work runs nothing and dead-letters nothing")
    void duplicateIsQuiet() {
        EventEnvelope envelope = envelope("evt-twice");
        dispatch().deliver(envelope, succeed(envelope)).block();

        AtomicInteger secondRun = new AtomicInteger();
        ConsumerDispatch.Outcome outcome = dispatch().deliver(envelope,
                Mono.fromRunnable(secondRun::incrementAndGet)).block();

        assertThat(outcome).isEqualTo(ConsumerDispatch.Outcome.DUPLICATE);
        assertThat(secondRun).hasValue(0);
        assertThat(deadLettered).isEmpty();
    }

    // ------------------------------------------------------------------ the budget itself

    @Test
    @DisplayName("a budget that cannot behave as configured is refused at construction")
    void nonsensicalBudgetsAreRefused() {
        assertThatCode(() -> new RetryBudget(0, Duration.ofMillis(1), Duration.ofSeconds(1), 2.0))
                .hasMessageContaining("maxAttempts");
        assertThatCode(() -> new RetryBudget(3, Duration.ofSeconds(10), Duration.ofSeconds(1), 2.0))
                .as("a ceiling below the floor silently shortens the first wait")
                .hasMessageContaining("below initialBackoff");
        assertThatCode(() -> new RetryBudget(3, Duration.ofMillis(1), Duration.ofSeconds(1), 0.5))
                .as("a multiplier below 1 makes each retry sooner than the last")
                .hasMessageContaining("multiplier");
    }

    // ------------------------------------------------------------------ helpers

    private ConsumerDispatch dispatch() {
        ConsumerGuard guard = new ConsumerGuard(
                redis,
                envelope -> Mono.just(handled.contains(envelope.eventId())),
                CONSUMER);

        return new ConsumerDispatch(guard, BUDGET, letter -> {
            deadLettered.add(letter);
            return Mono.empty();
        }, new DeadLetterDepth(meters), CONSUMER, SUBSCRIPTION);
    }

    /** The consumer's effect: its own durable write, which is what makes it idempotent. */
    private Mono<Void> succeed(EventEnvelope envelope) {
        return Mono.fromRunnable(() -> handled.add(envelope.eventId()));
    }

    private static EventEnvelope envelope(String eventId) {
        return new EventEnvelope(eventId + "-" + RUN, "booking.PaymentCompleted", 1,
                Instant.parse("2026-08-19T09:00:00Z"), "corr-1", null, "booking",
                Map.of("paymentId", "pay-1", "eventId", "ev-1"));
    }

    /** A failure retrying cannot fix — see {@link PermanentFailure}. */
    private static final class MalformedPayload extends RuntimeException implements PermanentFailure {
        MalformedPayload(String message) {
            super(message);
        }
    }
}
