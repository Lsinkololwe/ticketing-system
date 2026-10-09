package com.pml.shared.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Makes a consumer idempotent on {@code eventId}.
 *
 * <h2>Why every consumer needs one</h2>
 * Azure Service Bus delivers at least once, by design. A consumer that issues a ticket on
 * {@code PaymentCompleted} and sees the message twice issues two tickets — and the second is
 * indistinguishable from a legitimate second purchase, so nothing downstream can detect it
 * either.
 *
 * <h2>Redis is the fast path, never the authority</h2>
 * {@code evt:seen:{consumer}:{eventId}} with a seven-day TTL answers most calls without touching
 * MongoDB. But Redis holds no business state on this platform, and a guard that
 * trusted only Redis would lose its memory on a {@code FLUSHALL} — every message redelivered
 * after that would be processed a second time. So a Redis miss falls through to the durable
 * marker rather than being treated as "not seen".
 *
 * <h2>The durable answer is the consumer's own write</h2>
 * The authority is whatever the consumer
 * already persists — an escrow account keyed on the event, a ticket carrying the
 * {@code paymentIntentId} — checked through {@link DurablyHandled}. That marker is atomic with
 * the effect it guards, which a separate bookkeeping collection is not: a marker written beside
 * the work can succeed while the work rolls back, and then the message is never retried.
 *
 * <h2>Marked after the work, not before</h2>
 * The marker could be set with {@code SET evt:seen NX EX 604800} <em>before</em> the work. This
 * marks after, and the difference is a real trade rather than an oversight:
 *
 * <ul>
 *   <li><b>Before</b> stops two concurrent deliveries doing the work twice. But a consumer that
 *       crashes mid-work leaves the key set for seven days, so every redelivery inside that
 *       window is skipped and the work never happens.</li>
 *   <li><b>After</b> lets two concurrent deliveries both run — which is safe precisely because
 *       the durable check is the consumer's own idempotent write — and a crash mid-work
 *       redelivers normally.</li>
 * </ul>
 *
 * <p>The second is chosen because the failure it accepts is one the durable write absorbs, and
 * the failure it avoids is silent.
 */
@Slf4j
public final class ConsumerGuard {

    private static final Duration CACHE_TTL = Duration.ofDays(7);

    /**
     * Whether this consumer's own persisted state already reflects the event.
     *
     * <p>Supplied by the consumer because only the consumer knows what its effect looks like:
     * "does an escrow account exist for this event", "does a ticket carry this
     * paymentIntentId". That check is atomic with the effect, which is what makes it the
     * authority rather than a second record that can disagree with it.</p>
     */
    @FunctionalInterface
    public interface DurablyHandled {
        Mono<Boolean> check(EventEnvelope envelope);
    }

    private final ReactiveStringRedisTemplate cache;
    private final DurablyHandled durable;
    private final String consumerName;

    public ConsumerGuard(ReactiveStringRedisTemplate cache,
                         DurablyHandled durable,
                         String consumerName) {
        this.cache = cache;
        this.durable = durable;
        this.consumerName = consumerName;
    }

    /**
     * Runs {@code work} unless this envelope has already been handled by this consumer.
     *
     * @return true when the work ran, false when the delivery was a duplicate
     */
    public Mono<Boolean> runOnce(EventEnvelope envelope, Mono<Void> work) {
        String eventId = envelope.eventId();

        return alreadyHandled(envelope)
                .flatMap(seen -> {
                    if (seen) {
                        log.debug("[{}] {} already handled — skipping duplicate delivery",
                                consumerName, eventId);
                        return Mono.just(false);
                    }
                    return work.then(mark(eventId)).thenReturn(true);
                });
    }

    private Mono<Boolean> alreadyHandled(EventEnvelope envelope) {
        return cache.hasKey(cacheKey(envelope.eventId()))
                .flatMap(cached -> Boolean.TRUE.equals(cached)
                        ? Mono.just(true)
                        // A cache miss is not an answer. Redis may have been flushed, evicted
                        // under memory pressure, or simply never told — the consumer's own
                        // state is what decides.
                        : durable.check(envelope))
                .onErrorResume(error -> {
                    log.warn("[{}] idempotency cache unavailable ({}), asking the consumer's own state",
                            consumerName, error.getMessage());
                    return durable.check(envelope);
                });
    }

    private Mono<Void> mark(String eventId) {
        return cache.opsForValue().set(cacheKey(eventId), "1", CACHE_TTL)
                .onErrorResume(error -> {
                    // The cache is an optimisation. Losing the write costs a redundant durable
                    // check on the next delivery, not correctness.
                    log.warn("[{}] could not cache the marker for {}: {}",
                            consumerName, eventId, error.getMessage());
                    return Mono.just(true);
                })
                .then();
    }

    /** {@code evt:seen:{consumer}:{eventId}} — the platform's registered Redis key shape. */
    private String cacheKey(String eventId) {
        return "evt:seen:" + consumerName + ":" + eventId;
    }

}
