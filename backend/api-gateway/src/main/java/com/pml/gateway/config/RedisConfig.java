package com.pml.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for the API Gateway.
 *
 * <h2>Redis Usage in Gateway</h2>
 * <pre>
 * 1. RATE LIMITING (via RequestRateLimiter filter)
 *    ────────────────────────────────────────────────
 *    Keys: request_rate_limiter.{key}.tokens, request_rate_limiter.{key}.timestamp
 *    Purpose: Token bucket counters per IP/user
 *    Managed by: Spring Cloud Gateway (automatic)
 *
 * 2. SESSION BLACKLIST (via SessionBlacklistFilter)
 *    ────────────────────────────────────────────────
 *    Keys: session:blacklist:{sid}, session:blacklist:user:{sub}
 *    Purpose: Track revoked sessions from Keycloak back-channel logout
 *    Managed by: This application (SessionBlacklistService)
 *
 * Flow for back-channel logout:
 *   1. User logs out in Keycloak
 *   2. Keycloak sends logout event to registered clients
 *   3. Client writes session ID to Redis: SET session:blacklist:{sid} "revoked" EX 3600
 *   4. SessionBlacklistFilter checks Redis on every authenticated request
 *   5. If found → 401 Unauthorized (even if JWT is technically valid)
 * </pre>
 *
 * <h2>Why ReactiveRedisTemplate?</h2>
 * <p>
 * Spring Cloud Gateway uses WebFlux (reactive/non-blocking). Using blocking Redis
 * operations would defeat the purpose. ReactiveRedisTemplate returns Mono/Flux
 * which integrate with the reactive pipeline.
 * </p>
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a ReactiveRedisTemplate with String serializers.
     *
     * Why String serializers?
     * - Session blacklist stores simple string values ("revoked")
     * - Rate limiter keys are strings (IP addresses, user IDs)
     * - String serialization is fast and human-readable in Redis CLI
     *
     * @param connectionFactory Auto-configured by Spring Boot from application.yml
     * @return ReactiveRedisTemplate for non-blocking Redis operations
     */
    @Bean
    @Primary
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory connectionFactory
    ) {
        StringRedisSerializer serializer = new StringRedisSerializer();

        // Configure serializers for all Redis data structures
        RedisSerializationContext<String, String> serializationContext =
                RedisSerializationContext.<String, String>newSerializationContext()
                        .key(serializer)       // Regular keys
                        .value(serializer)     // String values
                        .hashKey(serializer)   // Hash field names
                        .hashValue(serializer) // Hash field values
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }
}
