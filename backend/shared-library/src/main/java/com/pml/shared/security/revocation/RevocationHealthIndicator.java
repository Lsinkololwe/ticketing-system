package com.pml.shared.security.revocation;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Reports whether the revocation control is actually working.
 *
 * <p>Together with the counters in {@link RevocationMetrics}, this exposes whether revocation is
 * currently enforceable as a dashboard- and alert-visible signal.</p>
 *
 * <h2>Statuses</h2>
 * <ul>
 *   <li>{@code UP} — both stores answered. Revocation is enforced.</li>
 *   <li>{@code DEGRADED} — one store is down, or the cache is eviction-prone. Answers remain
 *       correct, and the instance stays in rotation.</li>
 *   <li>{@code DOWN} — neither store answered. Fail-closed operations are being refused.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class RevocationHealthIndicator implements ReactiveHealthIndicator {

    /** Correct answers with reduced capability. Maps to HTTP 200 and does not fail readiness. */
    public static final Status DEGRADED = new Status("DEGRADED");

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

    private final ReactiveStringRedisTemplate cache;
    private final DurableRevocationStore durable;
    private final RedisEvictionPolicyProbe evictionProbe;
    private final RevocationMetrics metrics;
    private final CircuitBreaker cacheCircuitBreaker;
    private final RevocationProperties properties;

    @Override
    public Mono<Health> health() {
        return Mono.zip(probeCache(), probeDurable())
                .map(results -> describe(results.getT1(), results.getT2()))
                .onErrorResume(error -> Mono.just(Health.down(error)
                        .withDetail("component", "revocation")
                        .build()));
    }

    private Mono<Boolean> probeCache() {
        return cache.hasKey("pml:revocation:health-probe")
                .map(found -> true)
                .timeout(PROBE_TIMEOUT)
                .onErrorReturn(false);
    }

    private Mono<Boolean> probeDurable() {
        return durable.ping()
                .thenReturn(true)
                .timeout(PROBE_TIMEOUT)
                .onErrorReturn(false);
    }

    private Health describe(boolean cacheUp, boolean durableUp) {
        RedisEvictionPolicyProbe.Result eviction = evictionProbe.lastResult();

        Health.Builder builder = Health.status(statusFor(cacheUp, durableUp, eviction))
                .withDetail("enforced", cacheUp || durableUp)
                .withDetail("cache", cacheUp ? "up" : "down")
                .withDetail("durableStore", durable.name() + ":" + (durableUp ? "up" : "down"))
                .withDetail("cacheCircuitBreaker", cacheCircuitBreaker.getState().name())
                .withDetail("cacheEvictionSafety", eviction.safety().name())
                .withDetail("cacheMaxmemoryPolicy", eviction.policy())
                .withDetail("accessTokenLifespan", properties.getAccessTokenLifespan().toString())
                .withDetail("lastDegradedAt", asInstant(metrics.lastDegradedAt()))
                .withDetail("lastUnavailableAt", asInstant(metrics.lastUnavailableAt()));

        if (!cacheUp && !durableUp) {
            builder.withDetail("impact",
                    "Revocation cannot be resolved. Operations annotated "
                            + "@FailClosedOnRevocation are being refused with 503.");
        } else if (!durableUp) {
            builder.withDetail("impact",
                    "Durable store unreachable: revocation reads still work from cache, but new "
                            + "revocations cannot be persisted and would be lost.");
        } else if (!cacheUp) {
            builder.withDetail("impact",
                    "Cache unreachable: every check is served by " + durable.name()
                            + ". Correct, but slower.");
        } else if (eviction.safety() == RedisEvictionPolicyProbe.Safety.AT_RISK) {
            builder.withDetail("impact",
                    "Cache may evict revocation keys silently (" + eviction.detail()
                            + "). The durable store still answers correctly.");
        }

        return builder.build();
    }

    private Status statusFor(boolean cacheUp, boolean durableUp,
                             RedisEvictionPolicyProbe.Result eviction) {
        if (!cacheUp && !durableUp) {
            return Status.DOWN;
        }
        if (!cacheUp || !durableUp) {
            return DEGRADED;
        }
        boolean evictionConcern = properties.isRequireEvictionSafeCache()
                && eviction.safety() == RedisEvictionPolicyProbe.Safety.AT_RISK;
        return evictionConcern ? DEGRADED : Status.UP;
    }

    private static String asInstant(long epochMillis) {
        return epochMillis == 0 ? "never" : Instant.ofEpochMilli(epochMillis).toString();
    }
}
