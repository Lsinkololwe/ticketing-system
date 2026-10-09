package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.domain.model.PlatformConfiguration;
import com.pml.catalog.web.graphql.dto.*;
import com.pml.catalog.service.ApprovalEscalationService;
import com.pml.catalog.service.ApprovalTimelineService;
import com.pml.catalog.service.ApprovalWorkflowService;
import com.pml.catalog.service.PlatformConfigurationService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * GraphQL Query Resolver for Approval Workflow queries.
 * All queries are admin-only.
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: "my*" queries extract adminId from JWT,
 *       never from client input</li>
 * </ul>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ApprovalWorkflowQueryResolver {

    private final PlatformConfigurationService configurationService;
    private final ApprovalTimelineService timelineService;
    private final ApprovalEscalationService escalationService;
    private final ApprovalWorkflowService workflowService;

    // ==========================================
    // Platform Configuration Query
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<PlatformConfiguration> platformConfiguration() {
        log.debug("GraphQL query: platformConfiguration");
        return configurationService.getConfiguration();
    }

    // ==========================================
    // Approval Timeline Queries
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimeline> approvalTimeline(@InputArgument String eventId) {
        log.debug("GraphQL query: approvalTimeline(eventId={})", eventId);
        return timelineService.findByEventId(eventId);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineOffsetPage> approvalTimelines(
            @InputArgument ApprovalTimelineFilterInput filter,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: approvalTimelines");
        return timelineService.findTimelinesOffsetPagination(filter, pagination);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineOffsetPage> approvalTimelinesByOrganizer(
            @InputArgument String organizerId,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: approvalTimelinesByOrganizer(organizerId={})", organizerId);
        return timelineService.findByOrganizerOffsetPagination(organizerId, pagination);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineOffsetPage> pendingApprovalTimelines(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: pendingApprovalTimelines");
        return timelineService.findPendingOffsetPagination(pagination);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineOffsetPage> overdueApprovalTimelines(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: overdueApprovalTimelines");
        return timelineService.findOverdueOffsetPagination(pagination);
    }
    // ==========================================
    // Escalation Queries
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalEscalation> approvalEscalation(@InputArgument String id) {
        log.debug("GraphQL query: approvalEscalation(id={})", id);
        return escalationService.findById(id);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalEscalationOffsetPage> activeEscalations(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: activeEscalations");
        return escalationService.findActiveOffsetPagination(pagination);
    }

    /**
     * Get escalations assigned to the current admin (offset pagination).
     * adminId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalEscalationOffsetPage> myEscalations(
            @InputArgument OffsetPaginationInput pagination) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(adminId -> log.debug("GraphQL query: myEscalations(adminId={})", adminId))
                .flatMap(adminId -> escalationService.findByAdminOffsetPagination(adminId, pagination));
    }
    // ==========================================
    // Statistics Query
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalStats> approvalStats() {
        log.debug("GraphQL query: approvalStats");
        return workflowService.getApprovalStats();
    }
}
