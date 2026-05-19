package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.dto.*;
import com.pml.catalog.repository.ApprovalTimelineRepository;
import com.pml.catalog.service.ApprovalTimelineService;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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
    public Mono<ApprovalTimeline> getOrCreateTimeline(String eventId, String eventTitle,
                                                       String organizerId, String organizerName) {
        return timelineRepository.findByEventId(eventId)
                .switchIfEmpty(Mono.defer(() -> {
                    ApprovalTimeline timeline = ApprovalTimeline.create(eventId, eventTitle, organizerId, organizerName);
                    return timelineRepository.save(timeline);
                }));
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

    // ==========================================
    // Cursor Pagination Queries
    // ==========================================

    @Override
    public Mono<ApprovalTimelineConnection> findTimelinesCursorPagination(
            ApprovalTimelineFilterInput filter, CursorPaginationInput pagination) {

        int first = pagination != null && pagination.getFirst() != null ? pagination.getFirst() : 20;
        String afterCursor = pagination != null ? pagination.getAfter() : null;
        Pageable pageable = PageRequest.of(0, first + 1); // Fetch one extra for hasNextPage

        Flux<ApprovalTimeline> timelines;
        Mono<Long> count;

        if (filter != null && Boolean.TRUE.equals(filter.getIsOverdue())) {
            timelines = afterCursor != null
                    ? timelineRepository.findOverdueAfterCursor(afterCursor, pageable)
                    : timelineRepository.findOverdueFirstPage(pageable);
            count = timelineRepository.countOverdue();
        } else if (filter != null && filter.getStatus() == EventStatus.PENDING_APPROVAL) {
            timelines = afterCursor != null
                    ? timelineRepository.findPendingAfterCursor(afterCursor, pageable)
                    : timelineRepository.findPendingFirstPage(pageable);
            count = timelineRepository.countPendingApproval();
        } else {
            timelines = afterCursor != null
                    ? timelineRepository.findAllAfterCursor(afterCursor, pageable)
                    : timelineRepository.findAllByOrderBySubmittedAtDesc(pageable);
            count = timelineRepository.count();
        }

        return buildConnection(timelines, count, first, afterCursor);
    }

    @Override
    public Mono<ApprovalTimelineConnection> findByOrganizerCursorPagination(
            String organizerId, CursorPaginationInput pagination) {

        int first = pagination != null && pagination.getFirst() != null ? pagination.getFirst() : 20;
        String afterCursor = pagination != null ? pagination.getAfter() : null;
        Pageable pageable = PageRequest.of(0, first + 1);

        Flux<ApprovalTimeline> timelines = afterCursor != null
                ? timelineRepository.findByOrganizerAfterCursor(organizerId, afterCursor, pageable)
                : timelineRepository.findByOrganizerIdOrderBySubmittedAtDesc(organizerId, pageable);
        Mono<Long> count = timelineRepository.countByOrganizerId(organizerId);

        return buildConnection(timelines, count, first, afterCursor);
    }

    @Override
    public Mono<ApprovalTimelineConnection> findPendingCursorPagination(CursorPaginationInput pagination) {
        int first = pagination != null && pagination.getFirst() != null ? pagination.getFirst() : 20;
        String afterCursor = pagination != null ? pagination.getAfter() : null;
        Pageable pageable = PageRequest.of(0, first + 1);

        Flux<ApprovalTimeline> timelines = afterCursor != null
                ? timelineRepository.findPendingAfterCursor(afterCursor, pageable)
                : timelineRepository.findPendingFirstPage(pageable);
        Mono<Long> count = timelineRepository.countPendingApproval();

        return buildConnection(timelines, count, first, afterCursor);
    }

    @Override
    public Mono<ApprovalTimelineConnection> findOverdueCursorPagination(CursorPaginationInput pagination) {
        int first = pagination != null && pagination.getFirst() != null ? pagination.getFirst() : 20;
        String afterCursor = pagination != null ? pagination.getAfter() : null;
        Pageable pageable = PageRequest.of(0, first + 1);

        Flux<ApprovalTimeline> timelines = afterCursor != null
                ? timelineRepository.findOverdueAfterCursor(afterCursor, pageable)
                : timelineRepository.findOverdueFirstPage(pageable);
        Mono<Long> count = timelineRepository.countOverdue();

        return buildConnection(timelines, count, first, afterCursor);
    }

    // ==========================================
    // Scheduled Job Helpers
    // ==========================================

    @Override
    public Flux<ApprovalTimeline> findNewlyOverdueTimelines() {
        return timelineRepository.findNewlyOverdue(LocalDateTime.now());
    }

    @Override
    public Mono<Integer> markOverdueTimelines() {
        return findNewlyOverdueTimelines()
                .flatMap(timeline -> {
                    timeline.setOverdue(true);
                    return timelineRepository.save(timeline);
                })
                .count()
                .map(Long::intValue)
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Marked {} timelines as overdue", count);
                    }
                });
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private Mono<ApprovalTimelineConnection> buildConnection(
            Flux<ApprovalTimeline> timelines, Mono<Long> count, int first, String afterCursor) {

        return Mono.zip(timelines.collectList(), count)
                .map(tuple -> {
                    List<ApprovalTimeline> items = tuple.getT1();
                    long totalCount = tuple.getT2();

                    boolean hasNextPage = items.size() > first;
                    if (hasNextPage) {
                        items = items.subList(0, first);
                    }

                    List<ApprovalTimelineEdge> edges = items.stream()
                            .map(ApprovalTimelineEdge::of)
                            .collect(Collectors.toList());

                    String startCursor = edges.isEmpty() ? null : edges.get(0).getCursor();
                    String endCursor = edges.isEmpty() ? null : edges.get(edges.size() - 1).getCursor();

                    return ApprovalTimelineConnection.builder()
                            .edges(edges)
                            .pageInfo(PageInfo.builder()
                                    .hasNextPage(hasNextPage)
                                    .hasPreviousPage(afterCursor != null)
                                    .startCursor(startCursor)
                                    .endCursor(endCursor)
                                    .build())
                            .totalCount((int) totalCount)
                            .build();
                });
    }
}
