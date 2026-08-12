package com.pml.shared.security.revocation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reports whether Redis may discard revocation keys on its own initiative.
 *
 * <p>Under a {@code maxmemory} limit combined with an evicting {@code maxmemory-policy}, Redis
 * can drop a revocation key to reclaim memory without raising an error, so a cache lookup would
 * report the key as absent. The durable store still returns the correct answer; this probe makes
 * the condition visible through the log and the health endpoint so the cache can be moved to a
 * {@code noeviction} instance or a dedicated database.</p>
 *
 * <p>Managed Redis often refuses {@code CONFIG GET}, which is reported as {@link Safety#UNKNOWN}
 * rather than as a failure.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class RedisEvictionPolicyProbe {

    /** Policies under which Redis may discard a revocation key on its own initiative. */
    private static final Set<String> EVICTING_POLICIES = Set.of(
            "allkeys-lru", "allkeys-lfu", "allkeys-random",
            "volatile-lru", "volatile-lfu", "volatile-random", "volatile-ttl");

    public enum Safety {
        /** {@code noeviction}, or no {@code maxmemory} limit — keys are never dropped. */
        SAFE,
        /** An evicting policy with a memory cap — revocation keys can silently disappear. */
        AT_RISK,
        /** Redis would not tell us. Neither reassuring nor alarming. */
        UNKNOWN
    }

    /** The probe result plus the raw values, so the health endpoint can show its working. */
    public record Result(Safety safety, String policy, String maxMemory, String detail) {
    }

    private static final Result NOT_YET_PROBED =
            new Result(Safety.UNKNOWN, "unknown", "unknown", "not probed yet");

    private final ReactiveRedisConnectionFactory connectionFactory;
    private final AtomicReference<Result> lastResult = new AtomicReference<>(NOT_YET_PROBED);

    public Result lastResult() {
        return lastResult.get();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void probeOnStartup() {
        probe().subscribe(
                result -> {
                    if (result.safety() == Safety.AT_RISK) {
                        log.error("""
                                [Revocation] Redis maxmemory-policy is '{}' with maxmemory={}. \
                                Revocation keys may be evicted under memory pressure, in which \
                                case cache lookups for them return absent. The durable store still \
                                answers correctly; move revocation keys to a noeviction instance \
                                or a dedicated Redis database to restore the fast path.""",
                                result.policy(), result.maxMemory());
                    } else {
                        log.info("[Revocation] Redis eviction safety: {} (policy={}, maxmemory={})",
                                result.safety(), result.policy(), result.maxMemory());
                    }
                },
                error -> log.warn("[Revocation] Could not probe Redis eviction policy: {}",
                        error.toString()));
    }

    /** Runs the probe and caches the result. Safe to call repeatedly. */
    public Mono<Result> probe() {
        return Mono.usingWhen(
                        Mono.fromCallable(connectionFactory::getReactiveConnection),
                        connection -> connection.serverCommands().getConfig("maxmemory*"),
                        ReactiveRedisConnection::closeLater)
                .timeout(Duration.ofSeconds(5))
                .map(RedisEvictionPolicyProbe::evaluate)
                .onErrorResume(error -> Mono.just(new Result(
                        Safety.UNKNOWN, "unknown", "unknown",
                        "CONFIG GET unavailable: " + error.getMessage())))
                .doOnNext(lastResult::set);
    }

    private static Result evaluate(Properties config) {
        String policy = config.getProperty("maxmemory-policy", "unknown").trim().toLowerCase();
        String maxMemory = config.getProperty("maxmemory", "unknown").trim();

        boolean capped = isPositive(maxMemory);
        boolean evicting = EVICTING_POLICIES.contains(policy);

        if ("unknown".equals(policy)) {
            return new Result(Safety.UNKNOWN, policy, maxMemory,
                    "Redis did not report maxmemory-policy");
        }
        if (evicting && capped) {
            return new Result(Safety.AT_RISK, policy, maxMemory,
                    "revocation keys are evictable under memory pressure");
        }
        if (evicting) {
            return new Result(Safety.SAFE, policy, maxMemory,
                    "evicting policy, but no maxmemory limit is set so nothing is evicted");
        }
        return new Result(Safety.SAFE, policy, maxMemory, "keys are never evicted");
    }

    private static boolean isPositive(String maxMemory) {
        try {
            return Long.parseLong(maxMemory) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
