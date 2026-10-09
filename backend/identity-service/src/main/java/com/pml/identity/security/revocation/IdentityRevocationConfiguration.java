package com.pml.identity.security.revocation;

import com.pml.shared.security.revocation.DurableRevocationStore;
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationMetrics;
import com.pml.shared.security.revocation.RevocationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Clock;

/**
 * Binds the shared revocation SPI to identity-service's own MongoDB collection.
 *
 * <p>This is the service-exclusive half. The guard, cache composition, circuit breaker, metrics
 * and health indicator all come from {@code com.pml.shared.security.revocation} and are the same
 * in every service; the durable store is the one part each service chooses, and identity-service
 * answers from the collection it owns.</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "pml.security.revocation", name = "enabled", havingValue = "true")
public class IdentityRevocationConfiguration {

    /**
     * The single {@link DurableRevocationStore} in this service.
     *
     * <p>Declared as the concrete type because {@link InternalRevocationController} needs the
     * write methods that only the owner has; the shared cache composition injects it by its
     * interface. Registering it twice would leave two candidates for the SPI type.</p>
     */
    @Bean
    public MongoRevocationStore mongoRevocationStore(RevocationRepository repository,
                                                     ReactiveStringRedisTemplate cache,
                                                     RevocationCacheTrust revocationCacheTrust,
                                                     RevocationProperties properties,
                                                     RevocationMetrics metrics,
                                                     Clock revocationClock) {
        log.info("[Revocation] identity-service owns the revocation system of record "
                + "(collection: token_revocations)");
        return new MongoRevocationStore(repository, cache, revocationCacheTrust, properties,
                metrics, revocationClock);
    }

    /**
     * Only identity-service warms the cache, because it owns the data. The keys and the
     * completeness sentinel are shared, so every other service reads a cache this one maintains.
     */
    @Bean
    public RevocationCacheWarmer revocationCacheWarmer(RevocationRepository repository,
                                                       ReactiveStringRedisTemplate cache,
                                                       RevocationCacheTrust revocationCacheTrust,
                                                       RevocationProperties properties,
                                                       Clock revocationClock) {
        return new RevocationCacheWarmer(repository, cache, revocationCacheTrust, properties,
                revocationClock);
    }

    /**
     * Declared here as well as in the shared auto-configuration so that bean ordering is
     * deterministic: the auto-configuration's {@code @ConditionalOnBean(DurableRevocationStore)}
     * check needs this class's contributions to be present first.
     */

    @Bean
    public RevocationMetrics revocationMetrics(MeterRegistry meterRegistry, Clock clock) {
        return new RevocationMetrics(meterRegistry, clock);
    }
}
