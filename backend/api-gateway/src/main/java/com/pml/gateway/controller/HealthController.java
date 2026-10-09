package com.pml.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Health Controller
 *
 * Provides custom health and info endpoints for the API Gateway.
 */
@RestController
@RequestMapping("/")
public class HealthController {

    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * The injected platform clock, not {@code LocalDateTime.now()}.
     *
     * <p>A <b>zone-less local</b> time in a response body would let a probe comparing the gateway's
     * timestamp against a service's UTC one see them disagree by the host's offset with nothing to
     * say which was which. {@link Instant} carries the zone, and the platform clock is UTC.</p>
     */
    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", applicationName);
        health.put("timestamp", Instant.now(clock).toString());
        return Mono.just(ResponseEntity.ok(health));
    }

    @GetMapping("/info")
    public Mono<ResponseEntity<Map<String, Object>>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "API Gateway");
        info.put("description", "Central gateway for Event Ticketing microservices");
        info.put("version", "1.0.0");
        info.put("routes", Map.of(
                "catalog", "/graphql/catalog/**, /api/events/**",
                "booking", "/graphql/booking/**, /api/tickets/**",
                "identity", "/graphql/identity/**, /api/auth/**, /api/users/**"
        ));
        return Mono.just(ResponseEntity.ok(info));
    }
}
