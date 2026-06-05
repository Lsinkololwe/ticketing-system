package com.pml.gateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Service for checking and managing session/token blacklist in Redis.
 *
 * <h2>Enterprise-Scale Token Revocation Architecture</h2>
 * <p>
 * This service supports THREE types of blacklisting for immediate token revocation:
 * </p>
 * <ol>
 *   <li><b>Token Blacklist (JTI)</b> - O(1) lookup, industry standard for immediate revocation</li>
 *   <li><b>Session Blacklist (SID)</b> - Keycloak session ID blacklisting</li>
 *   <li><b>User Blacklist (SUB)</b> - "Logout everywhere" for a user</li>
 * </ol>
 *
 * <h2>Redis Key Structure (Unified)</h2>
 * <pre>
 * Token Blacklist:   pml:blacklist:{jti}   → JSON metadata (TTL: 1 hour)
 * Session Blacklist: pml:session:{sid}     → "revoked" (TTL: token lifetime)
 * User Blacklist:    pml:revoked:{sub}     → timestamp (TTL: token lifetime)
 * </pre>
 *
 * <h2>Fail-Open Design</h2>
 * <p>
 * If Redis is down, requests are ALLOWED through. Rationale:
 * </p>
 * <ul>
 *   <li>Availability > immediate revocation for most use cases</li>
 *   <li>JWT will eventually expire anyway</li>
 *   <li>Critical apps can implement fail-closed instead</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionBlacklistService {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // Unified Redis key prefixes - shared across all services and frontends
    private static final String TOKEN_BLACKLIST_PREFIX = "pml:blacklist:";
    private static final String SESSION_BLACKLIST_PREFIX = "pml:session:";
    private static final String USER_REVOCATION_PREFIX = "pml:revoked:";

    /**
     * Checks if a token is blacklisted by its JTI (JWT ID).
     *
     * <p>This is the PRIMARY and RECOMMENDED blacklist check.</p>
     * <ul>
     *   <li>O(1) Redis EXISTS operation</li>
     *   <li>Minimal memory footprint</li>
     *   <li>Industry standard approach</li>
     * </ul>
     *
     * @param jti JWT ID from token's 'jti' claim
     * @return Mono<Boolean> true if blacklisted, false if not or Redis unavailable
     */
    public Mono<Boolean> isTokenBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return Mono.just(false);
        }

        String key = TOKEN_BLACKLIST_PREFIX + jti;
        return redisTemplate.hasKey(key)
                .doOnNext(blacklisted -> {
                    if (blacklisted) {
                        log.info("[Blacklist] Token {} is revoked (JTI blacklist)", maskValue(jti));
                    }
                })
                .onErrorResume(error -> {
                    log.warn("[Blacklist] Redis unavailable for JTI check, allowing request: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Checks if a specific session is blacklisted.
     *
     * @param sid Session ID from JWT's 'sid' claim (Keycloak session ID)
     * @return Mono<Boolean> true if blacklisted, false if not or Redis unavailable
     */
    public Mono<Boolean> isSessionBlacklisted(String sid) {
        if (sid == null || sid.isBlank()) {
            return Mono.just(false);
        }

        String key = SESSION_BLACKLIST_PREFIX + sid;
        return redisTemplate.hasKey(key)
                .doOnNext(blacklisted -> {
                    if (blacklisted) {
                        log.info("[Blacklist] Session {} is revoked", maskValue(sid));
                    }
                })
                .onErrorResume(error -> {
                    log.warn("[Blacklist] Redis unavailable, allowing request: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Checks if all sessions for a user are blacklisted ("logout everywhere").
     *
     * @param sub Subject (user ID) from JWT's 'sub' claim
     * @return Mono<Boolean> true if user is blacklisted
     */
    public Mono<Boolean> isUserBlacklisted(String sub) {
        if (sub == null || sub.isBlank()) {
            return Mono.just(false);
        }

        String key = USER_REVOCATION_PREFIX + sub;
        return redisTemplate.hasKey(key)
                .doOnNext(blacklisted -> {
                    if (blacklisted) {
                        log.info("[Blacklist] User {} is revoked (all sessions)", maskValue(sub));
                    }
                })
                .onErrorResume(error -> {
                    log.warn("[Blacklist] Redis unavailable, allowing request: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Checks if either session OR user is blacklisted.
     *
     * @param sid Session ID (may be null)
     * @param sub Subject/User ID (may be null)
     * @return Mono<Boolean> true if either is blacklisted
     */
    public Mono<Boolean> isBlacklisted(String sid, String sub) {
        return isSessionBlacklisted(sid)
                .zipWith(isUserBlacklisted(sub), (sessionBlacklisted, userBlacklisted) ->
                        sessionBlacklisted || userBlacklisted
                )
                .onErrorResume(error -> {
                    log.warn("[Blacklist] Check failed, allowing request: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Comprehensive blacklist check: token (JTI) + session (SID) + user (SUB).
     *
     * <p>
     * This is the RECOMMENDED method for production use. It checks all three
     * blacklist types in parallel for maximum security with minimal latency.
     * </p>
     *
     * @param jti JWT ID from token's 'jti' claim (may be null)
     * @param sid Session ID from token's 'sid' claim (may be null)
     * @param sub Subject/User ID from token's 'sub' claim (may be null)
     * @return Mono<Boolean> true if ANY blacklist matches
     */
    public Mono<Boolean> isBlacklistedComprehensive(String jti, String sid, String sub) {
        return Mono.zip(
                isTokenBlacklisted(jti),
                isSessionBlacklisted(sid),
                isUserBlacklisted(sub)
        ).map(tuple -> {
            boolean tokenBlacklisted = tuple.getT1();
            boolean sessionBlacklisted = tuple.getT2();
            boolean userBlacklisted = tuple.getT3();

            if (tokenBlacklisted || sessionBlacklisted || userBlacklisted) {
                log.info("[Blacklist] Request blocked - token:{}, session:{}, user:{}",
                        tokenBlacklisted, sessionBlacklisted, userBlacklisted);
            }

            return tokenBlacklisted || sessionBlacklisted || userBlacklisted;
        }).onErrorResume(error -> {
            log.warn("[Blacklist] Comprehensive check failed, allowing request: {}", error.getMessage());
            return Mono.just(false);
        });
    }

    /**
     * Adds a session to the blacklist.
     * Called by back-channel logout handler when Keycloak sends logout notification.
     *
     * @param sid Session ID to blacklist
     * @param ttl Time to live (should match access token lifetime)
     * @return Mono<Boolean> true if successfully blacklisted
     */
    public Mono<Boolean> blacklistSession(String sid, Duration ttl) {
        if (sid == null || sid.isBlank()) {
            return Mono.just(false);
        }

        String key = SESSION_BLACKLIST_PREFIX + sid;
        return redisTemplate.opsForValue()
                .set(key, "revoked", ttl)
                .doOnSuccess(success -> log.info("[Blacklist] Session blacklisted: {}, TTL: {}",
                        maskValue(sid), ttl))
                .onErrorResume(error -> {
                    log.error("[Blacklist] Failed to blacklist session: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Masks sensitive values for logging (shows first/last 4 chars only).
     */
    private String maskValue(String value) {
        if (value == null || value.length() <= 8) return "****";
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
