package com.pml.shared.security.revocation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks whether the cache holds every live revocation, which determines when a cache miss may
 * be treated as an answer.
 *
 * <h2>Why a miss is not an answer on its own</h2>
 * <p>A cache hit is conclusive: the key is only ever written for a real revocation. A miss is
 * not, because the key may be absent for reasons that have nothing to do with the token — the
 * cache was unreachable when the revocation was written, was restarted or flushed, or evicted
 * the key under memory pressure. Treating those as "not revoked" would admit a revoked token
 * while both stores are healthy.</p>
 *
 * <p>The sentinel resolves that. It is written only after the cache has been loaded with every
 * live revocation, and is dropped as soon as anything could have made the cache incomplete.
 * While it is absent, a miss falls through to the durable store; hits are still served from the
 * cache. Reads cost one extra {@code EXISTS} issued alongside the identifier lookups.</p>
 *
 * <p>The sentinel is a single shared key, so one warmer — identity-service, which owns the data
 * — keeps every service's cache reads trustworthy.</p>
 */
@Slf4j
public class RevocationCacheTrust {

    /** Present only while the cache is known to hold every live revocation. */
    public static final String SENTINEL_KEY = "pml:revocation:cache-complete";

    private final ReactiveStringRedisTemplate cache;

    /**
     * TTL on the sentinel, so a warmer that dies stops vouching for a cache it is no longer
     * maintaining. The warmer refreshes it well inside this window.
     */
    private final Duration sentinelTtl;

    /**
     * Set when this process knows a revocation failed to reach the cache.
     *
     * <p>The shared sentinel covers losses visible to everyone — a restart, a flush, an eviction.
     * It cannot cover a failed write, because the same outage that lost the write also prevents
     * deleting the sentinel. This flag survives that: the process that failed the write stops
     * trusting misses immediately, without needing Redis to agree.</p>
     */
    private final AtomicBoolean locallyIncomplete = new AtomicBoolean(false);

    /** Emits whenever the cache becomes incomplete, so a warmer can reload without waiting. */
    private final Sinks.Many<Long> reloadRequests =
            Sinks.many().multicast().onBackpressureBuffer(1, false);

    public RevocationCacheTrust(ReactiveStringRedisTemplate cache, Duration sentinelTtl) {
        this.cache = cache;
        this.sentinelTtl = sentinelTtl;
    }

    /** True when a cache miss may be taken as "not revoked". */
    public Mono<Boolean> isComplete() {
        if (locallyIncomplete.get()) {
            return Mono.just(false);
        }
        return cache.hasKey(SENTINEL_KEY);
    }

    /**
     * Declares the cache loaded with every live revocation.
     *
     * <p>Clears the local flag only after the sentinel is written, so a reload that fails
     * part-way does not restore trust.</p>
     */
    public Mono<Boolean> markComplete() {
        return cache.opsForValue()
                .set(SENTINEL_KEY, "1", sentinelTtl)
                .doOnNext(written -> {
                    if (Boolean.TRUE.equals(written)) {
                        locallyIncomplete.set(false);
                    }
                });
    }

    /**
     * Withdraws the guarantee, sending subsequent misses to the durable store, and asks for a
     * reload.
     *
     * <p>The local flag takes effect immediately. The sentinel delete is attempted as well so
     * other services stop trusting misses too; when Redis is the thing that failed, that delete
     * cannot succeed and the reload restores a correct cache instead.</p>
     */
    public Mono<Void> invalidate() {
        boolean firstTime = locallyIncomplete.compareAndSet(false, true);
        if (firstTime) {
            log.warn("[Revocation] Cache marked incomplete — misses will be resolved against the "
                    + "durable store until it is reloaded");
        }

        return cache.delete(SENTINEL_KEY)
                .onErrorResume(error -> Mono.just(0L))
                .doFinally(signal -> reloadRequests.tryEmitNext(System.nanoTime()))
                .then();
    }

    /** Reload requests raised by {@link #invalidate()}. */
    public Flux<Long> reloadRequests() {
        return reloadRequests.asFlux();
    }
}
