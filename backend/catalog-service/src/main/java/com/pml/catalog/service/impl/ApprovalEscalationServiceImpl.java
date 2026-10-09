package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.web.graphql.dto.*;
import com.pml.catalog.repository.ApprovalEscalationRepository;
import com.pml.catalog.service.ApprovalEscalationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.Instant;
/**
 * Implementation of ApprovalEscalationService.
 * Provides escalation management operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalEscalationServiceImpl implements ApprovalEscalationService {

    private final ApprovalEscalationRepository escalationRepository;

    /**
     * Acknowledgement, resolution and reminder times are stamped from here.
     *
     * <p>Reading the wall clock inside the domain model would put every SLA boundary out of reach
     * of a test: whether an escalation created one hour past its deadline records one hour
     * overdue, and whether a reminder is due the minute before its interval elapses, are both
     * assertions that need a controllable clock.</p>
     */
    private final java.time.Clock clock;

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
                    escalation.acknowledge(adminId, adminName, notes, clock.instant());
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
                    escalation.resolve(adminId, adminName, resolutionNotes, clock.instant());
                    return escalationRepository.save(escalation);
                })
                .doOnSuccess(e -> log.info("Escalation resolved: {}", escalationId));
    }

    @Override
    @Transactional
    public Mono<ApprovalEscalation> createEscalation(String eventId, String eventTitle, String escalateTo,
                                                      String escalateToName, String reason, Instant slaDeadline,
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
                reminderIntervalHours,
                clock.instant()
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
    // Count Operations
    // ==========================================

    @Override
    public Mono<Long> countActive() {
        return escalationRepository.countActive();
    }
}
