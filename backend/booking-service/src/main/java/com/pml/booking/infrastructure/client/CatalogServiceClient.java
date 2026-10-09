package com.pml.booking.infrastructure.client;

import com.pml.shared.security.InternalServiceWebClients;
import com.pml.booking.infrastructure.client.dto.*;
import com.pml.shared.dto.EventSummaryDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Client for Catalog Service
 *
 * <p>Uses Resilience4j for circuit breaker, retry, and timeout protection.
 * Prevents cascading failures when catalog service is unavailable.</p>
 *
 * <h2>Resilience Patterns</h2>
 * <ul>
 *   <li><b>Circuit Breaker</b>: Opens after 50% failures, waits 30s before half-open</li>
 *   <li><b>Retry</b>: 3 attempts with exponential backoff</li>
 *   <li><b>Time Limiter</b>: 15s timeout per operation</li>
 * </ul>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A04:2021 - Insecure Design: Circuit breaker prevents resource exhaustion</li>
 * </ul>
 */
@Slf4j
@Component
public class CatalogServiceClient {

    private static final String CIRCUIT_BREAKER_NAME = "catalogService";
    private static final Duration FALLBACK_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;

    public CatalogServiceClient(
            InternalServiceWebClients clients,
            @Value("${services.catalog.url:http://localhost:8085}") String catalogServiceUrl) {
        this.webClient = clients.to(catalogServiceUrl);
    }

    /**
     * Get event summary by ID.
     *
     * <p>Protected by circuit breaker - returns empty Mono if service is unavailable.</p>
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "getEventByIdFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<EventSummaryDto> getEventById(String eventId) {
        log.debug("Fetching event from catalog service: {}", eventId);
        return webClient.get()
                .uri("/api/internal/events/{id}", eventId)
                .retrieve()
                .bodyToMono(EventSummaryDto.class)
                .doOnSuccess(event -> log.debug("Event fetched successfully: {}", eventId))
                .doOnError(error -> log.error("Failed to fetch event: {}", eventId, error));
    }

    /**
     * Fallback for getEventById when circuit is open or call fails.
     */
    private Mono<EventSummaryDto> getEventByIdFallback(String eventId, Throwable t) {
        log.warn("Circuit breaker fallback for getEventById({}): {}", eventId, t.getMessage());
        return Mono.empty();
    }

    // ========================================================================
    // INVENTORY MANAGEMENT OPERATIONS
    // ========================================================================

    /**
     * Reserve inventory for a pending purchase.
     *
     * <p>Called when creating a reservation. Holds inventory atomically
     * to prevent overselling.</p>
     *
     * <p>Protected by circuit breaker - returns failure result if service is unavailable.</p>
     *
     * @param tierId Ticket tier ID
     * @param quantity Number of tickets to reserve
     * @param reservationId Unique reservation identifier
     * @return Reservation result with success/failure status
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "reserveInventoryFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<InventoryReservationResult> reserveInventory(String tierId, int quantity, String reservationId) {
        log.debug("Reserving {} tickets for tier {} (reservation: {})", quantity, tierId, reservationId);

        // exchangeToMono, not retrieve().onStatus(...). The distinction this makes is the whole
        // point of the method:
        //
        //   4xx — a BUSINESS answer. Catalog returns 409 with a well-formed
        //         InventoryReservationResult when a tier cannot satisfy the request. "Sold out" is
        //         the platform working, and it must reach the caller as success=false. It must NOT
        //         reach the circuit breaker: tiers sell out during an on-sale, which is precisely
        //         when opening the breaker would fail every OTHER reservation too.
        //
        //   5xx — an INFRASTRUCTURE failure. Raised, so the breaker counts it and the fallback
        //         returns a refusal. Failing closed here is deliberate: reserving against
        //         inventory nobody confirmed is how a venue oversells.
        //
        // The previous form read the 409 body, logged it, and then returned Mono.empty() from the
        // onStatus handler. An empty handler suppresses the error, so WebClient continued to a body
        // that had already been consumed, doOnSuccess ran with null, and an ordinary sold-out threw
        // a NullPointerException the breaker counted as a failure.
        return webClient.post()
                .uri("/api/internal/inventory/tiers/{tierId}/reserve", tierId)
                .bodyValue(new InventoryReservationRequest(quantity, reservationId))
                .exchangeToMono(response -> {
                    if (response.statusCode().is5xxServerError()) {
                        return response.releaseBody().then(Mono.error(new IllegalStateException(
                                "catalog returned " + response.statusCode()
                                        + " reserving tier " + tierId)));
                    }
                    return response.bodyToMono(InventoryReservationResult.class)
                            // A 4xx with no parseable body is still a refusal, not a success. The
                            // switchIfEmpty is what stops an unreadable response becoming a
                            // reservation against inventory nobody checked.
                            .switchIfEmpty(Mono.fromSupplier(() -> InventoryReservationResult.failure(
                                    tierId, "catalog returned " + response.statusCode()
                                            + " with no readable body")));
                })
                .doOnNext(result -> {
                    if (result.success()) {
                        log.debug("Inventory reserved for tier {}: {} tickets", tierId, quantity);
                    } else {
                        log.warn("Inventory reservation refused for tier {}: {}",
                                tierId, result.errorMessage());
                    }
                });
    }

    /**
     * Fallback for reserveInventory when circuit is open or call fails.
     */
    private Mono<InventoryReservationResult> reserveInventoryFallback(String tierId, int quantity,
                                                                       String reservationId, Throwable t) {
        log.warn("Circuit breaker fallback for reserveInventory({}, {}, {}): {}",
                tierId, quantity, reservationId, t.getMessage());
        return Mono.just(InventoryReservationResult.failure(tierId,
                "Catalog service unavailable: " + t.getMessage()));
    }

    /**
     * Release reserved inventory back to available pool.
     *
     * <p>Called when a reservation expires or is cancelled.</p>
     *
     * <p>Protected by circuit breaker - returns failure result if service is unavailable.</p>
     *
     * @param tierId Ticket tier ID
     * @param quantity Number of tickets to release
     * @param reservationId Original reservation identifier
     * @return Operation result
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "releaseInventoryFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<InventoryOperationResult> releaseInventory(String tierId, int quantity, String reservationId) {
        log.debug("Releasing {} reserved tickets for tier {} (reservation: {})", quantity, tierId, reservationId);

        return webClient.post()
                .uri("/api/internal/inventory/tiers/{tierId}/release", tierId)
                .bodyValue(new InventoryReleaseRequest(quantity, reservationId))
                .retrieve()
                .bodyToMono(InventoryOperationResult.class)
                .doOnSuccess(result -> {
                    if (result.success()) {
                        log.debug("Inventory released for tier {}: {} tickets", tierId, quantity);
                    } else {
                        log.warn("Inventory release failed for tier {}: {}", tierId, result.errorMessage());
                    }
                });
    }

    /**
     * Fallback for releaseInventory when circuit is open or call fails.
     */
    private Mono<InventoryOperationResult> releaseInventoryFallback(String tierId, int quantity,
                                                                     String reservationId, Throwable t) {
        log.warn("Circuit breaker fallback for releaseInventory({}, {}, {}): {}",
                tierId, quantity, reservationId, t.getMessage());
        return Mono.just(InventoryOperationResult.failure("RELEASE", tierId,
                "Catalog service unavailable: " + t.getMessage()));
    }

    /**
     * Commit reserved inventory to sold state.
     *
     * <p>Called when payment succeeds.</p>
     *
     * <p>Protected by circuit breaker - returns failure result if service is unavailable.</p>
     *
     * @param tierId Ticket tier ID
     * @param quantity Number of tickets to commit
     * @param reservationId Original reservation identifier
     * @return Operation result
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "commitInventoryToSoldFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<InventoryOperationResult> commitInventoryToSold(String tierId, int quantity, String reservationId) {
        log.debug("Committing {} tickets to sold for tier {} (reservation: {})", quantity, tierId, reservationId);

        return webClient.post()
                .uri("/api/internal/inventory/tiers/{tierId}/commit", tierId)
                .bodyValue(new InventoryCommitRequest(quantity, reservationId))
                .retrieve()
                .bodyToMono(InventoryOperationResult.class)
                .doOnSuccess(result -> {
                    if (result.success()) {
                        log.debug("Inventory committed to sold for tier {}: {} tickets", tierId, quantity);
                    } else {
                        log.warn("Inventory commit failed for tier {}: {}", tierId, result.errorMessage());
                    }
                });
    }

    /**
     * Fallback for commitInventoryToSold when circuit is open or call fails.
     */
    private Mono<InventoryOperationResult> commitInventoryToSoldFallback(String tierId, int quantity,
                                                                          String reservationId, Throwable t) {
        log.warn("Circuit breaker fallback for commitInventoryToSold({}, {}, {}): {}",
                tierId, quantity, reservationId, t.getMessage());
        return Mono.just(InventoryOperationResult.failure("COMMIT", tierId,
                "Catalog service unavailable: " + t.getMessage()));
    }

    /**
     * Restore sold inventory back to available pool.
     *
     * <p>Called on refunds or chargebacks.</p>
     *
     * <p>Protected by circuit breaker - returns failure result if service is unavailable.</p>
     *
     * @param tierId Ticket tier ID
     * @param quantity Number of tickets to restore
     * @param reason Reason for restoration (REFUND, CHARGEBACK)
     * @return Operation result
     */
    @CircuitBreaker(name = CIRCUIT_BREAKER_NAME, fallbackMethod = "restoreInventoryFallback")
    @Retry(name = CIRCUIT_BREAKER_NAME)
    @TimeLimiter(name = CIRCUIT_BREAKER_NAME)
    public Mono<InventoryOperationResult> restoreInventory(String tierId, int quantity, String reason) {
        log.debug("Restoring {} sold tickets for tier {} (reason: {})", quantity, tierId, reason);

        return webClient.post()
                .uri("/api/internal/inventory/tiers/{tierId}/restore", tierId)
                .bodyValue(new InventoryRestoreRequest(quantity, reason))
                .retrieve()
                .bodyToMono(InventoryOperationResult.class)
                .doOnSuccess(result -> {
                    if (result.success()) {
                        log.debug("Inventory restored for tier {}: {} tickets ({})", tierId, quantity, reason);
                    } else {
                        log.warn("Inventory restore failed for tier {}: {}", tierId, result.errorMessage());
                    }
                });
    }

    /**
     * Fallback for restoreInventory when circuit is open or call fails.
     */
    private Mono<InventoryOperationResult> restoreInventoryFallback(String tierId, int quantity,
                                                                     String reason, Throwable t) {
        log.warn("Circuit breaker fallback for restoreInventory({}, {}, {}): {}",
                tierId, quantity, reason, t.getMessage());
        return Mono.just(InventoryOperationResult.failure("RESTORE", tierId,
                "Catalog service unavailable: " + t.getMessage()));
    }
}
