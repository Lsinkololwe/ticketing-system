package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.domain.model.PlatformConfiguration;
import com.pml.catalog.dto.*;
import com.pml.catalog.service.ApprovalEscalationService;
import com.pml.catalog.service.ApprovalTimelineService;
import com.pml.catalog.service.ApprovalWorkflowService;
import com.pml.catalog.service.PlatformConfigurationService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;

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
    public Mono<ApprovalTimelineOffsetPage> approvalTimelinesOffsetPagination(
            @InputArgument ApprovalTimelineFilterInput filter,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: approvalTimelinesOffsetPagination");
        return timelineService.findTimelinesOffsetPagination(filter, pagination);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineOffsetPage> approvalTimelinesByOrganizerOffsetPagination(
            @InputArgument String organizerId,
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: approvalTimelinesByOrganizerOffsetPagination(organizerId={})", organizerId);
        return timelineService.findByOrganizerOffsetPagination(organizerId, pagination);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineOffsetPage> pendingApprovalTimelinesOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: pendingApprovalTimelinesOffsetPagination");
        return timelineService.findPendingOffsetPagination(pagination);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineOffsetPage> overdueApprovalTimelinesOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: overdueApprovalTimelinesOffsetPagination");
        return timelineService.findOverdueOffsetPagination(pagination);
    }

    // ==========================================
    // Cursor Pagination Queries
    // ==========================================

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineConnection> approvalTimelinesCursorPagination(
            @InputArgument ApprovalTimelineFilterInput filter,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: approvalTimelinesCursorPagination");
        return timelineService.findTimelinesCursorPagination(filter, pagination);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineConnection> approvalTimelinesByOrganizerCursorPagination(
            @InputArgument String organizerId,
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: approvalTimelinesByOrganizerCursorPagination(organizerId={})", organizerId);
        return timelineService.findByOrganizerCursorPagination(organizerId, pagination);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineConnection> pendingApprovalTimelinesCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: pendingApprovalTimelinesCursorPagination");
        return timelineService.findPendingCursorPagination(pagination);
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalTimelineConnection> overdueApprovalTimelinesCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: overdueApprovalTimelinesCursorPagination");
        return timelineService.findOverdueCursorPagination(pagination);
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
    public Mono<ApprovalEscalationOffsetPage> activeEscalationsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        log.debug("GraphQL query: activeEscalationsOffsetPagination");
        return escalationService.findActiveOffsetPagination(pagination);
    }

    /**
     * Get escalations assigned to the current admin (offset pagination).
     * adminId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalEscalationOffsetPage> myEscalationsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(adminId -> log.debug("GraphQL query: myEscalationsOffsetPagination(adminId={})", adminId))
                .flatMap(adminId -> escalationService.findByAdminOffsetPagination(adminId, pagination));
    }

    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalEscalationConnection> activeEscalationsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        log.debug("GraphQL query: activeEscalationsCursorPagination");
        return escalationService.findActiveCursorPagination(pagination);
    }

    /**
     * Get escalations assigned to the current admin (cursor pagination).
     * adminId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApprovalEscalationConnection> myEscalationsCursorPagination(
            @InputArgument CursorPaginationInput pagination) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(adminId -> log.debug("GraphQL query: myEscalationsCursorPagination(adminId={})", adminId))
                .flatMap(adminId -> escalationService.findByAdminCursorPagination(adminId, pagination));
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
