package com.pml.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Configures key resolvers for the RequestRateLimiter filter.
 *
 * <h2>How Rate Limiting Works (Token Bucket Algorithm)</h2>
 * <pre>
 * Each key (IP address or user ID) has a "bucket" in Redis:
 *
 *   ┌─────────────────────────────────────┐
 *   │  Bucket for IP 192.168.1.100        │
 *   │  ┌───┬───┬───┬───┬───┬───┬───┬───┐  │
 *   │  │ ● │ ● │ ● │ ● │ ● │   │   │   │  │  ← 5 tokens available
 *   │  └───┴───┴───┴───┴───┴───┴───┴───┘  │
 *   │  Capacity: 8 (burstCapacity)        │
 *   │  Refill: 4/sec (replenishRate)      │
 *   └─────────────────────────────────────┘
 *
 * On each request:
 *   1. KeyResolver determines the bucket key (e.g., IP address)
 *   2. Try to take 1 token from bucket
 *   3. If token available → request allowed, token removed
 *   4. If bucket empty → 429 Too Many Requests
 *   5. Tokens refill at replenishRate per second
 *
 * Configuration in application.yml:
 *   redis-rate-limiter.replenishRate: 100  ← 100 tokens/second sustained rate
 *   redis-rate-limiter.burstCapacity: 200  ← Allow burst of 200 then 100/sec
 *   key-resolver: "#{@ipKeyResolver}"       ← SpEL reference to this bean
 * </pre>
 *
 * <h2>Key Resolver Types</h2>
 * <ul>
 *   <li><b>IP-based:</b> Rate limit per IP address (default, prevents single IP abuse)</li>
 *   <li><b>User-based:</b> Rate limit per authenticated user (fairer for shared IPs)</li>
 *   <li><b>Path-based:</b> Different limits for different endpoints</li>
 * </ul>
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Default key resolver: Rate limits by client IP address.
     *
     * Use case: Prevent single IP from overwhelming the API.
     * Limitation: Users behind same NAT/proxy share the limit.
     *
     * @Primary makes this the default when multiple KeyResolvers exist.
     */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            // Get client IP from request
            // Note: If behind proxy, may need to read X-Forwarded-For header
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip);
        };
    }

    /**
     * User-based key resolver: Rate limits by authenticated user ID.
     *
     * Use case: Fair rate limiting when many users share an IP (office, mobile carrier).
     * Falls back to IP for unauthenticated requests.
     *
     * Requires: OAuth2TokenRelayFilter to add X-User-Id header (runs at order -50).
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // X-User-Id is added by OAuth2TokenRelayFilter for authenticated requests
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isEmpty()) {
                return Mono.just("user:" + userId);
            }
            // Fallback to IP for unauthenticated requests (public endpoints)
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }

    /**
     * Path-based key resolver: Rate limits by IP + path combination.
     *
     * Use case: Different rate limits per endpoint.
     * Example: /api/auth/login gets stricter limit than /graphql.
     */
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> {
            String path = exchange.getRequest().getPath().value();
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(ip + ":" + path);
        };
    }
}
