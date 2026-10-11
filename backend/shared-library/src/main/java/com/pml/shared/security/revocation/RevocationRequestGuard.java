package com.pml.shared.security.revocation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Refuses every request that presents an expired or revoked token, in every service that runs it.
 *
 * <p>Installed in the security chain directly after authentication, so the token's signature
 * and issuer have already been checked — an unsigned token never costs a lookup. The pipeline
 * then runs in a fixed order: the lifetime is checked first, against the platform clock, so an
 * expired token is refused without touching Redis or MongoDB; only a token still inside its
 * lifetime is looked up. One lookup covers the token's {@code jti}, {@code sid} and {@code sub}
 * together.
 *
 * <p>Ordinary requests fail open: when neither the cache nor the durable store can answer, the
 * request proceeds, because an outage of the revocation store must not take down browsing.
 * Operations marked {@link FailClosedOnRevocation} are refused in that case instead, by
 * {@link SensitiveOperationGuard}.
 *
 * <p>Not a {@code WebFilter} bean on purpose: Spring registers every {@code WebFilter} bean
 * globally, outside the security chain, where no authenticated principal exists yet.
 */
@Slf4j
public class RevocationRequestGuard {

    static final String BODY = """
            {"errors":[{"message":"Your session has been revoked. Please sign in again.",\
            "extensions":{"code":"TOKEN_REVOKED","classification":"UNAUTHENTICATED","retryable":false}}]}""";

    static final String EXPIRED_BODY = """
            {"errors":[{"message":"Your session has expired. Please sign in again.",\
            "extensions":{"code":"ACTOR_NOT_AUTHENTICATED","classification":"UNAUTHENTICATED","retryable":false}}]}""";

    private final RevocationCheck revocationCheck;
    private final RevocationMetrics metrics;
    private final RevocationProperties properties;
    private final Clock clock;

    public RevocationRequestGuard(RevocationCheck revocationCheck, RevocationMetrics metrics,
                                  RevocationProperties properties, Clock clock) {
        this.revocationCheck = revocationCheck;
        this.metrics = metrics;
        this.properties = properties;
        this.clock = clock;
    }

    /** The filter to add after authentication in a service's security chain. */
    public WebFilter webFilter() {
        return (exchange, chain) -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(token -> {
                    if (AccessTokenLifetime.isExpired(token.getToken(), clock.instant(),
                            AccessTokenLifetime.TOLERANCE)) {
                        return Mono.just(Stage.EXPIRED);
                    }
                    return revocationCheck.check(token.getToken().getId(),
                                    token.getToken().getClaimAsString("sid"),
                                    token.getToken().getSubject())
                            .map(decision -> decision == RevocationDecision.REVOKED
                                    ? Stage.REVOKED : Stage.ADMIT);
                })
                .defaultIfEmpty(Stage.ADMIT)
                .flatMap(stage -> switch (stage) {
                    case EXPIRED -> refuse(exchange, "expired", EXPIRED_BODY,
                            "The access token has expired");
                    case REVOKED -> {
                        metrics.denied("request", RevocationDecision.REVOKED);
                        yield refuse(exchange, "revoked", BODY, "The access token has been revoked");
                    }
                    case ADMIT -> chain.filter(exchange);
                });
    }

    private enum Stage { EXPIRED, REVOKED, ADMIT }

    private Mono<Void> refuse(ServerWebExchange exchange, String why, String json, String description) {
        log.warn("[Revocation] Refused {} {} — the presented token is {}",
                exchange.getRequest().getMethod(), exchange.getRequest().getPath(), why);
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer error=\"invalid_token\", error_description=\"" + description + "\"");
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer body = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }
}
