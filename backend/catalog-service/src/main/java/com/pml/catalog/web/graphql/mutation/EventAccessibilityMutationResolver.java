package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.EventAccessibilityInput;
import com.pml.catalog.web.graphql.dto.EventMutationResponse;
import com.pml.catalog.service.EventAccessibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * Event Accessibility Mutation Resolver
 *
 * GraphQL mutations for managing event accessibility information.
 *
 * Business Intent: Enable organizers to provide comprehensive accessibility
 * details to help users with disabilities.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventAccessibilityMutationResolver {

    private final EventAccessibilityService accessibilityService;

    /**
     * Update accessibility information for an event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<EventMutationResponse> updateEventAccessibility(
            @InputArgument String eventId,
            @InputArgument EventAccessibilityInput input
    ) {
        log.info("Updating accessibility for event {}", eventId);
        return accessibilityService.updateAccessibility(eventId, input)
                .map(event -> EventMutationResponse.success(event, "Event accessibility updated successfully"))
                .onErrorResume(e -> {
                    log.error("Update event accessibility failed: {}", e.getMessage());
                    return Mono.just(EventMutationResponse.error(e.getMessage()));
                });
    }
}
