package com.pml.shared.security.revocation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tuning for the revocation store and the fail-closed guard.
 *
 * <pre>{@code
 * pml:
 *   security:
 *     revocation:
 *       enabled: true
 *       access-token-lifespan: 1h
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
     * <p>Keep this in sync with the realm: {@code myticketzm-realm.json} currently sets
     * {@code accessTokenLifespan: 3600}. The value bounds how long a revoked token could remain
     * usable if a revocation is ever missed.</p>
     */
    private Duration accessTokenLifespan = Duration.ofHours(1);

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
     * Treat an evicting Redis {@code maxmemory-policy} as a health problem.
     *
     * <p>Under an evicting policy a revocation key can be dropped without Redis reporting an
     * error. The durable store still answers correctly; this flag surfaces the condition through
     * the health endpoint.</p>
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

    /** The TTL a new revocation record gets: token lifespan plus skew. */
    public Duration recordTtl() {
        return accessTokenLifespan.plus(clockSkew);
    }
}
