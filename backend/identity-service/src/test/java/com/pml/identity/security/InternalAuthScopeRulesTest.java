package com.pml.identity.security;

import com.pml.shared.testing.jwt.StubIssuer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scope rules of {@code /api/internal/auth/**} on the real {@link SecurityConfig} chain:
 * GET needs {@code internal-read}, POST needs {@code internal-write}, any other method is refused
 * for everyone, and no token is 401.
 */
@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001 · /api/internal/auth/** scope rules")
class InternalAuthScopeRulesTest {

    private static final String AUDIENCE = "myticketzm-identity-service";
    private static final String PATH = "/api/internal/auth/accounts/ensure";

    private static StubIssuer realm;
    private static WebTestClient client;

    @BeforeAll
    static void start() {
        realm = StubIssuer.start("myticketzm");
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "issuerUri", realm.issuer());
        ReflectionTestUtils.setField(config, "trustedIssuersCsv", "");
        ReflectionTestUtils.setField(config, "keycloakClientId", AUDIENCE);
        ReflectionTestUtils.setField(config, "expectedAudiencesCsv", AUDIENCE);
        ReflectionTestUtils.setField(config, "allowedOrigins", java.util.List.of());
        RouterFunctions.Builder routes = RouterFunctions.route();
        for (HttpMethod method : new HttpMethod[]{HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE}) {
            routes.route(org.springframework.web.reactive.function.server.RequestPredicates.method(method)
                            .and(org.springframework.web.reactive.function.server.RequestPredicates.path("/api/internal/auth/**")),
                    request -> ServerResponse.ok().bodyValue("reached"));
        }
        client = WebTestClient.bindToRouterFunction(routes.build())
                .webFilter(new WebFilterChainProxy(
                        config.securityWebFilterChain(ServerHttpSecurity.http(), new org.springframework.mock.env.MockEnvironment())))
                .configureClient().build();
    }

    @AfterAll
    static void stop() {
        realm.close();
    }

    private static int status(HttpMethod method, String scope) {
        return client.method(method).uri(PATH)
                .headers(h -> {
                    if (scope != null) {
                        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + realm.scopedToken(AUDIENCE, scope));
                    }
                })
                .exchange().returnResult(Void.class).getStatus().value();
    }

    @Test
    @DisplayName("POST needs internal-write")
    void postNeedsWrite() {
        assertThat(status(HttpMethod.POST, "internal-write")).isEqualTo(200);
        assertThat(status(HttpMethod.POST, "internal-read")).isEqualTo(403);
        assertThat(status(HttpMethod.POST, "profile email")).isEqualTo(403);
    }

    @Test
    @DisplayName("GET needs internal-read")
    void getNeedsRead() {
        assertThat(status(HttpMethod.GET, "internal-read")).isEqualTo(200);
        assertThat(status(HttpMethod.GET, "internal-write")).isEqualTo(403);
    }

    @Test
    @DisplayName("PUT and DELETE are refused whatever the scopes")
    void otherMethodsDenied() {
        assertThat(status(HttpMethod.PUT, "internal-read internal-write")).isEqualTo(403);
        assertThat(status(HttpMethod.DELETE, "internal-read internal-write")).isEqualTo(403);
    }

    @Test
    @DisplayName("no token is 401")
    void anonymousRefused() {
        assertThat(status(HttpMethod.POST, null)).isEqualTo(401);
        assertThat(status(HttpMethod.GET, null)).isEqualTo(401);
    }
}
