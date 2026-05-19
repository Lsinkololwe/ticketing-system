package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.dto.*;
import com.pml.catalog.repository.ApprovalEscalationRepository;
import com.pml.catalog.service.ApprovalEscalationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of ApprovalEscalationService.
 * Provides escalation management operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEscalationServiceImpl implements ApprovalEscalationService {

    private final ApprovalEscalationRepository escalationRepository;

    // ==========================================
    // Single Escalation Operations
    // ==========================================

    @Override
    public Mono<ApprovalEscalation> findById(String id) {
        return escalationRepository.findById(id);
    }

    @Override
    public Mono<ApprovalEscalation> findByEventId(String eventId) {
        return escalationRepository.findByEventId(eventId);
    }

    @Override
    public Mono<ApprovalEscalation> save(ApprovalEscalation escalation) {
        return escalationRepository.save(escalation);
    }

    // ==========================================
    // Escalation Management Operations
    // ==========================================

    @Override
    @Transactional
    public Mono<ApprovalEscalation> acknowledge(String escalationId, String adminId, String adminName, String notes) {
        log.info("Acknowledging escalation: {} by admin: {}", escalationId, adminId);
        return escalationRepository.findById(escalationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Escalation not found: " + escalationId)))
                .flatMap(escalation -> {
                    escalation.acknowledge(adminId, adminName, notes);
                    return escalationRepository.save(escalation);
                })
                .doOnSuccess(e -> log.info("Escalation acknowledged: {}", escalationId));
    }

    @Override
    @Transactional
    public Mono<ApprovalEscalation> resolve(String escalationId, String adminId, String adminName, String resolutionNotes) {
        log.info("Resolving escalation: {} by admin: {}", escalationId, adminId);
        return escalationRepository.findById(escalationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Escalation not found: " + escalationId)))
                .flatMap(escalation -> {
                    escalation.resolve(adminId, adminName, resolutionNotes);
                    return escalationRepository.save(escalation);
                })
                .doOnSuccess(e -> log.info("Escalation resolved: {}", escalationId));
    }

    @Override
    @Transactional
    public Mono<ApprovalEscalation> createEscalation(String eventId, String eventTitle, String escalateTo,
                                                      String escalateToName, String reason, LocalDateTime slaDeadline,
                                                      String originalReviewerId, String originalReviewerName,
                                                      int reminderIntervalHours) {
        log.info("Creating escalation for event: {} to: {}", eventId, escalateTo);
        ApprovalEscalation escalation = ApprovalEscalation.create(
                eventId,
                eventTitle,
                escalateTo,
                escalateToName,
                reason,
                slaDeadline,
                originalReviewerId,
                originalReviewerName,
                reminderIntervalHours
        );
        return escalationRepository.save(escalation)
                .doOnSuccess(e -> log.info("Escalation created: {} for event: {}", e.getId(), eventId));
    }

    // ==========================================
    // Offset Pagination Queries
    // ==========================================

    @Override
    public Mono<ApprovalEscalationOffsetPage> findActiveOffsetPagination(OffsetPaginationInput pagination) {
        int pageNumber = pagination != null && pagination.page() != null ? pagination.page() : 0;
        int pageSize = pagination != null && pagination.size() != null ? pagination.size() : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        return escalationRepository.findActiveEscalations(pageable)
                .collectList()
                .zipWith(escalationRepository.countActive())
                .map(tuple -> ApprovalEscalationOffsetPage.of(tuple.getT1(), pageNumber, pageSize, tuple.getT2()));
    }

    @Override
    public Mono<ApprovalEscalationOffsetPage> findByAdminOffsetPagination(String adminId, OffsetPaginationInput pagination) {
        int pageNumber = pagination != null && pagination.page() != null ? pagination.page() : 0;
        int pageSize = pagination != null && pagination.size() != null ? pagination.size() : 20;
        Pageable pageable = PageRequest.of(pageNumber, pageSize);

        return escalationRepository.findActiveByEscalatedTo(adminId, pageable)
                .collectList()
                .zipWith(escalationRepository.countActiveByEscalatedTo(adminId))
                .map(tuple -> ApprovalEscalationOffsetPage.of(tuple.getT1(), pageNumber, pageSize, tuple.getT2()));
    }

    // ==========================================
    // Cursor Pagination Queries
    // ==========================================

    @Override
    public Mono<ApprovalEscalationConnection> findActiveCursorPagination(CursorPaginationInput pagination) {
        int first = pagination != null && pagination.getFirst() != null ? pagination.getFirst() : 20;
        String afterCursor = pagination != null ? pagination.getAfter() : null;
        Pageable pageable = PageRequest.of(0, first + 1);

        return (afterCursor != null
                ? escalationRepository.findActiveAfterCursor(afterCursor, pageable)
                : escalationRepository.findActiveFirstPage(pageable))
                .collectList()
                .zipWith(escalationRepository.countActive())
                .map(tuple -> buildConnection(tuple.getT1(), tuple.getT2(), first, afterCursor));
    }

    @Override
    public Mono<ApprovalEscalationConnection> findByAdminCursorPagination(String adminId, CursorPaginationInput pagination) {
        int first = pagination != null && pagination.getFirst() != null ? pagination.getFirst() : 20;
        String afterCursor = pagination != null ? pagination.getAfter() : null;
        Pageable pageable = PageRequest.of(0, first + 1);

        return (afterCursor != null
                ? escalationRepository.findForAdminAfterCursor(adminId, afterCursor, pageable)
                : escalationRepository.findForAdminFirstPage(adminId, pageable))
                .collectList()
                .zipWith(escalationRepository.countActiveByEscalatedTo(adminId))
                .map(tuple -> buildConnection(tuple.getT1(), tuple.getT2(), first, afterCursor));
    }

    // ==========================================
    // Count Operations
    // ==========================================

    @Override
    public Mono<Long> countActive() {
        return escalationRepository.countActive();
    }

    @Override
    public Mono<Long> countByAdmin(String adminId) {
        return escalationRepository.countActiveByEscalatedTo(adminId);
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private ApprovalEscalationConnection buildConnection(List<ApprovalEscalation> items, long totalCount,
                                                         int first, String afterCursor) {
        boolean hasNextPage = items.size() > first;
        if (hasNextPage) {
            items = items.subList(0, first);
        }

        List<ApprovalEscalationEdge> edges = items.stream()
                .map(ApprovalEscalationEdge::of)
                .collect(Collectors.toList());

        String startCursor = edges.isEmpty() ? null : edges.get(0).getCursor();
        String endCursor = edges.isEmpty() ? null : edges.get(edges.size() - 1).getCursor();

        return ApprovalEscalationConnection.builder()
                .edges(edges)
                .pageInfo(PageInfo.builder()
                        .hasNextPage(hasNextPage)
                        .hasPreviousPage(afterCursor != null)
                        .startCursor(startCursor)
                        .endCursor(endCursor)
                        .build())
                .totalCount((int) totalCount)
                .build();
    }
}
