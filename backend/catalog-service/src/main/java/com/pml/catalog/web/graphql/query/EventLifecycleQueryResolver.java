package com.pml.catalog.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.pml.shared.security.RequiresTenantPermission;
import com.pml.shared.security.Permission;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.EventLifecycleDto;
import com.pml.catalog.service.EventLifecycleService;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * GraphQL Query Resolver for Event Lifecycle queries.
 * Available to organizers (for their events) and admins (for all events).
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventLifecycleQueryResolver {

    private final EventLifecycleService lifecycleService;

    /**
     * Get the complete lifecycle history for an event.
     * Includes status transitions, timestamps, and audit information.
     *
     * @param eventId the event ID
     * @return the event lifecycle with all transitions
     */
    @DgsQuery
    @RequiresTenantPermission(Permission.EVENT_VIEW)
    public Mono<EventLifecycleDto> eventLifecycle(@InputArgument String eventId) {
        log.debug("GraphQL query: eventLifecycle(eventId={})", eventId);
        return lifecycleService.getEventLifecycle(eventId);
    }

    /**
     * Get the allowed status transitions for an event.
     * Used by workflow UI to show valid actions.
     *
     * @param eventId the event ID
     * @return list of valid next statuses
     */
    @DgsQuery
    @RequiresTenantPermission(Permission.EVENT_VIEW)
    public Mono<List<EventStatus>> allowedStatusTransitions(@InputArgument String eventId) {
        log.debug("GraphQL query: allowedStatusTransitions(eventId={})", eventId);
        return lifecycleService.getAllowedStatusTransitions(eventId);
    }
}
