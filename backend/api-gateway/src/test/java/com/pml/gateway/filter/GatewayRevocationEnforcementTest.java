package com.pml.gateway.filter;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.pml.shared.security.revocation.CachedRevocationCheck;
import com.pml.shared.security.revocation.HttpDurableRevocationStore;
import com.pml.shared.security.revocation.RevocationCacheTrust;
import com.pml.shared.security.revocation.RevocationKeys;
import com.pml.shared.security.revocation.RevocationMetrics;
import com.pml.shared.security.revocation.RevocationProperties;
import com.pml.shared.testing.RedisNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * The gateway filter on top of the real revocation check: a Redis container for the shared cache
 * and WireMock standing in for identity-service's durable records.
 */
@Tag("L5")
@Tag("ET-IDN-003")
@DisplayName("The gateway refuses revoked tokens even when the Redis cache has lost them")
class GatewayRevocationEnforcementTest {

    private static final String CHECK = "/api/internal/revocations/check";

    private static WireMockServer identity;
    private static LettuceConnectionFactory redisFactory;
    private static ReactiveStringRedisTemplate redis;

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
    void freshToken() {
        identity.resetAll();
        redis.delete(RevocationCacheTrust.SENTINEL_KEY).block();
        token = Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("jti", "jti-" + UUID.randomUUID()).subject("user-" + UUID.randomUUID())
                .claim("sid", "sid-" + UUID.randomUUID())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }

    /** A plain ObjectMapper made up front, so no Jackson module is discovered on the event loop. */
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

    private static SessionBlacklistFilter filterOver(ReactiveStringRedisTemplate cache) {
        RevocationProperties properties = new RevocationProperties();
        properties.setDurableTimeout(Duration.ofMillis(800));
        RevocationMetrics metrics = new RevocationMetrics(new SimpleMeterRegistry(), Clock.systemUTC());
        return new SessionBlacklistFilter(new CachedRevocationCheck(
                cache,
                new RevocationCacheTrust(cache, properties.getCacheCompletenessTtl()),
                new HttpDurableRevocationStore(identityClient()),
                properties, metrics,
                CircuitBreaker.ofDefaults("gateway-revocation-" + UUID.randomUUID()),
                TimeLimiter.ofDefaults()));
    }

    private void identitySays(String decision) {
        identity.stubFor(post(urlEqualTo(CHECK)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"decision\":\"" + decision + "\"}")));
    }

    private record Outcome(boolean reachedBackend, MockServerWebExchange exchange) {
    }

    private Outcome request(SessionBlacklistFilter filter, HttpMethod method) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(method, "/api/v1/documents").build());
        AtomicBoolean reached = new AtomicBoolean();
        filter.filter(exchange, ex -> Mono.fromRunnable(() -> reached.set(true)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                        new JwtAuthenticationToken(token, List.of())))
                .block();
        return new Outcome(reached.get(), exchange);
    }

    @Test
    @DisplayName("a revocation the cache holds refuses the token without asking identity")
    void revokedInTheCache() {
        redis.opsForValue().set(RevocationKeys.session(token.getClaimAsString("sid")), "revoked",
                Duration.ofMinutes(5)).block();

        Outcome outcome = request(filterOver(redis), HttpMethod.GET);

        assertThat(outcome.reachedBackend()).isFalse();
        assertThat(outcome.exchange().getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        identity.verify(0, postRequestedFor(urlEqualTo(CHECK)));
    }

    @Test
    @DisplayName("after the cache is flushed, a revoked token is still refused because identity is asked")
    void flushedCacheDoesNotAdmitARevokedToken() {
        // No key and no completeness marker: this is the state right after FLUSHALL or a restart.
        identitySays("REVOKED");

        Outcome outcome = request(filterOver(redis), HttpMethod.GET);

        assertThat(outcome.reachedBackend()).isFalse();
        assertThat(outcome.exchange().getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(outcome.exchange().getResponse().getHeaders().getFirst("X-Token-Revoked")).isEqualTo("true");
        identity.verify(1, postRequestedFor(urlEqualTo(CHECK)));
    }

    @Test
    @DisplayName("a complete cache that does not hold the token vouches for it without asking identity")
    void completeCacheServesActiveTokens() {
        redis.opsForValue().set(RevocationCacheTrust.SENTINEL_KEY, "1", Duration.ofMinutes(10)).block();

        assertThat(request(filterOver(redis), HttpMethod.POST).reachedBackend()).isTrue();
        identity.verify(0, postRequestedFor(urlEqualTo(CHECK)));
    }

    @Test
    @DisplayName("with Redis unreachable, identity's records still decide")
    void redisDownIdentityDecides() {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory("127.0.0.1", 1);
        unreachable.afterPropertiesSet();
        try {
            ReactiveStringRedisTemplate dead = new ReactiveStringRedisTemplate(unreachable);

            identitySays("REVOKED");
            assertThat(request(filterOver(dead), HttpMethod.POST).reachedBackend()).isFalse();

            identitySays("ACTIVE");
            assertThat(request(filterOver(dead), HttpMethod.POST).reachedBackend()).isTrue();
        } finally {
            unreachable.destroy();
        }
    }

    @Test
    @DisplayName("with neither store answering, every request proceeds at the gateway")
    void bothDown() {
        identity.stubFor(post(urlEqualTo(CHECK)).willReturn(aResponse().withStatus(503)));
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory("127.0.0.1", 1);
        unreachable.afterPropertiesSet();
        try {
            SessionBlacklistFilter filter = filterOver(new ReactiveStringRedisTemplate(unreachable));

            assertThat(request(filter, HttpMethod.GET).reachedBackend()).isTrue();
            assertThat(request(filter, HttpMethod.POST).reachedBackend())
                    .as("the gateway defers fail-closed enforcement to the service that owns the operation")
                    .isTrue();
        } finally {
            unreachable.destroy();
        }
    }
}
