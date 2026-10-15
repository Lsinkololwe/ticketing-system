package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.pml.shared.security.RequiresTenantPermission;
import com.pml.shared.security.Permission;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.EventAccessibilityInput;
import com.pml.catalog.web.graphql.dto.EventMutationResponse;
import com.pml.catalog.service.EventAccessibilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

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
@Validated
@RequiredArgsConstructor
public class EventAccessibilityMutationResolver {

    private final EventAccessibilityService accessibilityService;

    /**
     * Update accessibility information for an event
     */
    @DgsMutation
    @RequiresTenantPermission(Permission.EVENT_EDIT)
    public Mono<EventMutationResponse> updateEventAccessibility(
            @InputArgument String eventId,
            @Valid @InputArgument EventAccessibilityInput input
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
