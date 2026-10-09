package com.pml.catalog.service;


import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.web.graphql.dto.*;
import reactor.core.publisher.Mono;

/**
 * Service interface for Approval Escalation operations.
 * All escalation queries and mutations should go through this interface.
 */
public interface ApprovalEscalationService {

    // ==========================================
    // Single Escalation Operations
    // ==========================================

    /**
     * Find an escalation by ID.
     */
    Mono<ApprovalEscalation> findById(String id);

    /**
     * Find an escalation by event ID.
     */
    Mono<ApprovalEscalation> findByEventId(String eventId);

    /**
     * Save an escalation.
     */
    Mono<ApprovalEscalation> save(ApprovalEscalation escalation);

    // ==========================================
    // Escalation Management Operations
    // ==========================================

    /**
     * Acknowledge an escalation.
     *
     * @param escalationId the escalation ID
     * @param adminId the admin acknowledging
     * @param adminName the admin's display name
     * @param notes optional notes
     * @return the updated escalation
     */
    Mono<ApprovalEscalation> acknowledge(String escalationId, String adminId, String adminName, String notes);

    /**
     * Resolve an escalation.
     *
     * @param escalationId the escalation ID
     * @param adminId the admin resolving
     * @param adminName the admin's display name
     * @param resolutionNotes the resolution notes
     * @return the updated escalation
     */
    Mono<ApprovalEscalation> resolve(String escalationId, String adminId, String adminName, String resolutionNotes);

    /**
     * Create a manual escalation.
     *
     * @param eventId the event ID
     * @param eventTitle the event title
     * @param escalateTo the user ID to escalate to
     * @param escalateToName the name of the escalation recipient
     * @param reason the reason for escalation
     * @param slaDeadline the SLA deadline
     * @param originalReviewerId the original reviewer ID (may be null)
     * @param originalReviewerName the original reviewer name (may be null)
     * @param reminderIntervalHours the reminder interval in hours
     * @return the created escalation
     */
    Mono<ApprovalEscalation> createEscalation(String eventId, String eventTitle, String escalateTo,
                                               String escalateToName, String reason, java.time.Instant slaDeadline,
                                               String originalReviewerId, String originalReviewerName,
                                               int reminderIntervalHours);

    // ==========================================
    // Offset Pagination Queries
    // ==========================================

    /**
     * Find active escalations with offset pagination.
     */
    Mono<ApprovalEscalationOffsetPage> findActiveOffsetPagination(OffsetPaginationInput pagination);

    /**
     * Find escalations for a specific admin with offset pagination.
     */
    Mono<ApprovalEscalationOffsetPage> findByAdminOffsetPagination(String adminId, OffsetPaginationInput pagination);

    // ==========================================
    // Cursor Pagination Queries
    // ==========================================

    // ==========================================
    // Count Operations
    // ==========================================

    /**
     * Count all active escalations.
     */
    Mono<Long> countActive();
}
