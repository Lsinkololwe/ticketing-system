package com.pml.identity.security.revocation;

import com.pml.shared.security.revocation.DurableRevocationStore;
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationDecision;
import com.pml.shared.security.revocation.RevocationIdentifier;
import com.pml.shared.security.revocation.RevocationMetrics;
import com.pml.shared.security.revocation.RevocationProperties;
import com.pml.shared.security.revocation.RevocationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * identity-service's binding of the durable revocation store, plus the only write path in the
 * platform.
 *
 * <h2>Ownership</h2>
 * <p>identity-service owns identity data and therefore the revocation system of record, so the
 * collection and the write path live here rather than in shared-library. Other services read
 * through this service's internal API, keeping a single writer and a single copy of the data.
 * The policy is shared; the storage is not.</p>
 *
 * <h2>Durable-first writes</h2>
 * <p>{@link #revoke} persists to MongoDB before writing the cache and propagates the error if
 * the persist fails, so a caller learns when a revocation did not land. The Redis write is
 * best-effort: the record is already durable, so a cache miss costs a fallback lookup.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class MongoRevocationStore implements DurableRevocationStore {

    /** Every reader uses {@code EXISTS}, so the cached value is informational only. */
    private static final String CACHE_MARKER = "revoked";

    private static final int MAX_REASON = 500;

    private final RevocationRepository repository;
    private final ReactiveStringRedisTemplate cache;
    private final RevocationCacheTrust cacheTrust;
    private final RevocationProperties properties;
    private final RevocationMetrics metrics;
    private final Clock clock;

    // =========================================================================
    // DurableRevocationStore
    // =========================================================================

    @Override
    public Mono<RevocationDecision> check(Collection<RevocationIdentifier> identifiers) {
        Instant now = clock.instant();
        List<String> ids = identifiers.stream().map(RevocationIdentifier::storageId).toList();

        return repository.findAllById(ids)
                // The TTL monitor sweeps roughly once a minute, so an expired record can still
                // be readable; filter it out against the current clock.
                .filter(record -> record.isActiveAt(now))
                .hasElements()
                .map(found -> found ? RevocationDecision.REVOKED : RevocationDecision.ACTIVE);
    }

    @Override
    public Mono<Void> ping() {
        return repository.count().then();
    }

    @Override
    public String name() {
        return "mongodb";
    }

    // =========================================================================
    // Writes — identity-service only
    // =========================================================================

    /**
     * Records a revocation durably, then primes the cache.
     *
     * @return the persisted record
     * @throws IllegalArgumentException via the {@link Mono} for a blank identifier
     */
    public Mono<RevocationRecord> revoke(RevocationType type, String value,
                                         String reason, String revokedBy) {
        return write(type, value, RevocationTrigger.fromCode(reason), reason, revokedBy, null, false);
    }

    /**
     * Records a revocation with its trigger.
     *
     * <p>A trigger that a person answers for ({@link RevocationTrigger#requiresWrittenReason()})
     * must come with a written reason of at least {@link RevocationTrigger#MIN_WRITTEN_REASON}
     * characters, and is refused otherwise. Every other trigger is a machine event whose reason
     * is a short code; the stored reason is then the trigger's description followed by the
     * code, so no record is ever stored with less than a sentence of explanation.</p>
     *
     * @param tokenExpiresAt the revoked token's {@code exp}, when known, so a single-token
     *                       revocation is remembered only until that token would have expired
     */
    public Mono<RevocationRecord> revoke(RevocationType type, String value, RevocationTrigger trigger,
                                         String reason, String revokedBy, Instant tokenExpiresAt) {
        return write(type, value, trigger, reason, revokedBy, tokenExpiresAt, true);
    }

    /**
     * @param enforceWrittenReason false only for the reason-code entry point, whose callers are
     *                             services and web apps sending machine codes, never a person
     */
    private Mono<RevocationRecord> write(RevocationType type, String value, RevocationTrigger trigger,
                                         String reason, String revokedBy, Instant tokenExpiresAt,
                                         boolean enforceWrittenReason) {
        if (value == null || value.isBlank()) {
            return Mono.error(new IllegalArgumentException(
                    "Cannot revoke a blank " + type + " identifier"));
        }
        String written = reason == null ? "" : reason.trim();
        if (enforceWrittenReason && trigger.requiresWrittenReason()
                && written.length() < RevocationTrigger.MIN_WRITTEN_REASON) {
            return Mono.error(new IllegalArgumentException("A written reason of at least "
                    + RevocationTrigger.MIN_WRITTEN_REASON + " characters is required"));
        }
        String stored = written.length() >= RevocationTrigger.MIN_WRITTEN_REASON
                ? written
                : trigger.description() + (written.isEmpty() ? "" : " (" + written + ")");
        stored = stored.substring(0, Math.min(stored.length(), MAX_REASON));

        Instant now = clock.instant();
        RevocationRecord record = RevocationRecord.builder()
                .id(type.documentId(value))
                .type(type)
                .value(value)
                .reason(stored)
                .trigger(trigger)
                .revokedBy(revokedBy)
                .revokedAt(now)
                .expiresAt(now.plus(properties.ttlFor(type, tokenExpiresAt, now)))
                .build();

        return insertOnce(record, now)
                .timeout(properties.getDurableTimeout())
                .doOnError(error -> {
                    metrics.writeCompleted("durable_failed");
                    log.error("[Revocation] Could not persist revocation of {} — the token "
                                    + "remains valid until it expires on its own",
                            new RevocationIdentifier(type, value).masked(), error);
                })
                .flatMap(saved -> primeCache(saved).thenReturn(saved));
    }

    /**
     * Writes the record, or, if an active one already exists for the identifier, extends that
     * one's window instead of inserting a second row: the original {@code reason}, {@code
     * revokedBy} and {@code revokedAt} are kept — they describe the first cause — but {@code
     * expiresAt} moves out to whichever of the two records runs later. A revocation that only
     * returned the existing row unchanged would let a later cause (a suspension issued after an
     * earlier logout) expire before the tokens it was meant to cover, as soon as the earlier
     * row's own window ran out first. The id is derived from type and value, so two concurrent
     * writers cannot both insert. A record that is past its expiry but not yet swept by the TTL
     * monitor no longer revokes anything, so it is replaced rather than extended.
     */
    private Mono<RevocationRecord> insertOnce(RevocationRecord record, Instant now) {
        return repository.insert(record)
                .onErrorResume(DuplicateKeyException.class, duplicate -> repository.findById(record.getId())
                        .flatMap(existing -> existing.isActiveAt(now)
                                ? extendIfLater(existing, record.getExpiresAt())
                                : repository.save(record))
                        .switchIfEmpty(Mono.defer(() -> repository.save(record))));
    }

    /** Pushes {@code existing}'s expiry out to {@code candidateExpiresAt} when that is later. */
    private Mono<RevocationRecord> extendIfLater(RevocationRecord existing, Instant candidateExpiresAt) {
        if (!candidateExpiresAt.isAfter(existing.getExpiresAt())) {
            return Mono.just(existing);
        }
        existing.setExpiresAt(candidateExpiresAt);
        return repository.save(existing);
    }

    /**
     * Lifts a revocation, for the one case where it should not outlive its cause: an unsuspended
     * account must be able to sign in again before the record would have expired on its own.
     * The durable record goes first; a cache key that cannot be removed ages out with its TTL.
     */
    public Mono<Void> lift(RevocationType type, String value) {
        if (value == null || value.isBlank()) {
            return Mono.empty();
        }
        return repository.deleteById(type.documentId(value))
                .timeout(properties.getDurableTimeout())
                .then(cache.delete(type.cacheKey(value))
                        .onErrorResume(error -> {
                            log.warn("[Revocation] Lifted {} but could not clear its cache key ({})",
                                    new RevocationIdentifier(type, value).masked(), error.toString());
                            return Mono.just(0L);
                        })
                        .then());
    }

    /** Best-effort cache population. Never fails the enclosing write. */
    private Mono<Void> primeCache(RevocationRecord record) {
        Duration ttl = Duration.between(clock.instant(), record.getExpiresAt());
        if (ttl.isZero() || ttl.isNegative()) {
            metrics.writeCompleted("ok");
            return Mono.empty();
        }

        return cache.opsForValue()
                .set(record.getType().cacheKey(record.getValue()), CACHE_MARKER, ttl)
                .doOnNext(stored -> metrics.writeCompleted(
                        Boolean.TRUE.equals(stored) ? "ok" : "cache_failed"))
                .onErrorResume(error -> {
                    metrics.writeCompleted("cache_failed");
                    log.warn("[Revocation] Revocation of {} is durable but was not cached ({}) "
                                    + "— checks will use the durable path until Redis recovers",
                            new RevocationIdentifier(record.getType(), record.getValue()).masked(),
                            error.toString());
                    // The cache is now missing a live revocation, so a miss must stop counting
                    // as an answer until the warmer reloads it.
                    return cacheTrust.invalidate().thenReturn(false);
                })
                .then();
    }
}
