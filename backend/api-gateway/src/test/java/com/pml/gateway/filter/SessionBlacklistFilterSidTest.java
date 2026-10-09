package com.pml.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pml.gateway.service.SessionBlacklistService;
import com.pml.shared.security.revocation.RevocationKeys;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

/**
 * ET-IDN-003 R7: a Keycloak logout is recorded by identity-service as {@code pml:session:{sid}};
 * this filter refuses a token carrying that {@code sid}, and keeps keying users on {@code sub}
 * (a buyer's {@code accountId} claim is never used). The Redis keys are the ones identity writes.
 */
@Tag("L1")
@Tag("ET-IDN-003")
@DisplayName("ET-IDN-003-R7 · gateway refuses a token whose sid was revoked by a Keycloak logout")
class SessionBlacklistFilterSidTest {

    private final Set<String> redisKeys = new HashSet<>();
    private final Set<String> asked = new HashSet<>();

    @SuppressWarnings("unchecked")
    private SessionBlacklistFilter filter() {
        ReactiveRedisTemplate<String, String> redis = mock(ReactiveRedisTemplate.class);
        when(redis.hasKey(anyString())).thenAnswer(call -> {
            String key = call.getArgument(0);
            asked.add(key);
            return Mono.just(redisKeys.contains(key));
        });
        return new SessionBlacklistFilter(new SessionBlacklistService(redis));
    }

    private static Jwt token(String sub, String sid, String accountId) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256").subject(sub).claim("sid", sid)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).jti("jti-" + sid);
        if (accountId != null) {
            b.claim("accountId", accountId);
        }
        return b.build();
    }

    /** @return true when the request reached the next filter, false when it was answered 401 */
    private boolean passes(SessionBlacklistFilter filter, Jwt jwt, MockServerWebExchange[] out) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/graphql").build());
        out[0] = exchange;
        AtomicInteger chained = new AtomicInteger();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of());
        filter.filter(exchange, e -> {
            chained.incrementAndGet();
            return Mono.empty();
        }).contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth)))).block();
        assertThat(chained.get()).as("the rest of the chain runs at most once").isLessThanOrEqualTo(1);
        return chained.get() == 1;
    }

    @Test
    @DisplayName("a revoked sid is refused with 401 and X-Token-Revoked, for a buyer token and a staff token alike")
    void revokedSidIsRefused() {
        redisKeys.add(RevocationKeys.session("sid-buyer"));
        redisKeys.add(RevocationKeys.session("sid-staff"));
        MockServerWebExchange[] ex = new MockServerWebExchange[1];

        assertThat(passes(filter(), token("kc-buyer", "sid-buyer", "acc-1"), ex)).isFalse();
        assertThat(ex[0].getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex[0].getResponse().getHeaders().getFirst("X-Token-Revoked")).isEqualTo("true");

        assertThat(passes(filter(), token("kc-staff", "sid-staff", null), ex)).isFalse();
        assertThat(ex[0].getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("another session of the same user, with a different sid, is untouched by a session revocation")
    void otherSessionPasses() {
        redisKeys.add(RevocationKeys.session("sid-1"));
        MockServerWebExchange[] ex = new MockServerWebExchange[1];
        assertThat(passes(filter(), token("kc-user", "sid-2", null), ex)).isTrue();
    }

    @Test
    @DisplayName("users are looked up by sub, never by the accountId claim")
    void userIsKeyedOnSub() {
        redisKeys.add(RevocationKeys.user("kc-sub"));
        MockServerWebExchange[] ex = new MockServerWebExchange[1];

        assertThat(passes(filter(), token("kc-sub", "sid-9", "account-9"), ex)).isFalse();
        assertThat(asked).contains(RevocationKeys.user("kc-sub"), RevocationKeys.session("sid-9"))
                .doesNotContain(RevocationKeys.user("account-9"));

        // revoking the accountId is not a revocation of the user: it is not a key the platform writes
        redisKeys.clear();
        redisKeys.add(RevocationKeys.user("account-9"));
        assertThat(passes(filter(), token("kc-sub", "sid-9", "account-9"), ex)).isTrue();
    }

    @Test
    @DisplayName("the key layout is the contract with identity-service")
    void keyLayout() {
        assertThat(RevocationKeys.session("abc")).isEqualTo("pml:session:abc");
        assertThat(Map.of("user", RevocationKeys.user("u")).get("user")).isEqualTo("pml:revoked:u");
    }
}
