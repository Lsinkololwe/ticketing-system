package com.pml.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.pml.shared.security.revocation.RevocationCheck;
import com.pml.shared.security.revocation.RevocationDecision;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * What the gateway filter does with each answer of the revocation check, and which identifiers
 * of the token it hands over. The check itself (Redis, the completeness marker, identity's
 * durable records) is exercised against real stores in {@code GatewayRevocationEnforcementTest}.
 */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("The gateway filter refuses revoked tokens and fails open when revocation cannot be established")
class SessionBlacklistFilterSidTest {

    /** What the check was asked, as {@code jti|sid|sub}. */
    private final List<String> asked = new ArrayList<>();

    private SessionBlacklistFilter filterAnswering(RevocationDecision decision) {
        RevocationCheck check = (jti, sid, sub) -> {
            asked.add(jti + "|" + sid + "|" + sub);
            return Mono.just(decision);
        };
        return new SessionBlacklistFilter(check);
    }

    private static Jwt token(String sub, String sid, String accountId) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject(sub).claim("sid", sid)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).jti("jti-" + sid);
        if (accountId != null) {
            b.claim("accountId", accountId);
        }
        return b.build();
    }

    private record Outcome(boolean reachedBackend, MockServerWebExchange exchange) {
    }

    private Outcome run(SessionBlacklistFilter filter, HttpMethod method, Jwt jwt) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, "/api/v1/documents").build());
        AtomicInteger chained = new AtomicInteger();
        var context = jwt == null ? Mono.<SecurityContextImpl>empty()
                : Mono.just(new SecurityContextImpl(new JwtAuthenticationToken(jwt, List.of())));
        filter.filter(exchange, e -> {
            chained.incrementAndGet();
            return Mono.empty();
        }).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(context)).block();
        assertThat(chained.get()).as("the rest of the chain runs at most once").isLessThanOrEqualTo(1);
        return new Outcome(chained.get() == 1, exchange);
    }

    @Test
    @DisplayName("a revoked token is answered 401 with X-Token-Revoked and never reaches the backend, for buyer and staff alike")
    void revokedIsRefused() {
        for (Jwt jwt : List.of(token("kc-buyer", "sid-buyer", "acc-1"), token("kc-staff", "sid-staff", null))) {
            Outcome outcome = run(filterAnswering(RevocationDecision.REVOKED), HttpMethod.GET, jwt);

            assertThat(outcome.reachedBackend()).isFalse();
            assertThat(outcome.exchange().getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(outcome.exchange().getResponse().getHeaders().getFirst("X-Token-Revoked")).isEqualTo("true");
        }
    }

    @Test
    @DisplayName("an active token goes through")
    void activeIsServed() {
        assertThat(run(filterAnswering(RevocationDecision.ACTIVE), HttpMethod.POST, token("kc", "s", null))
                .reachedBackend()).isTrue();
    }

    @Test
    @DisplayName("the check gets jti, sid and the Keycloak sub, never the accountId claim")
    void identifiersComeFromTheToken() {
        run(filterAnswering(RevocationDecision.ACTIVE), HttpMethod.GET, token("kc-sub", "sid-9", "account-9"));

        assertThat(asked).containsExactly("jti-sid-9|sid-9|kc-sub");
    }

    @Test
    @DisplayName("when no store can answer, every request proceeds — the gateway isn't the one deciding what's sensitive")
    void unknownFailsOpen() {
        Jwt jwt = token("kc", "sid-1", null);

        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            Outcome outcome = run(filterAnswering(RevocationDecision.UNKNOWN), method, jwt);
            assertThat(outcome.reachedBackend()).as(method.name()).isTrue();
        }
    }

    @Test
    @DisplayName("a check that throws is treated as unknown, not as a refusal")
    void failingCheckIsUnknown() {
        SessionBlacklistFilter filter = new SessionBlacklistFilter((jti, sid, sub) -> Mono.error(new IllegalStateException("boom")));

        assertThat(run(filter, HttpMethod.POST, token("kc", "s", null)).reachedBackend()).isTrue();
        assertThat(run(filter, HttpMethod.GET, token("kc", "s", null)).reachedBackend()).isTrue();
    }

    @Test
    @DisplayName("an anonymous request is not looked up at all")
    void anonymousIsNotChecked() {
        Outcome outcome = run(filterAnswering(RevocationDecision.REVOKED), HttpMethod.GET, null);

        assertThat(outcome.reachedBackend()).isTrue();
        assertThat(asked).isEmpty();
    }
}
