package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.domain.model.PlatformConfiguration;
import com.pml.catalog.dto.AssignReviewerInput;
import com.pml.catalog.dto.ResolveEscalationInput;
import com.pml.catalog.service.ApprovalEscalationService;
import com.pml.catalog.service.ApprovalTimelineService;
import com.pml.catalog.service.ApprovalWorkflowService;
import com.pml.catalog.service.PlatformConfigurationService;
import com.pml.catalog.web.graphql.dto.*;
import com.pml.catalog.web.graphql.dto.EventMutationResponse;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * GraphQL Mutation Resolver for Approval Workflow mutations.
 * All mutations are admin-only unless otherwise noted.
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: All actor IDs (adminId, reviewerId)
 *       are extracted from JWT, never from client input</li>
 * </ul>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ApprovalWorkflowMutationResolver {

    private final PlatformConfigurationService configurationService;
    private final ApprovalTimelineService timelineService;
    private final ApprovalEscalationService escalationService;
    private final ApprovalWorkflowService workflowService;

    // ==========================================
    // Platform Configuration Mutations
    // ==========================================

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PlatformConfigurationMutationResponse> updatePlatformConfiguration(
            @InputArgument UpdatePlatformConfigurationInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(adminId -> log.info("Mutation: updatePlatformConfiguration by admin: {}", adminId))
                .flatMap(adminId -> configurationService.getConfiguration()
                        .flatMap(existing -> {
                            // Apply updates from input
                            if (input.getApprovalSlaHours() != null) {
                                existing.setApprovalSlaHours(input.getApprovalSlaHours());
                            }
                            if (input.getApprovalWarningThresholdHours() != null) {
                                existing.setApprovalWarningThresholdHours(input.getApprovalWarningThresholdHours());
                            }
                            if (input.getAutoEscalationEnabled() != null) {
                                existing.setAutoEscalationEnabled(input.getAutoEscalationEnabled());
                            }
                            if (input.getEscalationDelayHours() != null) {
                                existing.setEscalationDelayHours(input.getEscalationDelayHours());
                            }
                            if (input.getEscalationRecipientRole() != null) {
                                existing.setEscalationRecipientRole(input.getEscalationRecipientRole());
                            }
                            if (input.getEscalationReminderIntervalHours() != null) {
                                existing.setEscalationReminderIntervalHours(input.getEscalationReminderIntervalHours());
                            }
                            if (input.getMaxEscalationReminders() != null) {
                                existing.setMaxEscalationReminders(input.getMaxEscalationReminders());
                            }
                            if (input.getOrganizerNotificationChannel() != null) {
                                existing.setOrganizerNotificationChannel(input.getOrganizerNotificationChannel());
                            }
                            if (input.getAdminNotificationChannel() != null) {
                                existing.setAdminNotificationChannel(input.getAdminNotificationChannel());
                            }
                            if (input.getSendSlaWarningNotifications() != null) {
                                existing.setSendSlaWarningNotifications(input.getSendSlaWarningNotifications());
                            }
                            if (input.getSendEscalationNotifications() != null) {
                                existing.setSendEscalationNotifications(input.getSendEscalationNotifications());
                            }
                            if (input.getRequireCommentsOnRejection() != null) {
                                existing.setRequireCommentsOnRejection(input.getRequireCommentsOnRejection());
                            }
                            if (input.getRequireCommentsOnChangesRequested() != null) {
                                existing.setRequireCommentsOnChangesRequested(input.getRequireCommentsOnChangesRequested());
                            }
                            if (input.getAllowSelfApproval() != null) {
                                existing.setAllowSelfApproval(input.getAllowSelfApproval());
                            }

                            return configurationService.updateConfiguration(existing, adminId);
                        }))
                .map(config -> PlatformConfigurationMutationResponse.success(config, "Platform configuration updated successfully"))
                .onErrorResume(e -> {
                    log.error("Error updating platform configuration", e);
                    return Mono.just(PlatformConfigurationMutationResponse.error(e.getMessage()));
                });
    }

    // ==========================================
    // Event Review Mutations
    // ==========================================

    /**
     * Request changes to an event during the approval process.
     * Moves the event to CHANGES_REQUESTED status.
     * reviewerId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventMutationResponse> requestEventChanges(
            @InputArgument String eventId,
            @InputArgument String comments) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewerId -> log.info("Mutation: requestEventChanges(eventId={}, reviewerId={})", eventId, reviewerId))
                .flatMap(reviewerId -> workflowService.requestChanges(eventId, reviewerId, "Reviewer", comments)
                        .map(timeline -> EventMutationResponse.success(null, "Changes requested successfully")))
                .onErrorResume(e -> {
                    log.error("Error requesting changes for event {}", eventId, e);
                    return Mono.just(EventMutationResponse.error(e.getMessage()));
                });
    }

    // ==========================================
    // Reviewer Assignment Mutations
    // ==========================================

    /**
     * Assign reviewer to event.
     * Note: input.reviewerId is the target reviewer (who will review), not the assigner.
     * Assigner ID is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineMutationResponse> assignEventReviewer(
            @InputArgument AssignReviewerInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(assignerId -> log.info("Mutation: assignEventReviewer(eventId={}, reviewerId={}, assignedBy={})",
                        input.getEventId(), input.getReviewerId(), assignerId))
                .flatMap(assignerId -> workflowService.assignReviewer(
                                input.getEventId(),
                                input.getReviewerId(),
                                input.getReviewerName(),
                                assignerId,
                                input.getInternalNotes())
                        .map(timeline -> ApprovalTimelineMutationResponse.success(timeline, "Reviewer assigned successfully")))
                .onErrorResume(e -> {
                    log.error("Error assigning reviewer", e);
                    return Mono.just(ApprovalTimelineMutationResponse.error(e.getMessage()));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineMutationResponse> unassignEventReviewer(
            @InputArgument String eventId,
            @InputArgument String reason) {
        log.info("Mutation: unassignEventReviewer(eventId={}, reason={})", eventId, reason);

        return workflowService.unassignReviewer(eventId, reason)
                .map(timeline -> ApprovalTimelineMutationResponse.success(timeline, "Reviewer unassigned successfully"))
                .onErrorResume(e -> {
                    log.error("Error unassigning reviewer", e);
                    return Mono.just(ApprovalTimelineMutationResponse.error(e.getMessage()));
                });
    }

    // ==========================================
    // Approval Comment Mutation
    // ==========================================

    /**
     * Add approval comment.
     * adminId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineMutationResponse> addApprovalComment(
            @InputArgument String eventId,
            @InputArgument String comment,
            @InputArgument Boolean isInternal) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(adminId -> log.info("Mutation: addApprovalComment(eventId={}, adminId={}, isInternal={})", eventId, adminId, isInternal))
                .flatMap(adminId -> workflowService.addComment(eventId, adminId, "Admin", comment, isInternal != null && isInternal)
                        .map(timeline -> ApprovalTimelineMutationResponse.success(timeline, "Comment added successfully")))
                .onErrorResume(e -> {
                    log.error("Error adding comment", e);
                    return Mono.just(ApprovalTimelineMutationResponse.error(e.getMessage()));
                });
    }

    // ==========================================
    // Escalation Mutations
    // ==========================================

    /**
     * Acknowledge escalation.
     * adminId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalEscalationMutationResponse> acknowledgeEscalation(
            @InputArgument String escalationId,
            @InputArgument String notes) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(adminId -> log.info("Mutation: acknowledgeEscalation(escalationId={}, adminId={})", escalationId, adminId))
                .flatMap(adminId -> workflowService.acknowledgeEscalation(escalationId, adminId, "Admin", notes)
                        .map(escalation -> ApprovalEscalationMutationResponse.success(escalation, "Escalation acknowledged")))
                .onErrorResume(e -> {
                    log.error("Error acknowledging escalation", e);
                    return Mono.just(ApprovalEscalationMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Resolve escalation.
     * adminId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalEscalationMutationResponse> resolveEscalation(
            @InputArgument ResolveEscalationInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(adminId -> log.info("Mutation: resolveEscalation(escalationId={}, adminId={}, action={})",
                        input.getEscalationId(), adminId, input.getAction()))
                .flatMap(adminId -> workflowService.resolveEscalation(input.getEscalationId(), adminId, "Admin", input.getResolutionNotes())
                        .map(escalation -> ApprovalEscalationMutationResponse.success(escalation, "Escalation resolved")))
                .onErrorResume(e -> {
                    log.error("Error resolving escalation", e);
                    return Mono.just(ApprovalEscalationMutationResponse.error(e.getMessage()));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalEscalationMutationResponse> triggerManualEscalation(
            @InputArgument String eventId,
            @InputArgument String reason,
            @InputArgument String escalateTo) {
        log.info("Mutation: triggerManualEscalation(eventId={}, escalateTo={})", eventId, escalateTo);

        // TODO: Get escalateTo name from user service
        return workflowService.triggerManualEscalation(eventId, reason, escalateTo, "Senior Admin")
                .map(escalation -> ApprovalEscalationMutationResponse.success(escalation, "Escalation triggered manually"))
                .onErrorResume(e -> {
                    log.error("Error triggering manual escalation", e);
                    return Mono.just(ApprovalEscalationMutationResponse.error(e.getMessage()));
                });
    }
}
