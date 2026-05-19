package com.pml.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Fallback endpoints for Circuit Breaker pattern.
 *
 * <h2>What is a Circuit Breaker?</h2>
 * <pre>
 * Problem: When a downstream service is slow/down, requests pile up, threads exhaust,
 *          and failures cascade to other services → entire system goes down.
 *
 * Solution: Circuit Breaker pattern (like an electrical circuit breaker).
 *
 *     ┌─────────┐  failures exceed    ┌────────┐
 *     │ CLOSED  │ ─── threshold ────▶ │  OPEN  │
 *     │(normal) │                     │(reject)│
 *     └────┬────┘                     └────┬───┘
 *          │                               │
 *          │ requests succeed         wait duration
 *          │                           expires
 *          │                               │
 *     ┌────┴───────────────────────────────┴────┐
 *     │              HALF-OPEN                   │
 *     │  Allow limited requests to test if      │
 *     │  service recovered.                     │
 *     │  Success → CLOSED | Failure → OPEN      │
 *     └─────────────────────────────────────────┘
 *
 * CLOSED (normal operation):
 *   - All requests pass through to downstream
 *   - Failures are counted in sliding window
 *
 * OPEN (fast failure):
 *   - NO requests sent to downstream (give it time to recover)
 *   - ALL requests immediately routed to fallbackUri (this controller)
 *   - Returns quickly → prevents thread exhaustion
 *
 * HALF-OPEN (testing):
 *   - After waitDurationInOpenState, allows few test requests
 *   - If succeed → circuit CLOSES (service recovered)
 *   - If fail → circuit stays OPEN
 * </pre>
 *
 * <h2>Configuration Reference</h2>
 * <pre>
 * In application.yml, routes reference these endpoints:
 *
 *   - name: CircuitBreaker
 *     args:
 *       name: apolloRouterCircuitBreaker      # Instance name in resilience4j config
 *       fallbackUri: forward:/fallback/graphql # → routes to graphqlFallback() below
 *
 * Circuit breaker thresholds are in resilience4j.circuitbreaker section.
 * </pre>
 *
 * <h2>Why Fallbacks Matter</h2>
 * <ul>
 *   <li>Fast response: Client gets error in ms instead of timing out for 30s</li>
 *   <li>Clear message: Client knows service is down, not hanging</li>
 *   <li>Graceful degradation: Client can show cached data or retry UI</li>
 *   <li>Protection: Prevents cascade failures to other services</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    // ═══════════════════════════════════════════════════════════════════════════
    // GRAPHQL FALLBACK
    // Called when: apolloRouterCircuitBreaker is OPEN
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fallback for Apollo Router (GraphQL Federation Gateway).
     * Returns GraphQL-compliant error format that Apollo Client can parse.
     */
    @PostMapping(value = "/graphql", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> graphqlFallback() {
        log.warn("[CircuitBreaker] Apollo Router circuit OPEN - returning fallback");

        // GraphQL error format: { data: null, errors: [{ message, extensions }] }
        Map<String, Object> error = Map.of(
                "message", "GraphQL service is temporarily unavailable. Please retry shortly.",
                "extensions", Map.of(
                        "code", "SERVICE_UNAVAILABLE",
                        "retryAfter", 30
                )
        );

        Map<String, Object> response = Map.of(
                "data", Map.of(),  // null would be ideal but Map.of() works
                "errors", List.of(error)
        );

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(response));
    }

    /**
     * Fallback for GraphQL GET requests (GraphiQL introspection).
     */
    @GetMapping(value = "/graphql", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> graphqlFallbackGet() {
        return graphqlFallback();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KEYCLOAK FALLBACK
    // Called when: keycloakCircuitBreaker is OPEN
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Fallback for Keycloak (OAuth2/OIDC Authorization Server).
     * Called when: keycloakCircuitBreaker is OPEN
     */
    @GetMapping("/identity")
    public Mono<ResponseEntity<Map<String, Object>>> keycloakFallback() {
        log.warn("[CircuitBreaker] Keycloak circuit OPEN");
        return Mono.just(createFallbackResponse(
                "Keycloak",
                "Authentication service is temporarily unavailable."
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER
    // ═══════════════════════════════════════════════════════════════════════════

    private ResponseEntity<Map<String, Object>> createFallbackResponse(String service, String message) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(Map.of(
                        "success", false,
                        "service", service,
                        "message", message,
                        "errorCode", "CIRCUIT_BREAKER_OPEN",
                        "timestamp", LocalDateTime.now().toString(),
                        "retryAfter", 30
                ));
    }
}
