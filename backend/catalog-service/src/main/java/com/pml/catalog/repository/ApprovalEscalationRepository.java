package com.pml.catalog.repository;

import com.pml.catalog.domain.enums.EscalationStatus;
import com.pml.catalog.domain.model.ApprovalEscalation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Repository for ApprovalEscalation with pagination support.
 */
@Repository
public interface ApprovalEscalationRepository extends ReactiveMongoRepository<ApprovalEscalation, String> {

    // ==========================================
    // Single Entity Queries
    // ==========================================

    /**
     * Find escalation by event ID
     */
    Mono<ApprovalEscalation> findByEventId(String eventId);

    /**
     * Find active escalation by event ID
     */
    @Query("{ 'eventId': ?0, 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }")
    Mono<ApprovalEscalation> findActiveByEventId(String eventId);

    /**
     * Check if active escalation exists for event
     */
    @Query(value = "{ 'eventId': ?0, 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }", exists = true)
    Mono<Boolean> hasActiveEscalationForEvent(String eventId);

    // ==========================================
    // Offset Pagination Queries (Admin Dashboard)
    // ==========================================

    /**
     * All escalations - offset pagination
     */
    Flux<ApprovalEscalation> findAllBy(Pageable pageable);

    /**
     * Escalations by status - offset pagination
     */
    Flux<ApprovalEscalation> findByStatus(EscalationStatus status, Pageable pageable);

    /**
     * Active escalations (PENDING or ACKNOWLEDGED) - offset pagination
     */
    @Query("{ 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }")
    Flux<ApprovalEscalation> findActiveEscalations(Pageable pageable);

    /**
     * Escalations assigned to an admin - offset pagination
     */
    Flux<ApprovalEscalation> findByEscalatedTo(String adminId, Pageable pageable);

    /**
     * Active escalations assigned to an admin - offset pagination
     */
    @Query("{ 'escalatedTo': ?0, 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }")
    Flux<ApprovalEscalation> findActiveByEscalatedTo(String adminId, Pageable pageable);

    // ==========================================
    // Cursor Pagination Queries (Mobile Admin)
    // ==========================================

    /**
     * Active escalations - first page (cursor)
     */
    @Query("{ 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }")
    Flux<ApprovalEscalation> findActiveFirstPage(Pageable pageable);

    /**
     * Active escalations - after cursor
     */
    @Query("{ 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] }, '_id': { $gt: ?0 } }")
    Flux<ApprovalEscalation> findActiveAfterCursor(String afterId, Pageable pageable);

    /**
     * Escalations for admin - first page (cursor)
     */
    @Query("{ 'escalatedTo': ?0, 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }")
    Flux<ApprovalEscalation> findForAdminFirstPage(String adminId, Pageable pageable);

    /**
     * Escalations for admin - after cursor
     */
    @Query("{ 'escalatedTo': ?0, 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] }, '_id': { $gt: ?1 } }")
    Flux<ApprovalEscalation> findForAdminAfterCursor(String adminId, String afterId, Pageable pageable);

    // ==========================================
    // Count Queries
    // ==========================================

    Mono<Long> countByStatus(EscalationStatus status);

    @Query(value = "{ 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }", count = true)
    Mono<Long> countActive();

    @Query(value = "{ 'escalatedTo': ?0, 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] } }", count = true)
    Mono<Long> countActiveByEscalatedTo(String adminId);

    /**
     * Count escalations triggered this week (for stats)
     */
    @Query(value = "{ 'triggeredAt': { $gte: ?0 } }", count = true)
    Mono<Long> countTriggeredAfter(LocalDateTime since);

    // ==========================================
    // Reminder and Scheduled Job Queries
    // ==========================================

    /**
     * Find escalations due for reminder (for scheduled job)
     */
    @Query("{ 'status': { $in: ['PENDING', 'ACKNOWLEDGED'] }, 'nextReminderAt': { $lte: ?0 } }")
    Flux<ApprovalEscalation> findDueForReminder(LocalDateTime now);

    /**
     * Find old pending escalations that should be expired
     */
    @Query("{ 'status': 'PENDING', 'triggeredAt': { $lt: ?0 } }")
    Flux<ApprovalEscalation> findExpiredEscalations(LocalDateTime expiryThreshold);
}
