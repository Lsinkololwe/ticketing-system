package com.pml.catalog.repository;

import com.pml.catalog.domain.enums.ApprovalNotificationType;
import com.pml.catalog.domain.model.ApprovalNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Repository for ApprovalNotification.
 */
@Repository
public interface ApprovalNotificationRepository extends ReactiveMongoRepository<ApprovalNotification, String> {

    // ==========================================
    // Single Entity Queries
    // ==========================================

    /**
     * Find notifications by event ID
     */
    Flux<ApprovalNotification> findByEventId(String eventId);

    /**
     * Find notifications by recipient
     */
    Flux<ApprovalNotification> findByRecipientId(String recipientId, Pageable pageable);

    /**
     * Find unread notifications by recipient
     */
    @Query("{ 'recipientId': ?0, 'readAt': null }")
    Flux<ApprovalNotification> findUnreadByRecipientId(String recipientId, Pageable pageable);

    /**
     * Find notifications by type and event
     */
    Flux<ApprovalNotification> findByEventIdAndType(String eventId, ApprovalNotificationType type);

    // ==========================================
    // Count Queries
    // ==========================================

    /**
     * Count unread notifications for a recipient
     */
    @Query(value = "{ 'recipientId': ?0, 'readAt': null }", count = true)
    Mono<Long> countUnreadByRecipientId(String recipientId);

    /**
     * Count notifications by event
     */
    Mono<Long> countByEventId(String eventId);

    // ==========================================
    // Retry and Scheduled Job Queries
    // ==========================================

    /**
     * Find notifications due for retry (for scheduled job)
     */
    @Query("{ 'failedAt': { $ne: null }, 'nextRetryAt': { $lte: ?0 }, 'retryCount': { $lt: ?1 } }")
    Flux<ApprovalNotification> findDueForRetry(LocalDateTime now, int maxRetries);

    /**
     * Find failed notifications that exceeded max retries (for alerting)
     */
    @Query("{ 'failedAt': { $ne: null }, 'retryCount': { $gte: ?0 } }")
    Flux<ApprovalNotification> findPermanentlyFailed(int maxRetries);

    // ==========================================
    // Cleanup Queries
    // ==========================================

    /**
     * Find old delivered notifications (for cleanup)
     */
    @Query("{ 'deliveredAt': { $lt: ?0 } }")
    Flux<ApprovalNotification> findOldDeliveredNotifications(LocalDateTime olderThan);

    /**
     * Find old read notifications (for cleanup)
     */
    @Query("{ 'readAt': { $lt: ?0 } }")
    Flux<ApprovalNotification> findOldReadNotifications(LocalDateTime olderThan);
}
