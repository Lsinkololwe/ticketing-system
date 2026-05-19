package com.pml.catalog.repository;

import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.shared.constants.EventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Repository for ApprovalTimeline with pagination support.
 */
@Repository
public interface ApprovalTimelineRepository extends ReactiveMongoRepository<ApprovalTimeline, String> {

    // ==========================================
    // Single Entity Queries
    // ==========================================

    /**
     * Find timeline by event ID
     */
    Mono<ApprovalTimeline> findByEventId(String eventId);

    /**
     * Check if timeline exists for event
     */
    Mono<Boolean> existsByEventId(String eventId);

    // ==========================================
    // Offset Pagination Queries (Admin Dashboard)
    // ==========================================

    /**
     * All timelines - offset pagination
     */
    Flux<ApprovalTimeline> findAllBy(Pageable pageable);

    /**
     * Timelines by status - offset pagination
     */
    Flux<ApprovalTimeline> findByCurrentStatus(EventStatus status, Pageable pageable);

    /**
     * Timelines by organizer - offset pagination
     */
    Flux<ApprovalTimeline> findByOrganizerId(String organizerId, Pageable pageable);

    /**
     * Pending approval timelines - offset pagination
     */
    @Query("{ 'currentStatus': 'PENDING_APPROVAL' }")
    Flux<ApprovalTimeline> findPendingApprovalTimelines(Pageable pageable);

    /**
     * Overdue timelines - offset pagination
     */
    @Query("{ 'currentStatus': 'PENDING_APPROVAL', 'isOverdue': true }")
    Flux<ApprovalTimeline> findOverdueTimelines(Pageable pageable);

    /**
     * Timelines with active escalation - offset pagination
     */
    @Query("{ 'hasActiveEscalation': true }")
    Flux<ApprovalTimeline> findWithActiveEscalation(Pageable pageable);

    /**
     * Timelines assigned to a reviewer - offset pagination
     */
    Flux<ApprovalTimeline> findByAssignedReviewerId(String reviewerId, Pageable pageable);

    // ==========================================
    // Cursor Pagination Queries (Mobile Admin)
    // ==========================================

    /**
     * All timelines - first page (cursor)
     */
    Flux<ApprovalTimeline> findAllByOrderBySubmittedAtDesc(Pageable pageable);

    /**
     * Timelines after cursor
     */
    @Query("{ '_id': { $gt: ?0 } }")
    Flux<ApprovalTimeline> findAllAfterCursor(String afterId, Pageable pageable);

    /**
     * Pending timelines - first page (cursor)
     */
    @Query("{ 'currentStatus': 'PENDING_APPROVAL' }")
    Flux<ApprovalTimeline> findPendingFirstPage(Pageable pageable);

    /**
     * Pending timelines - after cursor
     */
    @Query("{ 'currentStatus': 'PENDING_APPROVAL', '_id': { $gt: ?0 } }")
    Flux<ApprovalTimeline> findPendingAfterCursor(String afterId, Pageable pageable);

    /**
     * Overdue timelines - first page (cursor)
     */
    @Query("{ 'currentStatus': 'PENDING_APPROVAL', 'isOverdue': true }")
    Flux<ApprovalTimeline> findOverdueFirstPage(Pageable pageable);

    /**
     * Overdue timelines - after cursor
     */
    @Query("{ 'currentStatus': 'PENDING_APPROVAL', 'isOverdue': true, '_id': { $gt: ?0 } }")
    Flux<ApprovalTimeline> findOverdueAfterCursor(String afterId, Pageable pageable);

    /**
     * Timelines by organizer - first page (cursor)
     */
    Flux<ApprovalTimeline> findByOrganizerIdOrderBySubmittedAtDesc(String organizerId, Pageable pageable);

    /**
     * Timelines by organizer - after cursor
     */
    @Query("{ 'organizerId': ?0, '_id': { $gt: ?1 } }")
    Flux<ApprovalTimeline> findByOrganizerAfterCursor(String organizerId, String afterId, Pageable pageable);

    // ==========================================
    // Count Queries (for pagination info)
    // ==========================================

    Mono<Long> countByCurrentStatus(EventStatus status);

    Mono<Long> countByOrganizerId(String organizerId);

    @Query(value = "{ 'currentStatus': 'PENDING_APPROVAL' }", count = true)
    Mono<Long> countPendingApproval();

    @Query(value = "{ 'currentStatus': 'PENDING_APPROVAL', 'isOverdue': true }", count = true)
    Mono<Long> countOverdue();

    @Query(value = "{ 'hasActiveEscalation': true }", count = true)
    Mono<Long> countWithActiveEscalation();

    Mono<Long> countByAssignedReviewerId(String reviewerId);

    // ==========================================
    // Filter Queries
    // ==========================================

    /**
     * Search by event title
     */
    @Query("{ 'eventTitle': { $regex: ?0, $options: 'i' } }")
    Flux<ApprovalTimeline> searchByEventTitle(String query, Pageable pageable);

    /**
     * Find timelines submitted after a date
     */
    Flux<ApprovalTimeline> findBySubmittedAtAfter(LocalDateTime date, Pageable pageable);

    /**
     * Find timelines submitted before a date
     */
    Flux<ApprovalTimeline> findBySubmittedAtBefore(LocalDateTime date, Pageable pageable);

    /**
     * Find timelines submitted between dates
     */
    Flux<ApprovalTimeline> findBySubmittedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // ==========================================
    // Bulk Update Helpers
    // ==========================================

    /**
     * Find all overdue timelines (for scheduled job)
     */
    @Query("{ 'currentStatus': 'PENDING_APPROVAL', 'slaDeadline': { $lt: ?0 }, 'isOverdue': false }")
    Flux<ApprovalTimeline> findNewlyOverdue(LocalDateTime now);

    /**
     * Find timelines due for SLA warning (for scheduled job)
     */
    @Query("{ 'currentStatus': 'PENDING_APPROVAL', 'slaDeadline': { $lt: ?0 }, 'isOverdue': false }")
    Flux<ApprovalTimeline> findApproachingSlaDeadline(LocalDateTime warningTime);
}
