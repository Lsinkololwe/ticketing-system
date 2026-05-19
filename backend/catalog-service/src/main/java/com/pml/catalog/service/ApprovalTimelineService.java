package com.pml.catalog.service;

import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.dto.ApprovalTimelineConnection;
import com.pml.catalog.dto.ApprovalTimelineFilterInput;
import com.pml.catalog.dto.ApprovalTimelineOffsetPage;
import com.pml.catalog.dto.CursorPaginationInput;
import com.pml.catalog.dto.OffsetPaginationInput;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for managing approval timelines.
 */
public interface ApprovalTimelineService {

    // ==========================================
    // Single Entity Operations
    // ==========================================

    /**
     * Get approval timeline for an event.
     */
    Mono<ApprovalTimeline> findByEventId(String eventId);

    /**
     * Create or get approval timeline for an event.
     */
    Mono<ApprovalTimeline> getOrCreateTimeline(String eventId, String eventTitle,
                                                String organizerId, String organizerName);

    /**
     * Save/update an approval timeline.
     */
    Mono<ApprovalTimeline> save(ApprovalTimeline timeline);

    // ==========================================
    // Offset Pagination Queries (Admin Dashboard)
    // ==========================================

    /**
     * Get timelines with optional filter - offset pagination.
     */
    Mono<ApprovalTimelineOffsetPage> findTimelinesOffsetPagination(
            ApprovalTimelineFilterInput filter, OffsetPaginationInput pagination);

    /**
     * Get timelines by organizer - offset pagination.
     */
    Mono<ApprovalTimelineOffsetPage> findByOrganizerOffsetPagination(
            String organizerId, OffsetPaginationInput pagination);

    /**
     * Get pending approval timelines - offset pagination.
     */
    Mono<ApprovalTimelineOffsetPage> findPendingOffsetPagination(OffsetPaginationInput pagination);

    /**
     * Get overdue timelines - offset pagination.
     */
    Mono<ApprovalTimelineOffsetPage> findOverdueOffsetPagination(OffsetPaginationInput pagination);

    // ==========================================
    // Cursor Pagination Queries (Mobile Admin)
    // ==========================================

    /**
     * Get timelines with optional filter - cursor pagination.
     */
    Mono<ApprovalTimelineConnection> findTimelinesCursorPagination(
            ApprovalTimelineFilterInput filter, CursorPaginationInput pagination);

    /**
     * Get timelines by organizer - cursor pagination.
     */
    Mono<ApprovalTimelineConnection> findByOrganizerCursorPagination(
            String organizerId, CursorPaginationInput pagination);

    /**
     * Get pending approval timelines - cursor pagination.
     */
    Mono<ApprovalTimelineConnection> findPendingCursorPagination(CursorPaginationInput pagination);

    /**
     * Get overdue timelines - cursor pagination.
     */
    Mono<ApprovalTimelineConnection> findOverdueCursorPagination(CursorPaginationInput pagination);

    // ==========================================
    // Scheduled Job Helpers
    // ==========================================

    /**
     * Find timelines that have become overdue.
     */
    Flux<ApprovalTimeline> findNewlyOverdueTimelines();

    /**
     * Mark timelines as overdue.
     */
    Mono<Integer> markOverdueTimelines();
}
