package com.pml.shared.security.publicop;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("ET-PLT-007-R9 · the filter marks, limits and counts without leaking what was asked")
class PublicGraphQlFilterTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private ReactiveStringRedisTemplate redis;
    private SimpleMeterRegistry meters;
    private PublicGraphQlFilter filter;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn((Flux) Flux.just(1L));
        meters = new SimpleMeterRegistry();
        filter = new PublicGraphQlFilter(PublicOperationPolicy.of("svc", Set.of("events")), redis, JSON, meters,
                TrustedProxies.parse("10.0.0.0/8"));
    }

    private static MockServerWebExchange post(String json) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/graphql").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.9").body(json));
    }

    private static String body(String query) {
        return "{\"query\":" + JSON.valueToTree(query) + "}";
    }

    private boolean marked(MockServerWebExchange exchange) {
        AtomicReference<Boolean> seen = new AtomicReference<>(false);
        filter.filter(exchange, ex -> {
            seen.set(Boolean.TRUE.equals(ex.getAttribute(PublicGraphQlFilter.PUBLIC_MARK)));
            return Mono.empty();
        }).block();
        return seen.get();
    }

    private double count(String outcome) {
        return meters.find("platform.public_graphql.requests").tag("outcome", outcome).counters().stream()
                .mapToDouble(c -> c.count()).sum();
    }

    @Test
    @DisplayName("only an allowed request is marked; the others reach the chain unmarked to be refused with 401")
    void marks() {
        assertThat(marked(post(body("{ events { id } }")))).isTrue();
        assertThat(marked(post(body("{ me { id } }")))).isFalse();
        assertThat(marked(post(body("mutation { events }")))).isFalse();
        assertThat(count("allowed")).isEqualTo(1);
        assertThat(count("rejected")).isEqualTo(2);
    }

    @Test
    @DisplayName("a body over the limit is not parsed and not marked")
    void oversize() {
        String huge = body("{ events { id } }") .replace("}\"}", "}\"," + "\"pad\":\"" + "x".repeat(20_000) + "\"}");
        assertThat(marked(post(huge))).isFalse();
        assertThat(meters.find("platform.public_graphql.requests").tag("reason", "too_large").counter()).isNotNull();
    }

    @Test
    @DisplayName("a request with a token is passed through untouched and uncounted")
    void tokenUntouched() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/graphql")
                .header(HttpHeaders.AUTHORIZATION, "Bearer x").body(body("{ events { id } }")));
        assertThat(marked(exchange)).isFalse();
        assertThat(meters.getMeters()).isEmpty();
    }

    @Test
    @DisplayName("a GET, or another path, is not this filter's business")
    void otherRequests() {
        assertThat(marked(MockServerWebExchange.from(MockServerHttpRequest.get("/graphql").build()))).isFalse();
        assertThat(marked(MockServerWebExchange.from(MockServerHttpRequest.post("/api/v1/x").body(body("{ events { id } }"))))).isFalse();
        assertThat(meters.getMeters()).isEmpty();
    }

    @Test
    @DisplayName("over the limit: 429 with Retry-After and the chain is never reached")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void limited() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn((Flux) Flux.just(121L));
        MockServerWebExchange exchange = post(body("{ events { id } }"));
        AtomicBoolean reached = new AtomicBoolean();
        filter.filter(exchange, ex -> { reached.set(true); return Mono.empty(); }).block();
        assertThat(reached).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(count("rate_limited")).isEqualTo(1);
    }

    @Test
    @DisplayName("Redis down: the request is served and the outage is counted")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void failsOpen() {
        when(redis.execute(any(RedisScript.class), anyList(), anyList())).thenReturn((Flux) Flux.error(new IllegalStateException("down")));
        assertThat(marked(post(body("{ events { id } }")))).isTrue();
        assertThat(count("limiter_unavailable")).isEqualTo(1);
    }

    @Test
    @DisplayName("metrics carry no address, query text or variable")
    void noPii() {
        marked(post(body("{ events(secret: \"hunter2\") { id } }")));
        marked(post(body("{ me { id } }")));
        List<String> tagValues = meters.getMeters().stream().flatMap(m -> m.getId().getTags().stream())
                .map(t -> t.getValue()).toList();
        assertThat(tagValues).allSatisfy(v -> assertThat(v).doesNotContain("203.0.113.9", "hunter2", "events", "me {"));
    }
}
