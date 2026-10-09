package com.pml.shared.security.publicop;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.util.List;

/**
 * Lets a signed-out caller reach exactly what a service's {@link PublicOperationPolicy} names and nothing else.
 *
 * <p>Opening {@code /graphql} to anonymous callers wholesale would also expose federation's {@code _entities}
 * and {@code _service}, introspection and every resolver that relies on the path being authenticated. So the
 * path stays authenticated, and this filter marks an exchange public only when it has no {@code Authorization}
 * header, is a POST to the GraphQL path, carries one parsed query (no batch, mutation or subscription) whose
 * every root field is allowlisted, within the policy's size and depth. Anything else falls through to the normal
 * chain and is refused with 401. A request that carries a token is never touched here.
 *
 * <p>Marked requests are counted per client address in Redis (fixed window). Redis being down lets the request
 * through: the data is public and the alternative is a signed-out visitor who cannot load the page.
 *
 * <p>Metrics carry the service, the outcome and the refusal reason, never an address, query text or variable.
 */
public class PublicGraphQlFilter implements WebFilter, Ordered {

    /** Ahead of Spring Security's filter chain (-100), which reads the mark. */
    public static final int ORDER = -150;
    public static final String PUBLIC_MARK = PublicGraphQlFilter.class.getName() + ".PUBLIC";

    private static final Logger log = LoggerFactory.getLogger(PublicGraphQlFilter.class);
    private static final RedisScript<Long> COUNT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) end
            return count
            """, Long.class);

    private final PublicOperationPolicy policy;
    private final PublicOperationRules rules;
    private final ReactiveStringRedisTemplate redis;
    private final MeterRegistry meters;
    private final TrustedProxies trustedProxies;

    /** A filter that trusts no proxy: the connecting peer is the client, {@code X-Forwarded-For} is ignored. */
    public PublicGraphQlFilter(PublicOperationPolicy policy, ReactiveStringRedisTemplate redis, ObjectMapper json,
                               MeterRegistry meters) {
        this(policy, redis, json, meters, TrustedProxies.NONE);
    }

    /**
     * @param trustedProxies the peers (the router, the gateway) whose {@code X-Forwarded-For} may name the client;
     *                       from any other peer the header is ignored, so a direct caller cannot choose its bucket
     */
    public PublicGraphQlFilter(PublicOperationPolicy policy, ReactiveStringRedisTemplate redis, ObjectMapper json,
                               MeterRegistry meters, TrustedProxies trustedProxies) {
        this.trustedProxies = trustedProxies == null ? TrustedProxies.NONE : trustedProxies;
        this.policy = policy;
        this.rules = new PublicOperationRules(policy, json);
        this.redis = redis;
        this.meters = meters;
    }

    public PublicOperationPolicy policy() {
        return policy;
    }

    /** For the security chain: true when this filter judged the request a public operation. */
    public static ServerWebExchangeMatcher isPublicOperation() {
        return exchange -> Boolean.TRUE.equals(exchange.getAttribute(PUBLIC_MARK))
                ? ServerWebExchangeMatcher.MatchResult.match()
                : ServerWebExchangeMatcher.MatchResult.notMatch();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (request.getMethod() != HttpMethod.POST
                || !"/graphql".equals(request.getPath().value())
                || request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            return chain.filter(exchange);
        }
        return DataBufferUtils.join(request.getBody(), policy.maxBodyBytes())
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .onErrorReturn(OVERSIZE) // too large or unreadable: not public, the chain refuses it
                .flatMap(bytes -> {
                    ServerWebExchange replay = exchange.mutate().request(replayOf(request, bytes)).build();
                    PublicOperationRules.Verdict verdict = bytes == OVERSIZE
                            ? PublicOperationRules.Verdict.TOO_LARGE : rules.judge(bytes);
                    if (verdict != PublicOperationRules.Verdict.ALLOWED) {
                        count("rejected", verdict.name().toLowerCase());
                        return chain.filter(replay);
                    }
                    return consume(clientAddress(request, trustedProxies)).flatMap(allowed -> {
                        if (!allowed) {
                            count("rate_limited", "none");
                            replay.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            replay.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER,
                                    String.valueOf(policy.window().toSeconds()));
                            return replay.getResponse().setComplete();
                        }
                        count("allowed", "none");
                        replay.getAttributes().put(PUBLIC_MARK, Boolean.TRUE);
                        return chain.filter(replay);
                    });
                });
    }

    private static final byte[] OVERSIZE = new byte[0];

    /** One fixed-window counter per client address; true when this request is within the limit. */
    private Mono<Boolean> consume(String client) {
        String key = "rl:" + policy.service() + ":public-graphql:" + client;
        return redis.execute(COUNT, List.of(key), List.of(String.valueOf(policy.window().toSeconds()))).next()
                .map(count -> count <= policy.limitPerWindow())
                .onErrorResume(failure -> {
                    log.warn("public GraphQL rate limiter unavailable; allowing the request ({})",
                            failure.getClass().getSimpleName());
                    count("limiter_unavailable", "none");
                    return Mono.just(true);
                })
                .defaultIfEmpty(true);
    }

    private void count(String outcome, String reason) {
        if (meters != null) {
            meters.counter("platform.public_graphql.requests", "service", policy.service(),
                    "outcome", outcome, "reason", reason).increment();
        }
    }

    /** The client address with no trusted proxy: the connecting peer. */
    public static String clientAddress(ServerHttpRequest request) {
        return clientAddress(request, TrustedProxies.NONE);
    }

    /**
     * The client this request is counted against.
     *
     * <p>When the connecting peer is not a trusted proxy the answer is the peer itself and
     * {@code X-Forwarded-For} is ignored: it is whatever the caller typed. When the peer is trusted (the Apollo
     * Router, which propagates the header the Next BFF set from the browser's address) the chain is walked from
     * the right, skipping our own proxies, and the first address that is not one of them is the client. An entry
     * that is not an IP literal ends the walk at the peer, so a malformed header cannot become a Redis key.
     */
    public static String clientAddress(ServerHttpRequest request, TrustedProxies trusted) {
        InetAddress peerAddress = request.getRemoteAddress() == null ? null : request.getRemoteAddress().getAddress();
        String peer = peerAddress == null ? "unknown" : peerAddress.getHostAddress();
        if (peerAddress == null || trusted == null || !trusted.trusts(peerAddress)) {
            return peer;
        }
        List<String> headers = request.getHeaders().get("X-Forwarded-For");
        if (headers == null || headers.isEmpty()) {
            return peer;
        }
        String[] hops = String.join(",", headers).split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (hop.isEmpty()) {
                continue;
            }
            if (TrustedProxies.literal(hop) == null) {
                return peer;
            }
            if (!trusted.trusts(hop)) {
                return hop;
            }
        }
        return peer;
    }

    private static ServerHttpRequest replayOf(ServerHttpRequest request, byte[] bytes) {
        return new ServerHttpRequestDecorator(request) {
            @Override
            public Flux<DataBuffer> getBody() {
                return bytes.length == 0
                        ? Flux.empty()
                        : Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes));
            }
        };
    }
}
