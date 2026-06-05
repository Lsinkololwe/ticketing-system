package com.pml.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extracts user information from validated JWT and adds as headers for downstream services.
 *
 * <h2>Purpose</h2>
 * <p>
 * Downstream services (Identity, Catalog, Booking) need to know WHO is making the request.
 * Instead of each service parsing the JWT, the gateway extracts key claims and adds them
 * as headers. This is called "token relay" or "user context propagation".
 * </p>
 *
 * <h2>Headers Added</h2>
 * <pre>
 * X-User-Id       → userId claim (or sub if not present)
 * X-User-Email    → email claim
 * X-User-Username → username claim
 * Authorization   → Bearer {token} (original JWT for services that need full token)
 *
 * Note: Roles are extracted from JWT realm_access and resource_access claims by each service.
 * </pre>
 *
 * <h2>Request Flow</h2>
 * <pre>
 * Client Request (with Authorization: Bearer xyz)
 *        ↓
 * Gateway validates JWT (Spring Security at order -100)
 *        ↓
 * This filter extracts claims → adds X-User-* headers
 *        ↓
 * Downstream service reads headers instead of parsing JWT again
 * </pre>
 *
 * <h2>Filter Order: -50</h2>
 * <pre>
 * -200: RequestLoggingFilter
 * -100: Spring Security (JWT validation)
 *  -75: SessionBlacklistFilter
 *  -50: THIS FILTER ← JWT is validated, safe to extract claims
 *    0: Route filters (CircuitBreaker, RewritePath, etc.)
 * </pre>
 *
 * <h2>Why Not Just Forward the JWT?</h2>
 * <ul>
 *   <li>Performance: Downstream doesn't need to validate JWT again</li>
 *   <li>Simplicity: Services just read headers, no JWT library needed</li>
 *   <li>We still forward the JWT for services that need full token (e.g., for nested calls)</li>
 * </ul>
 */
@Slf4j
@Component
public class OAuth2TokenRelayFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                // Only process if there's a valid authentication
                .filter(context -> context.getAuthentication() != null)
                .filter(context -> context.getAuthentication().isAuthenticated())
                .filter(context -> context.getAuthentication() instanceof JwtAuthenticationToken)
                .map(context -> (JwtAuthenticationToken) context.getAuthentication())
                .flatMap(authentication -> {
                    Jwt jwt = authentication.getToken();

                    // Extract user information from JWT claims
                    // These claims are set by Keycloak based on user attributes and mappers
                    String userId = jwt.getClaimAsString("userId");
                    String email = jwt.getClaimAsString("email");
                    String username = jwt.getClaimAsString("username");

                    // Fallback: use 'sub' claim if custom userId not present
                    // 'sub' is the Keycloak user ID (UUID)
                    if (userId == null) {
                        userId = jwt.getSubject();
                    }

                    // Build modified request with user context headers
                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

                    // Add user identity headers
                    if (userId != null) {
                        requestBuilder.header("X-User-Id", userId);
                    }
                    if (email != null) {
                        requestBuilder.header("X-User-Email", email);
                    }
                    if (username != null) {
                        requestBuilder.header("X-User-Username", username);
                    }

                    // Forward original JWT for services that need full token
                    // (e.g., for making authenticated calls to other services)
                    requestBuilder.header("Authorization", "Bearer " + jwt.getTokenValue());

                    ServerHttpRequest modifiedRequest = requestBuilder.build();

                    log.debug("[TokenRelay] Forwarding user context: userId={}, email={}, username={}",
                            userId, email, username);

                    return chain.filter(exchange.mutate().request(modifiedRequest).build());
                })
                // No authentication = public endpoint, continue without user headers
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        // Run after security filters (-100) and blacklist check (-75)
        // but before route filters (0)
        return -50;
    }
}
