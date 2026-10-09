package com.pml.gateway.filter;

import com.pml.gateway.service.SessionBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Comprehensive Token/Session Blacklist Filter for Enterprise-Scale Systems.
 *
 * <h2>Why This Filter Exists</h2>
 * <p>
 * JWTs are stateless - once issued, they're valid until expiry. Problem: if user logs out
 * on one device, their JWT on another device remains valid. This filter solves this by
 * checking THREE types of blacklists:
 * </p>
 * <ol>
 *   <li><b>Token Blacklist (JTI)</b> - Industry standard, O(1) lookup, immediate revocation</li>
 *   <li><b>Session Blacklist (SID)</b> - Keycloak session revocation</li>
 *   <li><b>User Blacklist (SUB)</b> - "Logout everywhere" functionality</li>
 * </ol>
 *
 * <h2>Flow</h2>
 * <pre>
 * 1. User clicks "Logout" in any application
 * 2. Application blacklists the token JTI in Redis
 * 3. Keycloak sends back-channel logout → session SID blacklisted
 * 4. This filter checks ALL blacklists on every request
 * 5. If ANY match → reject with 401 (even if JWT is technically valid)
 * </pre>
 *
 * <h2>Filter Order: -75</h2>
 * <pre>
 * -200: RequestLoggingFilter (log request)
 * -100: Spring Security (validate JWT signature and claims)
 *  -75: THIS FILTER (check blacklists) ← runs AFTER JWT is validated
 *  -50: OAuth2TokenRelayFilter (extract user info)
 *    0: Route filters
 * </pre>
 *
 * <h2>Performance at Scale (20M users)</h2>
 * <pre>
 * - 3 parallel Redis EXISTS calls (~1-2ms total)
 * - O(1) lookup complexity
 * - Minimal memory: ~50 bytes per blacklisted token
 * - 100K+ requests/second supported
 * </pre>
 *
 * <h2>Fail-Open Behavior</h2>
 * <p>
 * If Redis is unavailable, requests are ALLOWED through. This prioritizes availability
 * over immediate revocation. Trade-off: revoked tokens may briefly work until Redis recovers.
 * </p>
 *
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionBlacklistFilter implements GlobalFilter, Ordered {

    private final SessionBlacklistService sessionBlacklistService;

    // Runs after Spring Security (-100) but before token relay (-50)
    private static final int FILTER_ORDER = -75;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Step 1: Get security context (populated by Spring Security filter at -100)
        return ReactiveSecurityContextHolder.getContext()
                // Step 2: Only proceed if authenticated (has valid JWT)
                .filter(context -> context.getAuthentication() != null)
                .filter(context -> context.getAuthentication().isAuthenticated())
                .filter(context -> context.getAuthentication() instanceof JwtAuthenticationToken)
                // Step 3: Extract JWT from authentication
                .map(context -> (JwtAuthenticationToken) context.getAuthentication())
                // Step 4: Check blacklist
                // checkBlacklistAndFilter completes empty (Mono<Void>), so it reports "handled" explicitly:
                // switchIfEmpty on its result would run the chain a second time, and after a 401.
                .flatMap(auth -> checkBlacklistAndFilter(exchange, chain, auth).thenReturn(Boolean.TRUE))
                // Step 5: If no auth context (public endpoint), allow through
                .defaultIfEmpty(Boolean.FALSE)
                .flatMap(handled -> handled ? Mono.<Void>empty() : chain.filter(exchange));
    }

    /**
     * Checks if token/session/user is blacklisted and either rejects or allows the request.
     *
     * <p>Checks three blacklist types in parallel:</p>
     * <ol>
     *   <li>Token blacklist (JTI) - immediate token revocation</li>
     *   <li>Session blacklist (SID) - Keycloak session revocation</li>
     *   <li>User blacklist (SUB) - logout everywhere</li>
     * </ol>
     */
    private Mono<Void> checkBlacklistAndFilter(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            JwtAuthenticationToken authentication
    ) {
        Jwt jwt = authentication.getToken();

        // Extract all identifiers from JWT claims
        // jti = JWT ID (unique per token - INDUSTRY STANDARD for blacklisting)
        // sid = Keycloak session ID (unique per login session)
        // sub = the KEYCLOAK user id (for "logout everywhere" scenarios). Deliberately NOT the
        // accountId claim: identity revokes a user by keycloakUserId, which is what sub carries for
        // buyers (whose account id differs) as well as for staff.
        String jti = jwt.getId();  // JWT ID claim
        String sid = jwt.getClaimAsString("sid");
        String sub = jwt.getSubject();

        // If no identifiers at all, can't check blacklist - allow through
        if (jti == null && sid == null && sub == null) {
            log.debug("[Blacklist] No jti/sid/sub in token, skipping check");
            return chain.filter(exchange);
        }

        // Check all three blacklists in parallel for efficiency. FAIL-OPEN: if Redis fails, the
        // request is allowed through. Only the check is guarded; an error from the rest of the
        // chain must not run the chain again.
        return sessionBlacklistService.isBlacklistedComprehensive(jti, sid, sub)
                .onErrorResume(error -> {
                    log.warn("[Blacklist] Redis check failed, allowing request (fail-open): {}",
                            error.getMessage());
                    return Mono.just(false);
                })
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        // Token/Session was revoked - reject request
                        log.warn("[Blacklist] REJECTED revoked token: jti={}, sid={}, path={}",
                                maskValue(jti), maskValue(sid), exchange.getRequest().getPath());

                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        exchange.getResponse().getHeaders().add("X-Token-Revoked", "true");
                        exchange.getResponse().getHeaders().add("X-Session-Revoked", "true");
                        return exchange.getResponse().setComplete();
                    }
                    // Token valid - continue to next filter
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    private String maskValue(String value) {
        if (value == null || value.length() <= 8) return "****";
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }
}
