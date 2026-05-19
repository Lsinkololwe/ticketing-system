package com.pml.catalog.service;

import com.pml.catalog.dto.EventLifecycleDto;
import com.pml.shared.constants.EventStatus;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service for managing event lifecycle and status transitions.
 * Provides audit trail and state machine logic for event workflow.
 */
public interface EventLifecycleService {

    /**
     * Get the complete lifecycle history for an event.
     *
     * @param eventId the event ID
     * @return the event lifecycle with all transitions
     */
    Mono<EventLifecycleDto> getEventLifecycle(String eventId);

    /**
     * Get the allowed status transitions for an event based on its current status.
     * Implements the state machine rules for event workflow.
     *
     * @param eventId the event ID
     * @return list of valid next statuses
     */
    Mono<List<EventStatus>> getAllowedStatusTransitions(String eventId);

    /**
     * Get allowed transitions based on a given status (no event lookup needed).
     * Used for displaying UI options based on status alone.
     *
     * @param currentStatus the current event status
     * @return list of valid next statuses
     */
    List<EventStatus> getAllowedTransitionsForStatus(EventStatus currentStatus);
}
