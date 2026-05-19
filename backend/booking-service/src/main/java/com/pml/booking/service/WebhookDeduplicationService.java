package com.pml.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;

/**
 * Webhook Deduplication Service
 *
 * <p>Prevents duplicate processing of webhooks using Redis as a distributed cache.
 * Webhooks can be retried by PawaPay if they don't receive a timely response,
 * so this service ensures idempotent processing.</p>
 *
 * <h2>Deduplication Strategy</h2>
 * <p>Each webhook is identified by a unique key composed of:</p>
 * <ul>
 *   <li>Transaction type (deposit, refund, payout)</li>
 *   <li>Transaction ID (depositId, refundId, payoutId)</li>
 *   <li>Status (COMPLETED, FAILED)</li>
 * </ul>
 *
 * <p>The composite key ensures that:</p>
 * <ul>
 *   <li>Same transaction with same status is only processed once</li>
 *   <li>Status changes (e.g., PENDING -> COMPLETED) are still processed</li>
 * </ul>
 *
 * <h2>TTL Configuration</h2>
 * <p>Deduplication keys expire after 24 hours to prevent unbounded growth
 * while still handling delayed retries from PawaPay.</p>
 *
 * <h2>OWASP Compliance</h2>
 * <p>A04:2021 Insecure Design - Prevents replay attacks and duplicate processing
 * that could lead to double-crediting escrow accounts.</p>
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDeduplicationService {

    private static final String REDIS_KEY_PREFIX = "webhook:processed:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * Check if a webhook has already been processed and mark it if not.
     *
     * <p>Uses Redis SETNX (set if not exists) for atomic check-and-set operation.
     * This is safe for distributed deployments with multiple service instances.</p>
     *
     * @param transactionType Type of transaction (deposit, refund, payout)
     * @param transactionId   Unique transaction identifier
     * @param status          Webhook status (COMPLETED, FAILED, etc.)
     * @return Mono<Boolean> - true if this is the first time processing, false if duplicate
     */
    public Mono<Boolean> tryMarkAsProcessed(String transactionType, String transactionId, String status) {
        String key = buildDeduplicationKey(transactionType, transactionId, status);
        String value = Instant.now().toString();

        return redisTemplate.opsForValue()
                .setIfAbsent(key, value, DEFAULT_TTL)
                .doOnSuccess(result -> {
                    if (Boolean.TRUE.equals(result)) {
                        log.debug("Webhook marked as processing: type={}, id={}, status={}",
                                transactionType, transactionId, status);
                    } else {
                        log.warn("Duplicate webhook detected: type={}, id={}, status={}",
                                transactionType, transactionId, status);
                    }
                })
                .onErrorResume(error -> {
                    // If Redis is unavailable, allow processing (fail-open for availability)
                    // The existing payment-level idempotency in PaymentAttempt provides backup protection
                    log.error("Redis unavailable for deduplication check, allowing processing: type={}, id={}",
                            transactionType, transactionId, error);
                    return Mono.just(true);
                });
    }

    /**
     * Check if a webhook has already been processed without marking it.
     *
     * @param transactionType Type of transaction
     * @param transactionId   Unique transaction identifier
     * @param status          Webhook status
     * @return Mono<Boolean> - true if already processed, false if not
     */
    public Mono<Boolean> isAlreadyProcessed(String transactionType, String transactionId, String status) {
        String key = buildDeduplicationKey(transactionType, transactionId, status);

        return redisTemplate.hasKey(key)
                .onErrorResume(error -> {
                    log.error("Redis unavailable for deduplication check: type={}, id={}",
                            transactionType, transactionId, error);
                    return Mono.just(false);
                });
    }

    /**
     * Mark a webhook as processed (for use after successful processing).
     *
     * @param transactionType Type of transaction
     * @param transactionId   Unique transaction identifier
     * @param status          Webhook status
     * @return Mono<Boolean> - true if marked, false if already existed
     */
    public Mono<Boolean> markAsProcessed(String transactionType, String transactionId, String status) {
        String key = buildDeduplicationKey(transactionType, transactionId, status);
        String value = Instant.now().toString();

        return redisTemplate.opsForValue()
                .set(key, value, DEFAULT_TTL)
                .thenReturn(true)
                .onErrorResume(error -> {
                    log.error("Failed to mark webhook as processed: type={}, id={}",
                            transactionType, transactionId, error);
                    return Mono.just(false);
                });
    }

    /**
     * Remove processing marker (for use when processing fails and should be retried).
     *
     * @param transactionType Type of transaction
     * @param transactionId   Unique transaction identifier
     * @param status          Webhook status
     * @return Mono<Boolean> - true if removed
     */
    public Mono<Boolean> clearProcessingMarker(String transactionType, String transactionId, String status) {
        String key = buildDeduplicationKey(transactionType, transactionId, status);

        return redisTemplate.delete(key)
                .map(count -> count > 0)
                .onErrorResume(error -> {
                    log.error("Failed to clear processing marker: type={}, id={}",
                            transactionType, transactionId, error);
                    return Mono.just(false);
                });
    }

    /**
     * Build a Redis key for deduplication.
     */
    private String buildDeduplicationKey(String transactionType, String transactionId, String status) {
        return REDIS_KEY_PREFIX + transactionType + ":" + transactionId + ":" + status;
    }
}
