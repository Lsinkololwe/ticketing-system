package com.pml.catalog.web.graphql.mutation;

import com.pml.shared.security.revocation.FailClosedOnRevocation;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.web.graphql.dto.AssignReviewerInput;
import com.pml.catalog.web.graphql.dto.ResolveEscalationInput;
import com.pml.catalog.service.ApprovalWorkflowService;
import com.pml.catalog.service.PlatformConfigurationService;
import com.pml.catalog.web.graphql.dto.*;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.workflow.approval.EventApprovalProcess;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

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
@Validated
@RequiredArgsConstructor
public class ApprovalWorkflowMutationResolver {

    private final PlatformConfigurationService configurationService;
    private final ApprovalWorkflowService workflowService;

    /** Claims and change requests go through the event's review workflow. */
    private final EventApprovalProcess approvals;

    // ==========================================
    // Platform Configuration Mutations
    // ==========================================

    @FailClosedOnRevocation
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<com.pml.catalog.domain.model.PlatformConfiguration> updatePlatformConfiguration(
            @Valid @InputArgument UpdatePlatformConfigurationInput input) {
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
                            // The runtime rules: validated as a whole, then applied (ET-ADM-002-R1).
                            com.pml.catalog.service.PlatformRulesUpdater.apply(existing, input);

                            return configurationService.updateConfiguration(existing, adminId);
                        }))
                // The schema returns PlatformConfiguration!, so a refusal propagates as a GraphQL error
                // with its code rather than being folded into a response object the schema never declared.
                .doOnError(e -> log.warn("Platform configuration update refused: {}", e.getMessage()));
    }

    // ==========================================
    // Event Review Mutations
    // ==========================================

    /**
     * Request changes. The SLA clock stops until the organizer resubmits.
     * reviewerId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Event> requestEventChanges(
            @InputArgument String eventId,
            @InputArgument String comments) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewerId -> log.info("Mutation: requestEventChanges(eventId={}, reviewerId={})", eventId, reviewerId))
                .flatMap(reviewerId -> approvals.requestChanges(eventId, reviewerId, comments));
    }

    // ==========================================
    // Reviewer Claim Mutations
    // ==========================================

    /**
     * Claim the event for {@code input.reviewerId} for the claim lease. A claim held
     * by somebody else is refused with its holder and expiry; the holder re-claiming extends it.
     * The assigner's id is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimeline> assignEventReviewer(
            @Valid @InputArgument AssignReviewerInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(assignerId -> log.info("Mutation: assignEventReviewer(eventId={}, reviewerId={}, assignedBy={})",
                        input.getEventId(), input.getReviewerId(), assignerId))
                .flatMap(assignerId -> approvals.claim(input.getEventId(), input.getReviewerId(), assignerId));
    }

    /** Release the claim held on the event; the item keeps its queue position. */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimeline> unassignEventReviewer(
            @InputArgument String eventId,
            @InputArgument String reason) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(actorId -> log.info("Mutation: unassignEventReviewer(eventId={}, actor={}, reason={})",
                        eventId, actorId, reason))
                .flatMap(actorId -> approvals.release(eventId, actorId));
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
            @Valid @InputArgument ResolveEscalationInput input) {
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
