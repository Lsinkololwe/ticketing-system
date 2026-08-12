package com.pml.identity.security.revocation;

import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Loads every live revocation from MongoDB into Redis and marks the cache complete.
 *
 * <h2>What this enables</h2>
 * <p>{@link com.pml.shared.security.revocation.CachedRevocationCheck} only treats a cache miss as
 * "not revoked" while {@link RevocationCacheTrust} says the cache holds every live revocation.
 * This warmer is what establishes and maintains that guarantee, so the fast path can serve the
 * common case without a durable lookup per request.</p>
 *
 * <h2>When it runs</h2>
 * <p>At start-up, and on a fixed interval thereafter. The interval both refreshes the sentinel
 * before its TTL lapses and restores it after any event that dropped it — a Redis restart, a
 * flush, an eviction, or a revocation whose cache write failed.</p>
 *
 * <p>Only identity-service runs a warmer, because it owns the data. The sentinel and the keys are
 * shared, so every other service reads a cache this one keeps current.</p>
 *
 * <p>The working set is bounded by the record TTL — roughly one access-token lifespan of
 * sign-outs — so a full reload is a small, cheap scan.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class RevocationCacheWarmer {

    private static final String CACHE_MARKER = "revoked";

    private final RevocationRepository repository;
    private final ReactiveStringRedisTemplate cache;
    private final RevocationCacheTrust cacheTrust;
    private final RevocationProperties properties;
    private final Clock clock;

    private volatile Disposable schedule;

    @EventListener(ApplicationReadyEvent.class)
    public void startWarming() {
        // Periodic reloads refresh the sentinel and repair losses nobody reported; the trust
        // signal reloads immediately when a write is known to have missed the cache.
        schedule = Flux.merge(
                        Flux.interval(Duration.ZERO, properties.getCacheWarmInterval()),
                        cacheTrust.reloadRequests().onBackpressureLatest()
                                .delayElements(Duration.ofMillis(200)))
                .onBackpressureDrop()
                .concatMap(tick -> warm()
                        .onErrorResume(error -> {
                            log.warn("[Revocation] Cache reload failed ({}) — misses will be "
                                            + "resolved against MongoDB until the next attempt",
                                    error.toString());
                            return Mono.just(0L);
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    @PreDestroy
    public void stopWarming() {
        if (schedule != null && !schedule.isDisposed()) {
            schedule.dispose();
        }
    }

    /**
     * Reloads the cache and marks it complete.
     *
     * <p>The sentinel is written last: until every key is in place, a miss must not be trusted.</p>
     *
     * @return the number of revocations loaded
     */
    public Mono<Long> warm() {
        Instant now = clock.instant();

        return repository.findAll()
                .filter(record -> record.isActiveAt(now))
                .concatMap(record -> cache.opsForValue()
                        .set(record.getType().cacheKey(record.getValue()),
                                CACHE_MARKER,
                                Duration.between(now, record.getExpiresAt()))
                        .thenReturn(1L))
                .count()
                .flatMap(loaded -> cacheTrust.markComplete().thenReturn(loaded))
                .doOnNext(loaded -> log.debug(
                        "[Revocation] Cache reloaded with {} live revocation(s)", loaded));
    }
}
