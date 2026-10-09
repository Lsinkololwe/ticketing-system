package com.pml.shared.security.revocation;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

import java.time.Clock;
import java.time.Duration;

/**
 * Wires the revocation control for any service that opts in.
 *
 * <h2>What is shared and what is not</h2>
 * <p>The cache composition, circuit breaker, metrics, guard, aspect and health indicator are
 * defined here and are identical in every service, so the fail-closed policy cannot drift
 * between them.</p>
 *
 * <p>Each service supplies its own {@link DurableRevocationStore} bean, whose failure mode must
 * be uncorrelated with Redis: identity-service binds it to the MongoDB collection it owns,
 * other services bind it to {@link HttpDurableRevocationStore}. With no such bean present this
 * configuration backs off rather than wiring a cache-only check.</p>
 *
 * <h2>Enabling</h2>
 * <pre>{@code
 * pml:
 *   security:
 *     revocation:
 *       enabled: true
 * }</pre>
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass({ReactiveStringRedisTemplate.class, CircuitBreaker.class})
@ConditionalOnProperty(prefix = "pml.security.revocation", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RevocationProperties.class)
public class RevocationAutoConfiguration {

    /** Resilience4j instance name; override via {@code resilience4j.*.instances.revocationCache}. */
    public static final String CACHE_RESILIENCE_INSTANCE = "revocationCache";


    @Bean
    @ConditionalOnMissingBean
    public RevocationMetrics revocationMetrics(MeterRegistry meterRegistry, Clock clock) {
        return new RevocationMetrics(meterRegistry, clock);
    }

    /**
     * Uses the application's Resilience4j registry when present, so the breaker appears in
     * actuator and can be tuned in {@code application.yml}; otherwise builds an equivalent
     * standalone instance.
     */
    @Bean
    @ConditionalOnMissingBean(name = "revocationCacheCircuitBreaker")
    public CircuitBreaker revocationCacheCircuitBreaker(
            ObjectProvider<CircuitBreakerRegistry> registryProvider) {

        CircuitBreakerRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            return registry.circuitBreaker(CACHE_RESILIENCE_INSTANCE);
        }
        return CircuitBreaker.of(CACHE_RESILIENCE_INSTANCE, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build());
    }

    @Bean
    @ConditionalOnMissingBean(name = "revocationCacheTimeLimiter")
    public TimeLimiter revocationCacheTimeLimiter(
            ObjectProvider<TimeLimiterRegistry> registryProvider,
            RevocationProperties properties) {

        TimeLimiterRegistry registry = registryProvider.getIfAvailable();
        if (registry != null) {
            return registry.timeLimiter(CACHE_RESILIENCE_INSTANCE);
        }
        return TimeLimiter.of(CACHE_RESILIENCE_INSTANCE, TimeLimiterConfig.custom()
                .timeoutDuration(properties.getCacheTimeout())
                .cancelRunningFuture(true)
                .build());
    }

    @Bean
    @ConditionalOnMissingBean
    public RevocationCacheTrust revocationCacheTrust(ReactiveStringRedisTemplate cache,
                                                     RevocationProperties properties) {
        return new RevocationCacheTrust(cache, properties.getCacheCompletenessTtl());
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisEvictionPolicyProbe redisEvictionPolicyProbe(
            ReactiveRedisConnectionFactory connectionFactory) {
        return new RedisEvictionPolicyProbe(connectionFactory);
    }

    /** Backs off when no {@link DurableRevocationStore} is present rather than running cache-only. */
    @Bean
    @ConditionalOnBean(DurableRevocationStore.class)
    @ConditionalOnMissingBean(RevocationCheck.class)
    public RevocationCheck revocationCheck(ReactiveStringRedisTemplate cache,
                                           RevocationCacheTrust revocationCacheTrust,
                                           DurableRevocationStore durable,
                                           RevocationProperties properties,
                                           RevocationMetrics metrics,
                                           CircuitBreaker revocationCacheCircuitBreaker,
                                           TimeLimiter revocationCacheTimeLimiter) {
        log.info("[Revocation] Enabled — Redis cache in front of {} (independent stores, "
                        + "fail-closed on @FailClosedOnRevocation operations)",
                durable.name());
        return new CachedRevocationCheck(cache, revocationCacheTrust, durable, properties,
                metrics, revocationCacheCircuitBreaker, revocationCacheTimeLimiter);
    }

    @Bean
    @ConditionalOnBean(RevocationCheck.class)
    @ConditionalOnMissingBean
    public RevocationRequestGuard revocationRequestGuard(RevocationCheck revocationCheck,
                                                         RevocationMetrics metrics) {
        return new RevocationRequestGuard(revocationCheck, metrics);
    }

    @Bean
    @ConditionalOnBean(RevocationCheck.class)
    @ConditionalOnMissingBean
    public SensitiveOperationGuard sensitiveOperationGuard(RevocationCheck revocationCheck,
                                                           RevocationProperties properties,
                                                           RevocationMetrics metrics) {
        return new SensitiveOperationGuard(revocationCheck, properties, metrics);
    }

    @Bean
    @ConditionalOnBean(SensitiveOperationGuard.class)
    @ConditionalOnMissingBean
    public RevocationGuardAspect revocationGuardAspect(SensitiveOperationGuard guard) {
        return new RevocationGuardAspect(guard);
    }

    @Bean
    @ConditionalOnClass(ReactiveHealthIndicator.class)
    @ConditionalOnBean(DurableRevocationStore.class)
    @ConditionalOnMissingBean(name = "revocationHealthIndicator")
    public RevocationHealthIndicator revocationHealthIndicator(
            ReactiveStringRedisTemplate cache,
            DurableRevocationStore durable,
            RedisEvictionPolicyProbe evictionProbe,
            RevocationMetrics metrics,
            CircuitBreaker revocationCacheCircuitBreaker,
            RevocationProperties properties) {
        return new RevocationHealthIndicator(cache, durable, evictionProbe, metrics,
                revocationCacheCircuitBreaker, properties);
    }
}
