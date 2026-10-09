package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.web.graphql.dto.*;
import com.pml.catalog.repository.ApprovalTimelineRepository;
import com.pml.catalog.service.ApprovalTimelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Implementation of ApprovalTimelineService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalTimelineServiceImpl implements ApprovalTimelineService {

    private final ApprovalTimelineRepository timelineRepository;

    // ==========================================
    // Single Entity Operations
    // ==========================================

    @Override
    public Mono<ApprovalTimeline> findByEventId(String eventId) {
        return timelineRepository.findByEventId(eventId);
    }

    @Override
    public Mono<ApprovalTimeline> save(ApprovalTimeline timeline) {
        return timelineRepository.save(timeline);
    }

    // ==========================================
    // Offset Pagination Queries
    // ==========================================

    @Override
    public Mono<ApprovalTimelineOffsetPage> findTimelinesOffsetPagination(
            ApprovalTimelineFilterInput filter, OffsetPaginationInput pagination) {

        int pageNumber = pagination != null && pagination.page() != null ? pagination.page() : 0;
        int pageSize = pagination != null && pagination.size() != null ? pagination.size() : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        // Build query based on filter
        Flux<ApprovalTimeline> timelines;
        Mono<Long> count;

        if (filter != null && filter.getStatus() != null) {
            timelines = timelineRepository.findByCurrentStatus(filter.getStatus(), pageable);
            count = timelineRepository.countByCurrentStatus(filter.getStatus());
        } else if (filter != null && Boolean.TRUE.equals(filter.getIsOverdue())) {
            timelines = timelineRepository.findOverdueTimelines(pageable);
            count = timelineRepository.countOverdue();
        } else if (filter != null && Boolean.TRUE.equals(filter.getHasActiveEscalation())) {
            timelines = timelineRepository.findWithActiveEscalation(pageable);
            count = timelineRepository.countWithActiveEscalation();
        } else if (filter != null && filter.getSearchQuery() != null) {
            timelines = timelineRepository.searchByEventTitle(filter.getSearchQuery(), pageable);
            count = timelines.count();
        } else {
            timelines = timelineRepository.findAllBy(pageable);
            count = timelineRepository.count();
        }

        return Mono.zip(timelines.collectList(), count)
                .map(tuple -> ApprovalTimelineOffsetPage.of(tuple.getT1(), pageNumber, pageSize, tuple.getT2()));
    }

    @Override
    public Mono<ApprovalTimelineOffsetPage> findByOrganizerOffsetPagination(
            String organizerId, OffsetPaginationInput pagination) {

        int pageNumber = pagination != null && pagination.page() != null ? pagination.page() : 0;
        int pageSize = pagination != null && pagination.size() != null ? pagination.size() : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        return Mono.zip(
                timelineRepository.findByOrganizerId(organizerId, pageable).collectList(),
                timelineRepository.countByOrganizerId(organizerId)
        ).map(tuple -> ApprovalTimelineOffsetPage.of(tuple.getT1(), pageNumber, pageSize, tuple.getT2()));
    }

    @Override
    public Mono<ApprovalTimelineOffsetPage> findPendingOffsetPagination(OffsetPaginationInput pagination) {
        int pageNumber = pagination != null && pagination.page() != null ? pagination.page() : 0;
        int pageSize = pagination != null && pagination.size() != null ? pagination.size() : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        return Mono.zip(
                timelineRepository.findPendingApprovalTimelines(pageable).collectList(),
                timelineRepository.countPendingApproval()
        ).map(tuple -> ApprovalTimelineOffsetPage.of(tuple.getT1(), pageNumber, pageSize, tuple.getT2()));
    }

    @Override
    public Mono<ApprovalTimelineOffsetPage> findOverdueOffsetPagination(OffsetPaginationInput pagination) {
        int pageNumber = pagination != null && pagination.page() != null ? pagination.page() : 0;
        int pageSize = pagination != null && pagination.size() != null ? pagination.size() : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        return Mono.zip(
                timelineRepository.findOverdueTimelines(pageable).collectList(),
                timelineRepository.countOverdue()
        ).map(tuple -> ApprovalTimelineOffsetPage.of(tuple.getT1(), pageNumber, pageSize, tuple.getT2()));
    }
}
