package com.pml.shared.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

/**
 * Service for checking token blacklist in Redis.
 *
 * <h2>Purpose</h2>
 * <p>
 * JWTs are stateless - once issued, they're valid until expiry. This service
 * provides defense-in-depth by checking if a token's JTI (JWT ID) has been
 * blacklisted in Redis.
 * </p>
 *
 * <h2>Redis Key Structure</h2>
 * <pre>
 * Key:   pml:blacklist:{jti}
 * Value: JSON with metadata (userId, reason, revokedAt)
 * TTL:   1 hour (automatic cleanup)
 * </pre>
 *
 * <h2>Performance</h2>
 * <ul>
 *   <li>O(1) Redis EXISTS operation</li>
 *   <li>~1ms latency per check</li>
 *   <li>Minimal memory (~100 bytes per entry)</li>
 *   <li>Automatic TTL cleanup (no manual deletion)</li>
 * </ul>
 *
 * <h2>Fail-Open Design</h2>
 * <p>
 * If Redis is unavailable, requests are ALLOWED through. This prioritizes
 * availability over immediate revocation. JWTs will eventually expire anyway.
 * </p>
 *
 * @see TokenBlacklistConstants
 * @see TokenBlacklistFilter
 */
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * Checks if a token is blacklisted by its JTI (JWT ID).
     *
     * <p>This is the primary blacklist check for immediate token revocation.</p>
     *
     * @param jti JWT ID from token's 'jti' claim
     * @return Mono<Boolean> true if blacklisted, false if not or Redis unavailable
     */
    public Mono<Boolean> isTokenBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return Mono.just(false);
        }

        String key = TokenBlacklistConstants.blacklistKey(jti);

        return redisTemplate.hasKey(key)
                .doOnNext(blacklisted -> {
                    if (blacklisted) {
                        log.info("[TokenBlacklist] Token {} is revoked", maskJti(jti));
                    }
                })
                .onErrorResume(error -> {
                    // FAIL-OPEN: Allow request if Redis is unavailable
                    log.warn("[TokenBlacklist] Redis unavailable, allowing request: {}",
                            error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Masks JTI for secure logging (shows first/last 4 chars only).
     */
    private String maskJti(String jti) {
        if (jti == null || jti.length() <= 8) {
            return "****";
        }
        return jti.substring(0, 4) + "..." + jti.substring(jti.length() - 4);
    }
}
