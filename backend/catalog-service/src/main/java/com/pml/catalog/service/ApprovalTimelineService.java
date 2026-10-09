package com.pml.catalog.service;

import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.web.graphql.dto.ApprovalTimelineFilterInput;
import com.pml.catalog.web.graphql.dto.ApprovalTimelineOffsetPage;
import com.pml.catalog.web.graphql.dto.OffsetPaginationInput;
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

}
