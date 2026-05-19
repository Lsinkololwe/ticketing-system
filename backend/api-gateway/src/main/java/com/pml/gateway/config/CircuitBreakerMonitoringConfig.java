package com.pml.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Circuit Breaker Monitoring Configuration.
 *
 * <h2>Why This Class Exists</h2>
 * <pre>
 * YAML configuration handles WHAT thresholds to use:
 *   - failureRateThreshold: 50
 *   - waitDurationInOpenState: 60s
 *
 * This class handles WHAT HAPPENS when state changes:
 *   - Log state transitions (CLOSED → OPEN → HALF_OPEN)
 *   - Alert on circuit opening (service down)
 *   - Track metrics for dashboards
 *
 * YAML cannot register event handlers - requires programmatic configuration.
 * </pre>
 *
 * <h2>Circuit Breaker State Machine</h2>
 * <pre>
 *     ┌─────────────────────────────────────────────────────────────┐
 *     │                                                             │
 *     │    ┌────────┐   failure rate    ┌────────┐                 │
 *     │    │ CLOSED │ ──── exceeds ────▶│  OPEN  │                 │
 *     │    │(normal)│    threshold      │(reject)│                 │
 *     │    └────┬───┘                   └────┬───┘                 │
 *     │         │                            │                      │
 *     │         │ success                    │ waitDuration         │
 *     │         │                            │ expires              │
 *     │         │                            ▼                      │
 *     │         │                      ┌───────────┐               │
 *     │         └───────────────────── │ HALF_OPEN │               │
 *     │              if tests pass     │  (test)   │               │
 *     │                                └─────┬─────┘               │
 *     │                                      │                      │
 *     │                               if tests fail                 │
 *     │                                      │                      │
 *     │                                      ▼                      │
 *     │                                back to OPEN                 │
 *     └─────────────────────────────────────────────────────────────┘
 *
 * Events fired on each transition:
 *   - onStateTransition: CLOSED_TO_OPEN, OPEN_TO_HALF_OPEN, HALF_OPEN_TO_CLOSED
 *   - onError: Every failed call (used for metrics)
 *   - onSuccess: Every successful call
 *   - onCallNotPermitted: When circuit is OPEN and call is rejected
 * </pre>
 *
 * <h2>Integration with Gateway Routes</h2>
 * <pre>
 * In application.yml:
 *   filters:
 *     - name: CircuitBreaker
 *       args:
 *         name: apolloRouterCircuitBreaker  ◀─── This name links to instance below
 *         fallbackUri: forward:/fallback/graphql
 *
 * In resilience4j config:
 *   instances:
 *     apolloRouterCircuitBreaker:  ◀─── Same name, thresholds defined here
 *       failureRateThreshold: 30
 *
 * In this class:
 *   factory.addCircuitBreakerCustomizer(..., "apolloRouterCircuitBreaker")
 *                                            ▲
 *                                            └─── Same name, event handlers here
 * </pre>
 */
@Slf4j
@Configuration
public class CircuitBreakerMonitoringConfig {

    /**
     * Registers event handlers for all circuit breaker instances.
     *
     * <p>This customizer adds logging for state transitions across all
     * circuit breakers defined in application.yml. When a circuit opens,
     * closes, or enters half-open state, it will be logged.</p>
     *
     * <p><b>Production Enhancement:</b> Replace log statements with:</p>
     * <ul>
     *   <li>Metrics export (Prometheus counter for state changes)</li>
     *   <li>Alerting service (PagerDuty, Slack webhook on OPEN)</li>
     *   <li>Distributed tracing (add circuit state to span tags)</li>
     * </ul>
     *
     * @return Customizer that adds event handlers to circuit breaker instances
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> circuitBreakerMonitoringCustomizer() {
        return factory -> {
            // Register event handlers for each circuit breaker instance
            // Names must match those in application.yml resilience4j.circuitbreaker.instances
            String[] circuitBreakerNames = {
                "apolloRouterCircuitBreaker",
                "keycloakCircuitBreaker"
            };

            factory.addCircuitBreakerCustomizer(
                circuitBreaker -> {
                    String name = circuitBreaker.getName();

                    // ─────────────────────────────────────────────────────────
                    // STATE TRANSITION EVENTS
                    // Fired when circuit changes state (most important for ops)
                    // ─────────────────────────────────────────────────────────
                    circuitBreaker.getEventPublisher()
                        .onStateTransition(event -> {
                            CircuitBreaker.State fromState = event.getStateTransition().getFromState();
                            CircuitBreaker.State toState = event.getStateTransition().getToState();

                            if (toState == CircuitBreaker.State.OPEN) {
                                // CRITICAL: Service is failing, circuit opened
                                log.error("[CircuitBreaker] {} OPENED - downstream service failing. " +
                                          "Requests will be rejected for waitDuration. Previous state: {}",
                                          name, fromState);
                                // TODO: Add alerting here (Slack, PagerDuty, etc.)
                                // alertService.sendCriticalAlert("Circuit " + name + " OPENED");
                            } else if (toState == CircuitBreaker.State.HALF_OPEN) {
                                // INFO: Testing if service recovered
                                log.warn("[CircuitBreaker] {} HALF_OPEN - testing if {} recovered. " +
                                         "Allowing limited requests through.",
                                         name, name.replace("CircuitBreaker", ""));
                            } else if (toState == CircuitBreaker.State.CLOSED) {
                                // GOOD: Service recovered, normal operation resumed
                                log.info("[CircuitBreaker] {} CLOSED - service recovered. " +
                                         "Normal operation resumed. Previous state: {}",
                                         name, fromState);
                            }
                        });

                    // ─────────────────────────────────────────────────────────
                    // ERROR EVENTS
                    // Fired on every failed call (useful for debugging)
                    // ─────────────────────────────────────────────────────────
                    circuitBreaker.getEventPublisher()
                        .onError(event -> {
                            // Log at DEBUG to avoid noise; errors are expected during failures
                            log.debug("[CircuitBreaker] {} recorded error: {} - {}",
                                name,
                                event.getThrowable().getClass().getSimpleName(),
                                event.getThrowable().getMessage());
                        });

                    // ─────────────────────────────────────────────────────────
                    // CALL NOT PERMITTED EVENTS
                    // Fired when circuit is OPEN and request is rejected
                    // ─────────────────────────────────────────────────────────
                    circuitBreaker.getEventPublisher()
                        .onCallNotPermitted(event -> {
                            // This means fallback is being used
                            log.warn("[CircuitBreaker] {} rejected call - circuit is OPEN. " +
                                     "Request routed to fallback endpoint.",
                                     name);
                        });

                    // ─────────────────────────────────────────────────────────
                    // SLOW CALL EVENTS
                    // Fired when call duration exceeds slowCallDurationThreshold
                    // ─────────────────────────────────────────────────────────
                    circuitBreaker.getEventPublisher()
                        .onSlowCallRateExceeded(event -> {
                            log.warn("[CircuitBreaker] {} slow call rate exceeded threshold. " +
                                     "Current rate: {}%",
                                     name, event.getSlowCallRate());
                        });
                },
                circuitBreakerNames
            );
        };
    }
}
