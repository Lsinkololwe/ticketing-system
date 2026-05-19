package com.pml.catalog.service;

import com.pml.catalog.web.graphql.dto.EventAccessibilityInput;
import com.pml.catalog.domain.model.Event;
import reactor.core.publisher.Mono;

/**
 * Event Accessibility Service
 *
 * Service for managing event accessibility information.
 *
 * Business Intent: Help users with disabilities make informed decisions
 * about event attendance by providing comprehensive accessibility details.
 */
public interface EventAccessibilityService {

    /**
     * Update accessibility information for an event
     *
     * @param eventId Event ID
     * @param input Accessibility input
     * @return Updated event
     */
    Mono<Event> updateAccessibility(String eventId, EventAccessibilityInput input);
}
