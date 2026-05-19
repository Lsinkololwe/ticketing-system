package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.CategoryMutationResponse;
import com.pml.catalog.web.graphql.dto.CreateEventCategoryInput;
import com.pml.catalog.web.graphql.dto.CreateEventCategoryMutationResponse;
import com.pml.catalog.web.graphql.dto.DeleteEventCategoryMutationResponse;
import com.pml.catalog.web.graphql.dto.UpdateEventCategoryInput;
import com.pml.catalog.web.graphql.dto.UpdateEventCategoryMutationResponse;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.service.EventCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GraphQL Mutation Resolver for EventCategory Operations
 *
 * Business Intent: Handles all event category management mutations including
 * creation, updates, and deletion. Categories are used to organize and
 * filter events. All mutations are secured with admin role.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventCategoryMutationResolver {

    private final EventCategoryService eventCategoryService;

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<CreateEventCategoryMutationResponse> createEventCategory(
            @InputArgument CreateEventCategoryInput input
    ) {
        log.info("Creating event category: {}", input.name());

        EventCategory category = mapInputToCategory(input);

        return eventCategoryService.createCategory(category)
                .map(created -> new CreateEventCategoryMutationResponse(
                        true,
                        "Event category created successfully",
                        created,
                        List.of(),
                        null
                ))
                .onErrorResume(e -> {
                    log.error("Create event category failed: {}", e.getMessage());
                    return Mono.just(new CreateEventCategoryMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UpdateEventCategoryMutationResponse> updateEventCategory(
            @InputArgument String id,
            @InputArgument UpdateEventCategoryInput input
    ) {
        log.info("Updating event category: {}", id);

        return eventCategoryService.findById(id)
                .flatMap(existing -> {
                    updateCategoryFromInput(existing, input);
                    return eventCategoryService.updateCategory(id, existing);
                })
                .map(updated -> new UpdateEventCategoryMutationResponse(
                        true,
                        "Event category updated successfully",
                        updated,
                        List.of(),
                        null
                ))
                .onErrorResume(e -> {
                    log.error("Update event category failed: {}", e.getMessage());
                    return Mono.just(new UpdateEventCategoryMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<DeleteEventCategoryMutationResponse> deleteEventCategory(
            @InputArgument String id
    ) {
        log.info("Deleting event category: {}", id);

        return eventCategoryService.deleteCategory(id)
                .then(Mono.just(new DeleteEventCategoryMutationResponse(
                        true,
                        "Event category deleted successfully",
                        true,
                        List.of(),
                        null
                )))
                .onErrorResume(e -> {
                    log.error("Delete event category failed: {}", e.getMessage());
                    return Mono.just(new DeleteEventCategoryMutationResponse(
                            false,
                            e.getMessage(),
                            false,
                            List.of(e.getMessage()),
                            null
                    ));
                });
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
        category.setUpdatedAt(LocalDateTime.now());
    }
}
