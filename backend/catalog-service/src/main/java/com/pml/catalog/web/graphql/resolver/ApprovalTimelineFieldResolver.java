package com.pml.catalog.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.catalog.domain.model.ApprovalEscalation;
import com.pml.catalog.domain.model.ApprovalTimeline;
import com.pml.catalog.service.ApprovalEscalationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Field Resolver for ApprovalTimeline type.
 *
 * Resolves:
 * - escalation: Fetches the active escalation for this timeline
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ApprovalTimelineFieldResolver {

    private final ApprovalEscalationService escalationService;

    /**
     * Resolve ApprovalTimeline.escalation - fetch active escalation for event
     */
    @DgsData(parentType = "ApprovalTimeline", field = "escalation")
    public CompletableFuture<ApprovalEscalation> escalation(DgsDataFetchingEnvironment dfe) {
        ApprovalTimeline timeline = dfe.getSource();
        if (!timeline.isHasActiveEscalation() || timeline.getEscalationId() == null) {
            return CompletableFuture.completedFuture(null);
        }
        log.debug("Resolving escalation {} for timeline {}", timeline.getEscalationId(), timeline.getId());
        return escalationService.findById(timeline.getEscalationId()).toFuture();
    }
}
