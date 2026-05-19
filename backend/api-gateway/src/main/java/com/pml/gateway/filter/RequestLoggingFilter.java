package com.pml.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Global filter that logs all requests and adds correlation IDs for distributed tracing.
 *
 * <h2>Filter Execution Order</h2>
 * <pre>
 * Order -200 (this filter - runs FIRST)
 *    ↓
 * Order -100 (Spring Security - JWT validation)
 *    ↓
 * Order -75 (SessionBlacklistFilter - checks revoked sessions)
 *    ↓
 * Order -50 (OAuth2TokenRelayFilter - extracts user info from JWT)
 *    ↓
 * Order 0 (Route filters - CircuitBreaker, RateLimiter, RewritePath)
 *    ↓
 * Forward to downstream service
 * </pre>
 *
 * <h2>Correlation ID Flow</h2>
 * <pre>
 * 1. Check if request has X-Correlation-Id header (from upstream proxy/client)
 * 2. If missing, generate new UUID
 * 3. Add to request headers → forwarded to all downstream services
 * 4. All services log with this ID → enables request tracing across microservices
 * </pre>
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String REQUEST_START_TIME_ATTR = "requestStartTime";

    /**
     * Filter execution: logs incoming request, adds correlation ID, logs response.
     *
     * @param exchange Contains request/response and attributes (mutable state bag)
     * @param chain    Next filter in the chain - call chain.filter() to continue
     * @return Mono<Void> - reactive completion signal
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Step 1: Get or generate correlation ID
        // If client/proxy already set one, reuse it for end-to-end tracing
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Step 2: Store start time for duration calculation in response logging
        long startTime = Instant.now().toEpochMilli();
        exchange.getAttributes().put(REQUEST_START_TIME_ATTR, startTime);

        // Step 3: Add correlation ID to request (mutate creates new immutable request)
        String finalCorrelationId = correlationId;
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, correlationId)
                .build();

        // Step 4: Log incoming request
        log.info("[REQUEST] {} {} | IP: {} | Correlation-ID: {}",
                request.getMethod(),
                request.getPath(),
                request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : "unknown",
                correlationId);

        // Step 5: Continue filter chain, then log response when complete
        // The .then() runs AFTER downstream returns response
        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .then(Mono.fromRunnable(() -> logResponse(exchange, finalCorrelationId)));
    }

    /**
     * Logs response details after downstream service responds.
     * Called via .then() after the response is received.
     */
    private void logResponse(ServerWebExchange exchange, String correlationId) {
        Long startTime = exchange.getAttribute(REQUEST_START_TIME_ATTR);
        long duration = startTime != null ? Instant.now().toEpochMilli() - startTime : -1;

        ServerHttpResponse response = exchange.getResponse();
        log.info("[RESPONSE] {} | Duration: {}ms | Correlation-ID: {}",
                response.getStatusCode(),
                duration,
                correlationId);
    }

    /**
     * Order determines when this filter runs relative to others.
     * Negative = runs early, Positive = runs late.
     * -200 ensures this runs before security filters to log ALL requests.
     */
    @Override
    public int getOrder() {
        return -200;
    }
}
