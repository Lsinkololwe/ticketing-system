package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.CategoryMutationResponse;
import com.pml.catalog.web.graphql.dto.CreateEventCategoryInput;
import com.pml.catalog.web.graphql.dto.UpdateEventCategoryInput;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.service.EventCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

/**
 * GraphQL Mutation Resolver for EventCategory Operations
 *
 * Business Intent: Handles all event category management mutations including
 * creation, updates, and deletion. Categories are used to organize and
 * filter events. All mutations are secured with admin role.
 */
@Slf4j

@DgsComponent
@Validated
@RequiredArgsConstructor
public class EventCategoryMutationResolver {

    private final EventCategoryService eventCategoryService;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventCategory> createEventCategory(
            @Valid @InputArgument CreateEventCategoryInput input
    ) {
        log.info("Creating event category: {}", input.name());

        EventCategory category = mapInputToCategory(input);

        return eventCategoryService.createCategory(category);
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventCategory> updateEventCategory(
            @InputArgument String id,
            @Valid @InputArgument UpdateEventCategoryInput input
    ) {
        log.info("Updating event category: {}", id);

        return eventCategoryService.findById(id)
                .flatMap(existing -> {
                    updateCategoryFromInput(existing, input);
                    return eventCategoryService.updateCategory(id, existing);
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<String> deleteEventCategory(
            @InputArgument String id
    ) {
        log.info("Deleting event category: {}", id);

        return eventCategoryService.deleteCategory(id)
                .thenReturn(id);
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<CategoryMutationResponse> activateEventCategory(
            @InputArgument String id
    ) {
        log.info("Activating event category: {}", id);

        return eventCategoryService.activateCategory(id)
                .map(activated -> CategoryMutationResponse.success(
                        activated,
                        "Event category activated successfully"
                ))
                .onErrorResume(e -> {
                    log.error("Activate event category failed: {}", e.getMessage());
                    return Mono.just(CategoryMutationResponse.error(e.getMessage()));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<CategoryMutationResponse> deactivateEventCategory(
            @InputArgument String id
    ) {
        log.info("Deactivating event category: {}", id);

        return eventCategoryService.deactivateCategory(id)
                .map(deactivated -> CategoryMutationResponse.success(
                        deactivated,
                        "Event category deactivated successfully"
                ))
                .onErrorResume(e -> {
                    log.error("Deactivate event category failed: {}", e.getMessage());
                    return Mono.just(CategoryMutationResponse.error(e.getMessage()));
                });
    }

    private EventCategory mapInputToCategory(CreateEventCategoryInput input) {
        return EventCategory.builder()
                .name(input.name())
                .description(input.description())
                .isActive(true)
                .displayOrder(0)
                .build();
    }

    private void updateCategoryFromInput(EventCategory category, UpdateEventCategoryInput input) {
        if (input.name() != null) category.setName(input.name());
        if (input.description() != null) category.setDescription(input.description());
        if (input.isActive() != null) category.setActive(input.isActive());
        category.setUpdatedAt(clock.instant());
    }
}
