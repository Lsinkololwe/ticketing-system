package com.pml.catalog.service;

import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.web.graphql.dto.ApprovalStats;
import reactor.core.publisher.Mono;

/**
 * The operator actions around a review: comments, manual escalation, acknowledging
 * and resolving escalations, and the workbench statistics.
 *
 * <p>The review itself — submission, claims, decisions, the SLA clock and automatic escalation —
 * belongs to {@code EventApprovalWorkflow}, reached through {@code EventApprovalProcess}.
 */
public interface ApprovalWorkflowService {

    /**
     * Add a comment to the timeline without changing status.
     *
     * @param eventId the event ID
     * @param adminId the admin adding the comment
     * @param adminName the admin's display name
     * @param comment the comment text
     * @param isInternal whether the comment is internal-only
     * @return the updated timeline
     */
    Mono<ApprovalTimeline> addComment(String eventId, String adminId, String adminName,
                                      String comment, boolean isInternal);

    Mono<ApprovalEscalation> acknowledgeEscalation(String escalationId, String adminId,
                                                    String adminName, String notes);

    Mono<ApprovalEscalation> resolveEscalation(String escalationId, String adminId,
                                               String adminName, String resolutionNotes);

    /**
     * An escalation an operator raises by hand, alongside the automatic levels.
     *
     * @param eventId the event ID
     * @param reason the reason for manual escalation
     * @param escalateTo the user ID to escalate to
     * @param escalateToName the name of the escalation recipient
     * @return the created escalation
     */
    Mono<ApprovalEscalation> triggerManualEscalation(String eventId, String reason,
                                                      String escalateTo, String escalateToName);

    /**
     * Approval statistics for the admin dashboard.
     */
    Mono<ApprovalStats> getApprovalStats();
}
