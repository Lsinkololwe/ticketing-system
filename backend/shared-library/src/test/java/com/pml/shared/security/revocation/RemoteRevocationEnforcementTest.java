package com.pml.shared.security.revocation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.pml.shared.testing.RedisNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Revocation as a service that does not own the records enforces it: the shared Redis cache first,
 * then identity's internal API — here a WireMock server — for anything the cache cannot vouch for.
 */
@Tag("L5")
@Tag("ET-IDN-003")
@DisplayName("A service that reads revocations from identity refuses revoked tokens on every request")
class RemoteRevocationEnforcementTest {

    private static final String CHECK = "/api/internal/revocations/check";

    private static WireMockServer identity;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;

    private RevocationRequestGuard requests;
    private SensitiveOperationGuard sensitive;
    private Jwt token;

    @BeforeAll
    static void start() {
        identity = new WireMockServer(options().dynamicPort());
        identity.start();
        redisFactory = new LettuceConnectionFactory(RedisNode.host(), RedisNode.port());
        redisFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(redisFactory);
    }

    @AfterAll
    static void stop() {
        identity.stop();
        redisFactory.destroy();
    }

    @BeforeEach
    void wire() {
        identity.resetAll();
        // No completeness sentinel: this cache cannot vouch for a token by the absence of a key,
        // so every check it cannot answer from a key goes to identity.
        redis.delete(RevocationCacheTrust.SENTINEL_KEY).block();
        RevocationProperties properties = new RevocationProperties();
        properties.setDurableTimeout(Duration.ofMillis(800));
        RevocationMetrics metrics = new RevocationMetrics(new SimpleMeterRegistry(), Clock.systemUTC());
        RevocationCheck check = new CachedRevocationCheck(
                redis,
                new RevocationCacheTrust(redis, properties.getCacheCompletenessTtl()),
                new HttpDurableRevocationStore(identityClient()),
                properties, metrics,
                CircuitBreaker.ofDefaults("revocation-cache-" + UUID.randomUUID()),
                TimeLimiter.ofDefaults());
        requests = new RevocationRequestGuard(check, metrics);
        sensitive = new SensitiveOperationGuard(check, properties, metrics);
        token = Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("jti", "jti-" + UUID.randomUUID()).subject("user-" + UUID.randomUUID()).claim("sid", "sid-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    /**
     * With a plain ObjectMapper made up front. A default builder discovers Jackson modules lazily,
     * and the Kotlin one reads class metadata from jars at first use — on the event loop, where
     * BlockHound refuses it. The revocation records need no module.
     */
    private static WebClient identityClient() {
        ObjectMapper mapper = new ObjectMapper();
        return WebClient.builder()
                .baseUrl("http://127.0.0.1:" + identity.port())
                .codecs(codecs -> {
                    codecs.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
                    codecs.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
                })
                .build();
    }

    private void identitySays(String decision) {
        identity.stubFor(post(urlEqualTo(CHECK)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"decision\":\"" + decision + "\"}")));
    }

    private Context signedIn() {
        return ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(token, List.of()));
    }

    /** Runs one request through the guard; true when it reached the rest of the chain. */
    private boolean reachedHandler(MockServerWebExchange exchange) {
        AtomicBoolean reached = new AtomicBoolean();
        requests.webFilter()
                .filter(exchange, ex -> Mono.fromRunnable(() -> reached.set(true)))
                .contextWrite(signedIn())
                .block();
        return reached.get();
    }

    @Test
    @DisplayName("identity says revoked: the request is refused with TOKEN_REVOKED")
    void revokedAtIdentity() {
        identitySays("REVOKED");
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/graphql"));

        assertThat(reachedHandler(exchange)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .contains("invalid_token");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":\"TOKEN_REVOKED\"");
    }

    @Test
    @DisplayName("revoked in the shared cache: refused without asking identity")
    void revokedInTheCache() {
        redis.opsForValue().set(RevocationKeys.user(token.getSubject()), "1", Duration.ofMinutes(5)).block();

        assertThat(reachedHandler(MockServerWebExchange.from(MockServerHttpRequest.post("/graphql")))).isFalse();
        identity.verify(0, postRequestedFor(urlEqualTo(CHECK)));
    }

    @Test
    @DisplayName("identity says active: the request goes through")
    void activeToken() {
        identitySays("ACTIVE");

        assertThat(reachedHandler(MockServerWebExchange.from(MockServerHttpRequest.post("/graphql")))).isTrue();
    }

    @Test
    @DisplayName("identity down: an ordinary request goes through, a sensitive operation is refused")
    void outageFailsOpenForReadsAndClosedForMoney() {
        identity.stubFor(post(urlEqualTo(CHECK)).willReturn(aResponse().withStatus(503)));

        assertThat(reachedHandler(MockServerWebExchange.from(MockServerHttpRequest.post("/graphql")))).isTrue();
        assertThatThrownBy(() -> sensitive.assertNotRevoked("approvePayoutRequest").contextWrite(signedIn()).block())
                .isInstanceOf(RevocationUnavailableException.class);
    }

    @Test
    @DisplayName("an unauthenticated request is not looked up")
    void anonymousIsNotLookedUp() {
        AtomicBoolean reached = new AtomicBoolean();
        requests.webFilter()
                .filter(MockServerWebExchange.from(MockServerHttpRequest.get("/graphql")),
                        ex -> Mono.fromRunnable(() -> reached.set(true)))
                .block();

        assertThat(reached).isTrue();
        identity.verify(0, postRequestedFor(urlEqualTo(CHECK)));
    }
}
