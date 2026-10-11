package com.pml.shared.security.revocation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;

/**
 * Tuning for the revocation store and the fail-closed guard.
 *
 * <pre>{@code
 * pml:
 *   security:
 *     revocation:
 *       enabled: true
 *       access-token-lifespan: 5m
 *       clock-skew: 60s
 *       cache-timeout: 250ms
 *       durable-timeout: 2s
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "pml.security.revocation")
public class RevocationProperties {

    /** Master switch. When false the guard admits every request and logs once at start-up. */
    private boolean enabled = true;

    /**
     * How long an issued access token stays valid, mirroring the realm's
     * {@code accessTokenLifespan}. A revocation record must outlive the tokens it revokes, so
     * this drives the default record TTL.
     *
     * <p>Keep this in sync with the realm: {@code myticketzm-realm.json} and the admin realm set
     * {@code accessTokenLifespan: 300}. A value longer than the realm's only keeps records and
     * cache keys longer than needed; a shorter one lets a record expire while a token it revokes
     * is still valid, so it must never be below the realm's.</p>
     */
    private Duration accessTokenLifespan = Duration.ofMinutes(5);

    /** Added to {@link #accessTokenLifespan} so clock drift cannot expire a record early. */
    private Duration clockSkew = Duration.ofSeconds(60);

    /**
     * Budget for the Redis lookup before it is abandoned for the durable store.
     *
     * <p>Set well below the 2s {@code spring.data.redis.timeout} so that a slow cache hands over
     * to the durable store instead of adding latency to every request.</p>
     */
    private Duration cacheTimeout = Duration.ofMillis(250);

    /** Budget for the MongoDB fallback lookup before the check is declared UNKNOWN. */
    private Duration durableTimeout = Duration.ofSeconds(2);

    /**
     * Treat a Redis that can evict revocation keys as untrusted.
     *
     * <p>Under an evicting {@code maxmemory-policy} with a memory limit, a revocation key can be
     * dropped without Redis reporting anything. While the probe reports that risk, a cache miss is
     * not taken as "not revoked": every check a key does not answer goes to the durable store, and
     * the health endpoint reports {@code DEGRADED} with the policy named.</p>
     */
    private boolean requireEvictionSafeCache = true;

    /**
     * TTL on the cache-completeness sentinel. The warmer refreshes it well inside this window,
     * so a warmer that stops running stops vouching for the cache within one TTL.
     */
    private Duration cacheCompletenessTtl = Duration.ofMinutes(10);

    /** How often the cache is reloaded from the durable store and the sentinel refreshed. */
    private Duration cacheWarmInterval = Duration.ofMinutes(2);

    /**
     * identity-service's base URL, for a service that does not own the revocation records and
     * reads them over identity's internal API. Unset in identity itself.
     */
    private String identityUrl;

    /** The longest a revocation must be remembered: a full token lifespan plus skew. */
    public Duration recordTtl() {
        return accessTokenLifespan.plus(clockSkew);
    }

    /**
     * How long a revocation of {@code type} must be remembered, in both MongoDB and Redis.
     *
     * <p>A revocation only has to outlive the tokens it can still match. A {@code TOKEN}
     * revocation matches exactly one token, so it is kept until that token's own {@code exp}
     * plus skew, never longer than a full lifespan: revoking a token that is about to expire
     * holds a key for seconds rather than minutes, and revoking one that has already expired
     * holds only the skew. A {@code SESSION} or {@code USER} revocation also matches tokens
     * minted later, so it is kept for a full lifespan plus skew from the revocation.</p>
     *
     * @param tokenExpiresAt the revoked token's {@code exp}, or null when it is not known
     */
    public Duration ttlFor(RevocationType type, Instant tokenExpiresAt, Instant now) {
        Duration full = recordTtl();
        if (type != RevocationType.TOKEN || tokenExpiresAt == null) {
            return full;
        }
        Duration remaining = Duration.between(now, tokenExpiresAt);
        Duration needed = (remaining.isNegative() ? Duration.ZERO : remaining).plus(clockSkew);
        return needed.compareTo(full) < 0 ? needed : full;
    }

    /**
     * Refuses a configuration that would weaken the check: a durable budget that is not longer
     * than the cache budget makes the fallback give up before the cache would have, and a
     * non-positive lifespan or negative skew makes records expire before their tokens do.
     */
    @PostConstruct
    void validate() {
        if (accessTokenLifespan.isZero() || accessTokenLifespan.isNegative()) {
            throw new IllegalStateException(
                    "pml.security.revocation.access-token-lifespan must be positive");
        }
        if (clockSkew.isNegative()) {
            throw new IllegalStateException("pml.security.revocation.clock-skew must not be negative");
        }
        if (cacheWarmInterval.isZero() || cacheWarmInterval.isNegative()
                || cacheCompletenessTtl.compareTo(cacheWarmInterval.multipliedBy(2)) < 0) {
            throw new IllegalStateException("pml.security.revocation.cache-completeness-ttl ("
                    + cacheCompletenessTtl + ") must be at least twice cache-warm-interval ("
                    + cacheWarmInterval + "), or one late reload leaves the cache untrusted");
        }
        if (cacheTimeout.isZero() || cacheTimeout.isNegative()
                || durableTimeout.compareTo(cacheTimeout) <= 0) {
            throw new IllegalStateException("pml.security.revocation.durable-timeout ("
                    + durableTimeout + ") must be longer than cache-timeout (" + cacheTimeout + ")");
        }
    }
}
