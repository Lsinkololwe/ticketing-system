package com.pml.shared.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * WebFilter that checks token blacklist for defense-in-depth.
 *
 * <h2>Why Defense-in-Depth?</h2>
 * <p>
 * The API Gateway performs the primary blacklist check. However, if a request
 * bypasses the gateway (internal calls, misconfigured routing), microservices
 * should also validate that tokens aren't revoked.
 * </p>
 *
 * <h2>Filter Order: -50</h2>
 * <pre>
 * -100: Spring Security (validate JWT signature and claims)
 *  -50: THIS FILTER (check token blacklist)
 *    0: Application filters
 * </pre>
 *
 * <h2>Performance</h2>
 * <ul>
 *   <li>Single O(1) Redis EXISTS operation</li>
 *   <li>~1ms additional latency per request</li>
 *   <li>Only executes for authenticated requests</li>
 *   <li>Fail-open on Redis unavailability</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <p>
 * Enable via application.yml:
 * </p>
 * <pre>{@code
 * pml:
 *   security:
 *     token-blacklist:
 *       enabled: true
 * }</pre>
 *
 * @see TokenBlacklistService
 * @see TokenBlacklistAutoConfiguration
 */
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistFilter implements WebFilter, Ordered {

    private final TokenBlacklistService tokenBlacklistService;

    /**
     * Filter order: runs after Spring Security (-100) validates the JWT signature.
     */
    private static final int FILTER_ORDER = -50;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                // Only proceed if authenticated with JWT
                .filter(context -> context.getAuthentication() != null)
                .filter(context -> context.getAuthentication().isAuthenticated())
                .filter(context -> context.getAuthentication() instanceof JwtAuthenticationToken)
                // Extract JWT and check blacklist
                .map(context -> (JwtAuthenticationToken) context.getAuthentication())
                .flatMap(auth -> checkBlacklistAndFilter(exchange, chain, auth))
                // If no auth context (public endpoint), allow through
                .switchIfEmpty(chain.filter(exchange));
    }

    /**
     * Checks if the token's JTI is blacklisted and either rejects or allows the request.
     */
    private Mono<Void> checkBlacklistAndFilter(
            ServerWebExchange exchange,
            WebFilterChain chain,
            JwtAuthenticationToken authentication
    ) {
        Jwt jwt = authentication.getToken();
        String jti = jwt.getId();

        // If no JTI, can't check blacklist - allow through
        if (jti == null || jti.isBlank()) {
            log.debug("[TokenBlacklist] No JTI in token, skipping blacklist check");
            return chain.filter(exchange);
        }

        return tokenBlacklistService.isTokenBlacklisted(jti)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        // Token was revoked - reject request
                        log.warn("[TokenBlacklist] REJECTED revoked token: jti={}, path={}",
                                maskJti(jti), exchange.getRequest().getPath());

                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        exchange.getResponse().getHeaders().add("X-Token-Revoked", "true");
                        return exchange.getResponse().setComplete();
                    }
                    // Token not blacklisted - continue
                    return chain.filter(exchange);
                })
                .onErrorResume(error -> {
                    // FAIL-OPEN: If check fails, allow request through
                    log.warn("[TokenBlacklist] Check failed, allowing request: {}",
                            error.getMessage());
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    private String maskJti(String jti) {
        if (jti == null || jti.length() <= 8) {
            return "****";
        }
        return jti.substring(0, 4) + "..." + jti.substring(jti.length() - 4);
    }
}
