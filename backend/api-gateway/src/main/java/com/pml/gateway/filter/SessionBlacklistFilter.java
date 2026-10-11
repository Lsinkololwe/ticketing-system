package com.pml.gateway.filter;

import com.pml.shared.security.revocation.RevocationCheck;
import com.pml.shared.security.revocation.RevocationDecision;
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
 * Refuses a request whose bearer token was revoked, whatever its signature and expiry say.
 *
 * <p>A Keycloak access token is a signed JWT that stays valid until {@code exp}; logging out
 * ends the SSO session but does not recall tokens already issued. identity-service keeps the
 * list of revoked {@code jti}, {@code sid} and {@code sub} values, and this filter asks the
 * shared {@link RevocationCheck} about all three on every authenticated request.</p>
 *
 * <h2>Where the answer comes from</h2>
 * <p>The check reads Redis first and trusts a miss only while identity's completeness marker is
 * present. After a Redis flush, restart or eviction the marker is gone, so the check asks
 * identity-service's durable records instead of treating the missing key as "not revoked".
 * This is the same composition catalog, booking and identity use, so the edge and the services
 * cannot disagree about whether a session is alive.</p>
 *
 * <h2>When no store can answer</h2>
 * <p>The result is {@link RevocationDecision#UNKNOWN}, and the request proceeds — the same
 * fail-open rule {@code RevocationRequestGuard} applies inside each service. The gateway routes
 * every GraphQL operation as a POST to the same path, so it cannot tell a read from a write by
 * HTTP method, and it has no knowledge of which mutations are sensitive enough to warrant
 * refusing on an unresolved revocation. That decision belongs to the service that owns the
 * operation: a method marked {@code @FailClosedOnRevocation} refuses with {@code
 * REVOCATION_UNAVAILABLE} on the same {@code UNKNOWN} decision, where the request's actual shape
 * is known. The gateway is a router, not a security boundary.</p>
 *
 * <h2>Filter order: -75</h2>
 * <pre>
 * -100: Spring Security (signature, issuer, audience, expiry)
 *  -75: THIS FILTER, so an unsigned token never costs a lookup
 *  -50: OAuth2TokenRelayFilter
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionBlacklistFilter implements GlobalFilter, Ordered {

    private static final int FILTER_ORDER = -75;

    private final RevocationCheck revocationCheck;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .filter(context -> context.getAuthentication() instanceof JwtAuthenticationToken)
                .map(context -> (JwtAuthenticationToken) context.getAuthentication())
                .filter(JwtAuthenticationToken::isAuthenticated)
                // The decision, not the chain, is what this stage produces: running the chain
                // from inside the lookup would run it twice when the lookup completes empty.
                .flatMap(auth -> decide(auth.getToken()))
                .defaultIfEmpty(RevocationDecision.ACTIVE)
                .flatMap(decision -> decision == RevocationDecision.REVOKED
                        ? refuseRevoked(exchange)
                        : chain.filter(exchange));
    }

    /**
     * Looks up every identifier the token carries. {@code sub} is the Keycloak user id and is
     * deliberately not the {@code accountId} claim: identity revokes a user by Keycloak id, which
     * is what {@code sub} holds for buyers (whose account id differs) and for staff alike.
     */
    private Mono<RevocationDecision> decide(Jwt jwt) {
        return revocationCheck.check(jwt.getId(), jwt.getClaimAsString("sid"), jwt.getSubject())
                .onErrorResume(error -> {
                    log.error("[Revocation] The check itself failed: {}", error.toString());
                    return Mono.just(RevocationDecision.UNKNOWN);
                });
    }

    private Mono<Void> refuseRevoked(ServerWebExchange exchange) {
        log.warn("[Revocation] Refused {} {} — the presented token is revoked",
                exchange.getRequest().getMethod(), exchange.getRequest().getPath());
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("X-Token-Revoked", "true");
        exchange.getResponse().getHeaders().add("X-Session-Revoked", "true");
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}
