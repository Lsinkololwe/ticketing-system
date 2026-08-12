package com.pml.shared.security.revocation;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * The standard revocation composition: a fast Redis cache in front of an independent
 * {@link DurableRevocationStore}.
 *
 * <h2>Resolution order</h2>
 * <p>Redis answers the common case in roughly a millisecond. On error, timeout or an open
 * circuit the check falls through to the durable store and still returns a definitive answer.
 * {@link RevocationDecision#UNKNOWN} is returned only when both stores are unreachable; what
 * happens then is decided per operation by {@link SensitiveOperationGuard}.</p>
 *
 * <h2>Trusting a miss</h2>
 * <p>A cache hit is conclusive and is returned immediately. A miss is only accepted when
 * {@link RevocationCacheTrust} says the cache is complete; otherwise the check falls through to
 * the durable store, so a revocation the cache never received cannot be masked by its own
 * absence.</p>
 *
 * <h2>Cache budget</h2>
 * <p>The {@link TimeLimiter} budget sits well below {@code spring.data.redis.timeout} so that a
 * slow cache hands over to the durable store rather than adding latency to the request. The
 * {@link CircuitBreaker} records those timeouts and, once open, skips Redis entirely.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class CachedRevocationCheck implements RevocationCheck {

    private final ReactiveStringRedisTemplate cache;
    private final RevocationCacheTrust cacheTrust;
    private final DurableRevocationStore durable;
    private final RevocationProperties properties;
    private final RevocationMetrics metrics;
    private final CircuitBreaker cacheCircuitBreaker;
    private final TimeLimiter cacheTimeLimiter;
    private final Clock clock;

    @Override
    public Mono<RevocationDecision> check(String jti, String sid, String sub) {
        List<RevocationIdentifier> identifiers = RevocationIdentifier.from(jti, sid, sub);

        if (identifiers.isEmpty()) {
            // A token with no jti, sid or sub cannot be matched against any revocation record,
            // so its state is unknown rather than active.
            log.warn("[Revocation] Token carries no jti/sid/sub — it cannot be checked");
            metrics.checkCompleted("none", RevocationDecision.UNKNOWN);
            return Mono.just(RevocationDecision.UNKNOWN);
        }

        long startedAt = clock.millis();
        return checkCache(identifiers)
                .onErrorResume(error -> {
                    String reason = degradationReason(error);
                    metrics.degraded(reason);
                    log.warn("[Revocation] Cache unavailable ({}), using {} instead — "
                                    + "enforcement continues, latency increases",
                            reason, durable.name());
                    return checkDurable(identifiers);
                })
                // Empty means the cache missed while incomplete, so the durable store decides.
                .switchIfEmpty(Mono.defer(() -> {
                    metrics.degraded("cache_incomplete");
                    return checkDurable(identifiers);
                }))
                .doOnNext(decision -> metrics.checkTimer()
                        .record(Duration.ofMillis(clock.millis() - startedAt)));
    }

    /**
     * Resolves against the cache, or completes empty when the cache cannot answer conclusively.
     *
     * <p>The identifier lookups and the completeness sentinel are read together, so establishing
     * whether a miss can be trusted costs one extra key in the same round trip rather than a
     * second one.</p>
     *
     * <p>Errors propagate so that {@link #check} can fall through to the durable store.</p>
     */
    private Mono<RevocationDecision> checkCache(List<RevocationIdentifier> identifiers) {
        Mono<Boolean> anyRevoked = Flux.fromIterable(identifiers)
                .flatMap(identifier -> cache.hasKey(identifier.cacheKey()))
                .any(Boolean::booleanValue);

        return Mono.zip(anyRevoked, cacheTrust.isComplete())
                .flatMap(result -> {
                    boolean revoked = result.getT1();
                    boolean complete = result.getT2();

                    if (revoked) {
                        metrics.checkCompleted("redis", RevocationDecision.REVOKED);
                        return Mono.just(RevocationDecision.REVOKED);
                    }
                    if (complete) {
                        metrics.checkCompleted("redis", RevocationDecision.ACTIVE);
                        return Mono.just(RevocationDecision.ACTIVE);
                    }
                    // Miss against an incomplete cache proves nothing about this token.
                    return Mono.empty();
                })
                .transformDeferred(TimeLimiterOperator.of(cacheTimeLimiter))
                // Breaker outside the limiter so timeouts count towards opening it, and so an
                // open circuit refuses the call before the timeout budget is spent.
                .transformDeferred(CircuitBreakerOperator.of(cacheCircuitBreaker));
    }

    private Mono<RevocationDecision> checkDurable(List<RevocationIdentifier> identifiers) {
        return durable.check(identifiers)
                .timeout(properties.getDurableTimeout())
                .doOnNext(decision -> metrics.checkCompleted(durable.name(), decision))
                .onErrorResume(error -> {
                    metrics.unavailable();
                    metrics.checkCompleted("none", RevocationDecision.UNKNOWN);
                    log.error("[Revocation] {} is unavailable as well ({}) — the revocation "
                                    + "control cannot answer; fail-closed operations will be "
                                    + "refused until a store recovers",
                            durable.name(), error.toString());
                    return Mono.just(RevocationDecision.UNKNOWN);
                });
    }

    private static String degradationReason(Throwable error) {
        if (error instanceof CallNotPermittedException) {
            return "circuit_open";
        }
        if (error instanceof TimeoutException) {
            return "timeout";
        }
        return "error";
    }
}
