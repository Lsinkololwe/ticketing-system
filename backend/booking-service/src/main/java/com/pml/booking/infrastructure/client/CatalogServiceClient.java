package com.pml.booking.infrastructure.client;

import com.pml.booking.infrastructure.client.dto.*;
import com.pml.shared.dto.EventSummaryDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
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
            WebClient.Builder webClientBuilder,
            @Value("${services.catalog.url:http://localhost:8081}") String catalogServiceUrl) {
        this.webClient = webClientBuilder
                .baseUrl(catalogServiceUrl)
                .build();
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

    /**
     * Get ticket category for an event
     */
    public Mono<EventSummaryDto.TicketCategoryDto> getTicketCategory(String eventId, String categoryCode) {
        log.debug("Fetching ticket category {} for event: {}", categoryCode, eventId);
        return webClient.get()
                .uri("/api/internal/events/{id}/categories/{code}", eventId, categoryCode)
                .retrieve()
                .bodyToMono(EventSummaryDto.TicketCategoryDto.class)
                .doOnSuccess(cat -> log.debug("Category fetched successfully: {}/{}", eventId, categoryCode))
                .doOnError(error -> log.error("Failed to fetch category: {}/{}", eventId, categoryCode, error));
    }

    /**
     * Update sold tickets count for an event
     */
    public Mono<Void> updateSoldTickets(String eventId, int count) {
        log.debug("Updating sold tickets for event: {} by {}", eventId, count);
        return webClient.put()
                .uri("/api/internal/events/{id}/sold-tickets", eventId)
                .bodyValue(new UpdateSoldTicketsRequest(count))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.debug("Sold tickets updated for event: {}", eventId))
                .doOnError(error -> log.error("Failed to update sold tickets: {}", eventId, error));
    }

    /**
     * Check if event is available for purchase
     */
    public Mono<Boolean> isEventAvailable(String eventId) {
        log.debug("Checking event availability: {}", eventId);
        return webClient.get()
                .uri("/api/internal/events/{id}/available", eventId)
                .retrieve()
                .bodyToMono(Boolean.class)
                .doOnSuccess(available -> log.debug("Event {} availability: {}", eventId, available))
                .doOnError(error -> log.error("Failed to check availability: {}", eventId, error));
    }

    public record UpdateSoldTicketsRequest(int count) {}

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

        return webClient.post()
                .uri("/api/internal/inventory/tiers/{tierId}/reserve", tierId)
                .bodyValue(new InventoryReservationRequest(quantity, reservationId))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response ->
                        response.bodyToMono(InventoryReservationResult.class)
                                .flatMap(result -> {
                                    log.warn("Reservation failed for tier {}: {}", tierId, result.errorMessage());
                                    return Mono.just(result);
                                })
                                .then(Mono.empty())
                )
                .bodyToMono(InventoryReservationResult.class)
                .doOnSuccess(result -> {
                    if (result.success()) {
                        log.debug("Inventory reserved for tier {}: {} tickets", tierId, quantity);
                    } else {
                        log.warn("Inventory reservation failed for tier {}: {}", tierId, result.errorMessage());
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
                .bodyValue(new InventoryRestoreRequest(quantity, reason, null))
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
