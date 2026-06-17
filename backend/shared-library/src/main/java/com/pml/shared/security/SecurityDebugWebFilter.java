package com.pml.shared.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Debug WebFilter that logs detailed security information for each request.
 *
 * <p>This filter logs:
 * <ul>
 *   <li>Request method and path</li>
 *   <li>Authorization header presence</li>
 *   <li>Security context and authentication details</li>
 *   <li>Extracted authorities/roles</li>
 *   <li>JWT claims (if JWT authentication)</li>
 * </ul>
 *
 * <p>Enable by setting {@code security.debug.enabled=true} in application.yml
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 * security:
 *   debug:
 *     enabled: true
 * }</pre>
 *
 * @see ReactiveSecurityContextHolder
 */
@Component
@ConditionalOnProperty(name = "security.debug.enabled", havingValue = "true")
public class SecurityDebugWebFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SecurityDebugWebFilter.class);
    private static final String SEPARATOR = "═".repeat(80);
    private static final String THIN_SEPARATOR = "─".repeat(80);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getId();
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

        log.trace("\n{}", SEPARATOR);
        log.trace("🔐 SECURITY DEBUG - Request Start");
        log.trace("{}", THIN_SEPARATOR);
        log.trace("Request ID: {}", requestId);
        log.trace("Method: {} {}", method, path);
        log.trace("Authorization Header: {}", authHeader != null ? maskToken(authHeader) : "NOT PRESENT");
        log.trace("{}", THIN_SEPARATOR);

        // Use Mono.deferContextual to read the security context without modifying it
        // This avoids the infinite recursion caused by contextWrite + getContext()
        return ReactiveSecurityContextHolder.getContext()
                .doOnNext(ctx -> logSecurityContext(ctx, requestId))
                .switchIfEmpty(Mono.defer(() -> {
                    log.trace("[{}] ⚠️  No SecurityContext found (anonymous request)", requestId);
                    return Mono.empty();
                }))
                .then(chain.filter(exchange))
                .doOnSuccess(v -> log.trace("[{}] ✅ Request completed successfully", requestId))
                .doOnError(e -> log.trace("[{}] ❌ Request failed: {}", requestId, e.getMessage()))
                .doFinally(signal -> {
                    log.trace("[{}] 🏁 Request finished with signal: {}", requestId, signal);
                    log.trace("{}\n", SEPARATOR);
                });
    }

    private void logSecurityContext(SecurityContext context, String requestId) {
        log.trace("[{}] 🔓 SecurityContext found", requestId);

        Authentication auth = context.getAuthentication();
        if (auth == null) {
            log.trace("[{}] ⚠️  Authentication is null in SecurityContext", requestId);
            return;
        }

        log.trace("[{}] {}", requestId, THIN_SEPARATOR);
        log.trace("[{}] Authentication Details:", requestId);
        log.trace("[{}]   Type: {}", requestId, auth.getClass().getSimpleName());
        log.trace("[{}]   Principal: {}", requestId, auth.getName());
        log.trace("[{}]   Authenticated: {}", requestId, auth.isAuthenticated());
        log.trace("[{}]   Credentials: {}", requestId, auth.getCredentials() != null ? "[PRESENT]" : "[NOT PRESENT]");

        // Log authorities
        String authorities = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(", "));
        log.trace("[{}]   Authorities: {}", requestId, authorities.isEmpty() ? "[NONE]" : authorities);

        // If JWT authentication, log additional details
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            logJwtDetails(jwtAuth.getToken(), requestId);
        }

        log.trace("[{}] {}", requestId, THIN_SEPARATOR);
    }

    private void logJwtDetails(Jwt jwt, String requestId) {
        log.trace("[{}] JWT Token Details:", requestId);
        log.trace("[{}]   Issuer: {}", requestId, jwt.getIssuer());
        log.trace("[{}]   Subject: {}", requestId, jwt.getSubject());
        log.trace("[{}]   Issued At: {}", requestId, jwt.getIssuedAt());
        log.trace("[{}]   Expires At: {}", requestId, jwt.getExpiresAt());
        log.trace("[{}]   Token ID (jti): {}", requestId, jwt.getId());

        // Log key claims
        log.trace("[{}]   Claims:", requestId);

        // Preferred username
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null) {
            log.trace("[{}]     preferred_username: {}", requestId, username);
        }

        // Email
        String email = jwt.getClaimAsString("email");
        if (email != null) {
            log.trace("[{}]     email: {}", requestId, email);
        }

        // Realm roles
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            log.trace("[{}]     realm_access: {}", requestId, realmAccess);
        }

        // Resource access (client roles)
        Object resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            log.trace("[{}]     resource_access: {}", requestId, resourceAccess);
        }

        // Scopes
        String scope = jwt.getClaimAsString("scope");
        if (scope != null) {
            log.trace("[{}]     scope: {}", requestId, scope);
        }

        // Audience
        Object aud = jwt.getClaim("aud");
        if (aud != null) {
            log.trace("[{}]     aud: {}", requestId, aud);
        }

        // Authorized party
        String azp = jwt.getClaimAsString("azp");
        if (azp != null) {
            log.trace("[{}]     azp: {}", requestId, azp);
        }
    }

    private String maskToken(String authHeader) {
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            return authHeader;
        }
        String token = authHeader.substring(7);
        if (token.length() <= 20) {
            return "Bearer [MASKED]";
        }
        // Show first 10 and last 10 characters
        return "Bearer " + token.substring(0, 10) + "..." + token.substring(token.length() - 10);
    }

    @Override
    public int getOrder() {
        // Run very early to see the full filter chain
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
