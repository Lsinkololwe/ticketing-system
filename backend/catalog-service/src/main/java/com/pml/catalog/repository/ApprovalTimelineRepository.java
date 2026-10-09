package com.pml.catalog.repository;

import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.shared.constants.EventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    // ==========================================
    // Filter Queries
    // ==========================================

    /**
     * Search by event title
     */
    @Query("{ 'eventTitle': { $regex: ?0, $options: 'i' } }")
    Flux<ApprovalTimeline> searchByEventTitle(String query, Pageable pageable);
}
