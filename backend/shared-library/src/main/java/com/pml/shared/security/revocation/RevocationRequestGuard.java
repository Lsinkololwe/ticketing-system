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

/**
 * Refuses every request that presents a revoked token, in every service that runs it.
 *
 * <p>Installed in the security chain directly after authentication, so the token's signature,
 * issuer and expiry have already been checked — an unsigned token never costs a lookup. One
 * lookup covers the token's {@code jti}, {@code sid} and {@code sub} together.
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

    private final RevocationCheck revocationCheck;
    private final RevocationMetrics metrics;

    public RevocationRequestGuard(RevocationCheck revocationCheck, RevocationMetrics metrics) {
        this.revocationCheck = revocationCheck;
        this.metrics = metrics;
    }

    /** The filter to add after authentication in a service's security chain. */
    public WebFilter webFilter() {
        return (exchange, chain) -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(token -> revocationCheck.check(token.getToken().getId(),
                        token.getToken().getClaimAsString("sid"), token.getToken().getSubject()))
                .defaultIfEmpty(RevocationDecision.ACTIVE)
                .flatMap(decision -> decision == RevocationDecision.REVOKED
                        ? refuse(exchange)
                        : chain.filter(exchange));
    }

    private Mono<Void> refuse(ServerWebExchange exchange) {
        metrics.denied("request", RevocationDecision.REVOKED);
        log.warn("[Revocation] Refused {} {} — the presented token is revoked",
                exchange.getRequest().getMethod(), exchange.getRequest().getPath());
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                "Bearer error=\"invalid_token\", error_description=\"The access token has been revoked\"");
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer body = response.bufferFactory().wrap(BODY.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }
}
