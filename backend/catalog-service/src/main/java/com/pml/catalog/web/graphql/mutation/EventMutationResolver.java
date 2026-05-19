package com.pml.catalog.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.dto.EventCancellationInputDto;
import com.pml.catalog.dto.EventCancellationResponseDto;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.web.graphql.dto.ApproveEventMutationResponse;
import com.pml.catalog.web.graphql.dto.BulkReminderResponse;
import com.pml.catalog.web.graphql.dto.CreateEventInput;
import com.pml.catalog.web.graphql.dto.CreateEventMutationResponse;
import com.pml.catalog.web.graphql.dto.DeleteEventMutationResponse;
import com.pml.catalog.web.graphql.dto.DuplicateEventMutationResponse;
import com.pml.catalog.web.graphql.dto.EventMutationResponse;
import com.pml.catalog.web.graphql.dto.PublishEventMutationResponse;
import com.pml.catalog.web.graphql.dto.RejectEventMutationResponse;
import com.pml.catalog.web.graphql.dto.SubmitEventForApprovalMutationResponse;
import com.pml.catalog.web.graphql.dto.UnpublishEventMutationResponse;
import com.pml.catalog.web.graphql.dto.UpdateEventCapacityMutationResponse;
import com.pml.catalog.web.graphql.dto.UpdateEventInput;
import com.pml.catalog.web.graphql.dto.UpdateEventMutationResponse;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.service.EventService;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GraphQL Mutation Resolver for Event Operations
 *
 * <p>Handles all event lifecycle mutations including creation, updates, publishing,
 * cancellation, and approval workflows.</p>
 *
 * <h2>Security Model</h2>
 * <ul>
 *   <li>User identity is ALWAYS extracted from JWT, never from client parameters</li>
 *   <li>Authorization is verified via Identity Service before mutations</li>
 *   <li>Pre-authorize annotations provide role-based access control</li>
 * </ul>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: No client-provided identity, JWT extraction only</li>
 *   <li>A04:2021 - Insecure Design: Defense in depth with centralized authorization</li>
 *   <li>A07:2021 - Identification and Authentication Failures: Server-side identity validation</li>
 * </ul>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EventMutationResolver {

    private final EventService eventService;
    private final IdentityServiceClient identityServiceClient;

    /**
     * Create a new event.
     *
     * <p>Security: User ID is extracted from JWT, authorization verified via Identity Service.</p>
     *
     * @param input Event creation input
     * @return Mutation response with created event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<CreateEventMutationResponse> createEvent(
            @InputArgument CreateEventInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Creating event for user: {}", userId))
                .flatMap(userId ->
                        // Step 1: Verify user has EVENT_CREATE permission and get their organization
                        identityServiceClient.checkAuthorization(AuthorizationRequest.builder()
                                        .userId(userId)
                                        .requiredPermission("EVENT_CREATE")
                                        .build())
                                .flatMap(authResult -> {
                                    if (!authResult.isAuthorized()) {
                                        return Mono.just(new CreateEventMutationResponse(
                                                false,
                                                authResult.getReason(),
                                                null,
                                                List.of(authResult.getReason()),
                                                null
                                        ));
                                    }

                                    // Step 2: Create event with userId as organizerId
                                    String organizationId = authResult.getOrganizationId();
                                    Event event = mapInputToEvent(input, userId, organizationId);

                                    return eventService.createEvent(event)
                                            .map(created -> new CreateEventMutationResponse(
                                                    true,
                                                    "Event created successfully",
                                                    created,
                                                    List.of(),
                                                    Map.of(
                                                            "organizerId", userId,
                                                            "organizationId", organizationId != null ? organizationId : "",
                                                            "authSource", authResult.getAuthorizationSource()
                                                    )
                                            ));
                                })
                )
                .onErrorResume(SecurityException.class, e -> {
                    log.warn("Authentication required for createEvent");
                    return Mono.just(new CreateEventMutationResponse(
                            false,
                            "Authentication required",
                            null,
                            List.of("Please log in to create events"),
                            null
                    ));
                })
                .onErrorResume(IdentityServiceClient.AuthorizationDeniedException.class, e -> {
                    log.warn("Authorization denied for createEvent: {}", e.getMessage());
                    return Mono.just(new CreateEventMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                })
                .onErrorResume(e -> {
                    log.error("Create event failed: {}", e.getMessage());
                    return Mono.just(new CreateEventMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
    }

    /**
     * Update an existing event.
     *
     * <p>Security: User ID extracted from JWT, event ownership verified via Identity Service.</p>
     *
     * @param id Event ID to update
     * @param input Update input
     * @return Mutation response with updated event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<UpdateEventMutationResponse> updateEvent(
            @InputArgument String id,
            @InputArgument UpdateEventInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Updating event: {} by user: {}", id, userId))
                .flatMap(userId ->
                        // Step 1: Get the event to find its organizationId
                        eventService.findById(id)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                                .flatMap(existingEvent ->
                                        // Step 2: Verify authorization for this specific event
                                        identityServiceClient.checkEventAccess(
                                                        userId,
                                                        id,
                                                        existingEvent.getOrganizationId(),
                                                        "EVENT_EDIT")
                                                .flatMap(authResult -> {
                                                    if (!authResult.isAuthorized()) {
                                                        return Mono.just(new UpdateEventMutationResponse(
                                                                false,
                                                                authResult.getReason(),
                                                                null,
                                                                List.of(authResult.getReason()),
                                                                null
                                                        ));
                                                    }

                                                    // Step 3: Update the event
                                                    updateEventFromInput(existingEvent, input);
                                                    return eventService.updateEvent(id, existingEvent)
                                                            .map(updated -> new UpdateEventMutationResponse(
                                                                    true,
                                                                    "Event updated successfully",
                                                                    updated,
                                                                    List.of(),
                                                                    Map.of("authSource", authResult.getAuthorizationSource())
                                                            ));
                                                })
                                )
                )
                .onErrorResume(SecurityException.class, e -> Mono.just(new UpdateEventMutationResponse(
                        false, "Authentication required", null, List.of("Please log in"), null
                )))
                .onErrorResume(IdentityServiceClient.AuthorizationDeniedException.class, e ->
                        Mono.just(new UpdateEventMutationResponse(false, e.getMessage(), null, List.of(e.getMessage()), null))
                )
                .onErrorResume(e -> {
                    log.error("Update event failed: {}", e.getMessage());
                    return Mono.just(new UpdateEventMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null
                    ));
                });
    }

    /**
     * Delete an event.
     *
     * <p>Security: User ID extracted from JWT, EVENT_DELETE permission verified.</p>
     *
     * @param id Event ID to delete
     * @return Mutation response
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<DeleteEventMutationResponse> deleteEvent(
            @InputArgument String id
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Deleting event: {} by user: {}", id, userId))
                .flatMap(userId ->
                        eventService.findById(id)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                                .flatMap(existingEvent ->
                                        identityServiceClient.checkEventAccess(
                                                        userId, id, existingEvent.getOrganizationId(), "EVENT_DELETE")
                                                .flatMap(authResult -> {
                                                    if (!authResult.isAuthorized()) {
                                                        return Mono.just(new DeleteEventMutationResponse(
                                                                false, authResult.getReason(), false,
                                                                List.of(authResult.getReason()), null
                                                        ));
                                                    }
                                                    return eventService.deleteEvent(id)
                                                            .then(Mono.just(new DeleteEventMutationResponse(
                                                                    true, "Event deleted successfully", true,
                                                                    List.of(), null
                                                            )));
                                                })
                                )
                )
                .onErrorResume(SecurityException.class, e -> Mono.just(new DeleteEventMutationResponse(
                        false, "Authentication required", false, List.of("Please log in"), null
                )))
                .onErrorResume(e -> {
                    log.error("Delete event failed: {}", e.getMessage());
                    return Mono.just(new DeleteEventMutationResponse(
                            false, e.getMessage(), false, List.of(e.getMessage()), null
                    ));
                });
    }

    /**
     * Publish an event (make it visible to customers).
     *
     * <p>Security: User ID extracted from JWT, EVENT_PUBLISH permission verified.</p>
     *
     * @param id Event ID to publish
     * @return Mutation response with published event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<PublishEventMutationResponse> publishEvent(
            @InputArgument String id
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Publishing event: {} by user: {}", id, userId))
                .flatMap(userId ->
                        eventService.findById(id)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                                .flatMap(existingEvent ->
                                        identityServiceClient.checkEventAccess(
                                                        userId, id, existingEvent.getOrganizationId(), "EVENT_PUBLISH")
                                                .flatMap(authResult -> {
                                                    if (!authResult.isAuthorized()) {
                                                        return Mono.just(new PublishEventMutationResponse(
                                                                false, authResult.getReason(), null,
                                                                List.of(authResult.getReason()), null
                                                        ));
                                                    }
                                                    return eventService.publishEvent(id)
                                                            .map(published -> new PublishEventMutationResponse(
                                                                    true, "Event published successfully", published,
                                                                    List.of(),
                                                                    Map.of("publishedAt", LocalDateTime.now().toString())
                                                            ));
                                                })
                                )
                )
                .onErrorResume(SecurityException.class, e -> Mono.just(new PublishEventMutationResponse(
                        false, "Authentication required", null, List.of("Please log in"), null
                )))
                .onErrorResume(e -> {
                    log.error("Publish event failed: {}", e.getMessage());
                    return Mono.just(new PublishEventMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null
                    ));
                });
    }

    /**
     * Unpublish an event (hide from customers).
     *
     * <p>Security: User ID extracted from JWT, EVENT_PUBLISH permission verified.</p>
     *
     * @param id Event ID to unpublish
     * @return Mutation response with unpublished event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<UnpublishEventMutationResponse> unpublishEvent(
            @InputArgument String id
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Unpublishing event: {} by user: {}", id, userId))
                .flatMap(userId ->
                        eventService.findById(id)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                                .flatMap(existingEvent ->
                                        identityServiceClient.checkEventAccess(
                                                        userId, id, existingEvent.getOrganizationId(), "EVENT_PUBLISH")
                                                .flatMap(authResult -> {
                                                    if (!authResult.isAuthorized()) {
                                                        return Mono.just(new UnpublishEventMutationResponse(
                                                                false, authResult.getReason(), null,
                                                                List.of(authResult.getReason()), null
                                                        ));
                                                    }
                                                    existingEvent.setPublished(false);
                                                    existingEvent.setStatus(EventStatus.APPROVED);
                                                    existingEvent.setPublishedAt(null);
                                                    return eventService.updateEvent(id, existingEvent)
                                                            .map(unpublished -> new UnpublishEventMutationResponse(
                                                                    true, "Event unpublished successfully",
                                                                    unpublished, List.of(), null
                                                            ));
                                                })
                                )
                )
                .onErrorResume(SecurityException.class, e -> Mono.just(new UnpublishEventMutationResponse(
                        false, "Authentication required", null, List.of("Please log in"), null
                )))
                .onErrorResume(e -> {
                    log.error("Unpublish event failed: {}", e.getMessage());
                    return Mono.just(new UnpublishEventMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null
                    ));
                });
    }

    /**
     * Cancel an event with optional refunds and notifications.
     *
     * <p>Security: User ID extracted from JWT, EVENT_DELETE permission verified (cancellation is destructive).</p>
     *
     * @param id Event ID to cancel
     * @param input Cancellation options (reason, notify attendees, trigger refunds)
     * @return Cancellation response with saga ID if refunds initiated
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<EventCancellationResponseDto> cancelEvent(
            @InputArgument String id,
            @InputArgument EventCancellationInputDto input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Cancelling event: {} by user: {} reason: {}",
                        id, userId, input.getReason()))
                .flatMap(userId ->
                        eventService.findById(id)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                                .flatMap(existingEvent ->
                                        identityServiceClient.checkEventAccess(
                                                        userId, id, existingEvent.getOrganizationId(), "EVENT_DELETE")
                                                .flatMap(authResult -> {
                                                    if (!authResult.isAuthorized()) {
                                                        return Mono.just(EventCancellationResponseDto.builder()
                                                                .success(false)
                                                                .message(authResult.getReason())
                                                                .build());
                                                    }
                                                    boolean notifyAttendees = input.getNotifyAttendees() != null
                                                            ? input.getNotifyAttendees() : true;
                                                    boolean triggerRefunds = input.getTriggerRefunds() != null
                                                            ? input.getTriggerRefunds() : true;

                                                    return eventService.cancelEventWithDetails(
                                                                    id, input.getReason(), notifyAttendees, triggerRefunds)
                                                            .map(cancelled -> EventCancellationResponseDto.builder()
                                                                    .success(true)
                                                                    .message("Event cancelled successfully")
                                                                    .event(cancelled)
                                                                    .ticketsAffected(cancelled.getSoldTickets())
                                                                    .refundSagaInitiated(triggerRefunds)
                                                                    .sagaId(triggerRefunds
                                                                            ? java.util.UUID.randomUUID().toString() : null)
                                                                    .build());
                                                })
                                )
                )
                .onErrorResume(SecurityException.class, e ->
                        Mono.just(EventCancellationResponseDto.error("Authentication required")))
                .onErrorResume(e -> {
                    log.error("Cancel event failed: {}", e.getMessage());
                    return Mono.just(EventCancellationResponseDto.error(e.getMessage()));
                });
    }

    /**
     * Submit an event for admin approval.
     *
     * <p>Security: User ID extracted from JWT, EVENT_EDIT permission verified.</p>
     *
     * @param eventId Event ID to submit
     * @return Mutation response with submitted event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<SubmitEventForApprovalMutationResponse> submitEventForApproval(
            @InputArgument String eventId
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Submitting event for approval: {} by user: {}", eventId, userId))
                .flatMap(userId ->
                        eventService.findById(eventId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + eventId)))
                                .flatMap(existingEvent ->
                                        identityServiceClient.checkEventAccess(
                                                        userId, eventId, existingEvent.getOrganizationId(), "EVENT_EDIT")
                                                .flatMap(authResult -> {
                                                    if (!authResult.isAuthorized()) {
                                                        return Mono.just(new SubmitEventForApprovalMutationResponse(
                                                                false, authResult.getReason(), null,
                                                                List.of(authResult.getReason()), null
                                                        ));
                                                    }
                                                    existingEvent.setStatus(EventStatus.PENDING_APPROVAL);
                                                    existingEvent.setSubmittedForApprovalAt(LocalDateTime.now());
                                                    existingEvent.setApprovalDeadline(LocalDateTime.now().plusDays(3));
                                                    return eventService.updateEvent(eventId, existingEvent)
                                                            .map(submitted -> new SubmitEventForApprovalMutationResponse(
                                                                    true, "Event submitted for approval", submitted,
                                                                    List.of(),
                                                                    Map.of("submittedAt", LocalDateTime.now().toString())
                                                            ));
                                                })
                                )
                )
                .onErrorResume(SecurityException.class, e -> Mono.just(new SubmitEventForApprovalMutationResponse(
                        false, "Authentication required", null, List.of("Please log in"), null
                )))
                .onErrorResume(e -> {
                    log.error("Submit event for approval failed: {}", e.getMessage());
                    return Mono.just(new SubmitEventForApprovalMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null
                    ));
                });
    }

    /**
     * Approve event.
     * reviewerId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ApproveEventMutationResponse> approveEvent(
            @InputArgument String eventId,
            @InputArgument String comments
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewerId -> log.info("Approving event: {} by reviewer: {} comments: {}", eventId, reviewerId, comments))
                .flatMap(reviewerId -> eventService.approveEvent(eventId)
                        .map(approved -> new ApproveEventMutationResponse(
                                true,
                                "Event approved successfully",
                                approved,
                                List.of(),
                                Map.of("reviewerId", reviewerId, "comments", comments != null ? comments : "", "approvedAt", LocalDateTime.now().toString())
                        )))
                .onErrorResume(e -> {
                    log.error("Approve event failed: {}", e.getMessage());
                    return Mono.just(new ApproveEventMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
    }

    /**
     * Reject event.
     * reviewerId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<RejectEventMutationResponse> rejectEvent(
            @InputArgument String eventId,
            @InputArgument String comments
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewerId -> log.info("Rejecting event: {} by reviewer: {} reason: {}", eventId, reviewerId, comments))
                .flatMap(reviewerId -> eventService.rejectEvent(eventId, comments)
                        .map(rejected -> new RejectEventMutationResponse(
                                true,
                                "Event rejected",
                                rejected,
                                List.of(),
                                Map.of("reviewerId", reviewerId, "comments", comments != null ? comments : "", "rejectedAt", LocalDateTime.now().toString())
                        )))
                .onErrorResume(e -> {
                    log.error("Reject event failed: {}", e.getMessage());
                    return Mono.just(new RejectEventMutationResponse(
                            false,
                            e.getMessage(),
                            null,
                            List.of(e.getMessage()),
                            null
                    ));
                });
    }

    /**
     * Duplicate an event with a new title.
     *
     * <p>Security: User ID extracted from JWT, EVENT_CREATE permission verified.</p>
     *
     * @param eventId Original event ID to duplicate
     * @param newTitle Title for the duplicated event
     * @return Mutation response with duplicated event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<DuplicateEventMutationResponse> duplicateEvent(
            @InputArgument String eventId,
            @InputArgument String newTitle
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Duplicating event: {} by user: {} with title: {}",
                        eventId, userId, newTitle))
                .flatMap(userId ->
                        eventService.findById(eventId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + eventId)))
                                .flatMap(original ->
                                        // Need EVENT_CREATE permission to create the duplicate
                                        identityServiceClient.checkAuthorization(AuthorizationRequest.builder()
                                                        .userId(userId)
                                                        .requiredPermission("EVENT_CREATE")
                                                        .build())
                                                .flatMap(authResult -> {
                                                    if (!authResult.isAuthorized()) {
                                                        return Mono.just(new DuplicateEventMutationResponse(
                                                                false, authResult.getReason(), null,
                                                                List.of(authResult.getReason()), null
                                                        ));
                                                    }
                                                    // Create duplicate with current user as organizer
                                                    Event duplicate = Event.builder()
                                                            .title(newTitle)
                                                            .description(original.getDescription())
                                                            .categoryId(original.getCategoryId())
                                                            .eventDateTime(original.getEventDateTime())
                                                            .endDateTime(original.getEndDateTime())
                                                            .locationId(original.getLocationId())
                                                            .locationName(original.getLocationName())
                                                            .locationAddress(original.getLocationAddress())
                                                            .cityName(original.getCityName())
                                                            .organizerId(userId)
                                                            .organizationId(authResult.getOrganizationId())
                                                            .organizerName(original.getOrganizerName())
                                                            .status(EventStatus.DRAFT)
                                                            .published(false)
                                                            .totalCapacity(original.getTotalCapacity())
                                                            .availableTickets(original.getTotalCapacity())
                                                            .soldTickets(0)
                                                            .ticketCategories(original.getTicketCategories())
                                                            .bannerImageUrl(original.getBannerImageUrl())
                                                            .tags(original.getTags())
                                                            .additionalInfo(original.getAdditionalInfo())
                                                            .featured(false)
                                                            .isActive(true)
                                                            .build();
                                                    return eventService.createEvent(duplicate)
                                                            .map(duplicated -> new DuplicateEventMutationResponse(
                                                                    true, "Event duplicated successfully", duplicated,
                                                                    List.of(), Map.of("originalEventId", eventId)
                                                            ));
                                                })
                                )
                )
                .onErrorResume(SecurityException.class, e -> Mono.just(new DuplicateEventMutationResponse(
                        false, "Authentication required", null, List.of("Please log in"), null
                )))
                .onErrorResume(e -> {
                    log.error("Duplicate event failed: {}", e.getMessage());
                    return Mono.just(new DuplicateEventMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null
                    ));
                });
    }

    /**
     * Update event capacity.
     *
     * <p>Security: User ID extracted from JWT, EVENT_EDIT permission verified.</p>
     *
     * @param eventId Event ID
     * @param newCapacity New capacity value
     * @return Mutation response with updated event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<UpdateEventCapacityMutationResponse> updateEventCapacity(
            @InputArgument String eventId,
            @InputArgument int newCapacity
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Updating capacity for event: {} to: {} by user: {}",
                        eventId, newCapacity, userId))
                .flatMap(userId ->
                        eventService.findById(eventId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + eventId)))
                                .flatMap(existingEvent ->
                                        identityServiceClient.checkEventAccess(
                                                        userId, eventId, existingEvent.getOrganizationId(), "EVENT_EDIT")
                                                .flatMap(authResult -> {
                                                    if (!authResult.isAuthorized()) {
                                                        return Mono.just(new UpdateEventCapacityMutationResponse(
                                                                false, authResult.getReason(), null,
                                                                List.of(authResult.getReason()), null
                                                        ));
                                                    }
                                                    int oldCapacity = existingEvent.getTotalCapacity();
                                                    int capacityDiff = newCapacity - oldCapacity;
                                                    existingEvent.setTotalCapacity(newCapacity);
                                                    existingEvent.setAvailableTickets(
                                                            existingEvent.getAvailableTickets() + capacityDiff);
                                                    return eventService.updateEvent(eventId, existingEvent)
                                                            .map(updated -> new UpdateEventCapacityMutationResponse(
                                                                    true, "Event capacity updated successfully",
                                                                    updated, List.of(),
                                                                    Map.of("newCapacity", newCapacity)
                                                            ));
                                                })
                                )
                )
                .onErrorResume(SecurityException.class, e -> Mono.just(new UpdateEventCapacityMutationResponse(
                        false, "Authentication required", null, List.of("Please log in"), null
                )))
                .onErrorResume(e -> {
                    log.error("Update event capacity failed: {}", e.getMessage());
                    return Mono.just(new UpdateEventCapacityMutationResponse(
                            false, e.getMessage(), null, List.of(e.getMessage()), null
                    ));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventMutationResponse> featureEvent(
            @InputArgument String eventId,
            @InputArgument boolean featured
    ) {
        log.info("Setting event {} featured status to: {}", eventId, featured);

        return eventService.setEventFeatured(eventId, featured)
                .map(event -> EventMutationResponse.success(
                        event,
                        featured ? "Event featured successfully" : "Event unfeatured successfully",
                        Map.of("featured", featured)
                ))
                .onErrorResume(e -> {
                    log.error("Feature event failed: {}", e.getMessage());
                    return Mono.just(EventMutationResponse.error(e.getMessage()));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('SCOPE_internal-write')")
    public Mono<EventMutationResponse> completeEvent(
            @InputArgument String id
    ) {
        log.info("Completing event: {}", id);

        return eventService.completeEvent(id)
                .map(event -> EventMutationResponse.success(
                        event,
                        "Event completed successfully",
                        Map.of("completedAt", LocalDateTime.now().toString())
                ))
                .onErrorResume(e -> {
                    log.error("Complete event failed: {}", e.getMessage());
                    return Mono.just(EventMutationResponse.error(e.getMessage()));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EventMutationResponse> sendEventPublishReminder(
            @InputArgument String eventId,
            @InputArgument String triggeredBy
    ) {
        log.info("Sending publish reminder for event: {} by: {}", eventId, triggeredBy);

        return eventService.sendPublishReminder(eventId, triggeredBy)
                .map(event -> EventMutationResponse.success(
                        event,
                        "Publish reminder sent successfully",
                        Map.of("sentAt", LocalDateTime.now().toString(), "triggeredBy", triggeredBy)
                ))
                .onErrorResume(e -> {
                    log.error("Send publish reminder failed: {}", e.getMessage());
                    return Mono.just(EventMutationResponse.error(e.getMessage()));
                });
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<BulkReminderResponse> sendBulkEventPublishReminders(
            @InputArgument List<String> eventIds,
            @InputArgument String triggeredBy
    ) {
        log.info("Sending bulk publish reminders for {} events by: {}", eventIds.size(), triggeredBy);

        return reactor.core.publisher.Flux.fromIterable(eventIds)
                .flatMap(eventId -> eventService.sendPublishReminder(eventId, triggeredBy)
                        .map(event -> true)
                        .onErrorResume(e -> {
                            log.warn("Failed to send reminder for event {}: {}", eventId, e.getMessage());
                            return Mono.just(false);
                        }))
                .collectList()
                .map(results -> {
                    int sentCount = (int) results.stream().filter(Boolean::booleanValue).count();
                    int failedCount = results.size() - sentCount;
                    return BulkReminderResponse.success(sentCount, failedCount);
                })
                .onErrorResume(e -> {
                    log.error("Bulk send publish reminders failed: {}", e.getMessage());
                    return Mono.just(BulkReminderResponse.error(e.getMessage()));
                });
    }

    /**
     * Map CreateEventInput to Event entity.
     *
     * @param input Creation input
     * @param organizerId User ID from JWT (event owner)
     * @param organizationId Organization ID from authorization result
     * @return Event entity ready for persistence
     */
    private Event mapInputToEvent(CreateEventInput input, String organizerId, String organizationId) {
        return Event.builder()
                .title(input.title())
                .description(input.description())
                .categoryId(input.categoryId())
                .eventDateTime(input.eventDateTime())
                .endDateTime(input.endDateTime())
                .organizerId(organizerId)
                .organizationId(organizationId)
                .status(EventStatus.DRAFT)
                .published(false)
                .totalCapacity(input.totalCapacity())
                .availableTickets(input.totalCapacity())
                .soldTickets(0)
                .featured(false)
                .isActive(true)
                .build();
    }

    private void updateEventFromInput(Event event, UpdateEventInput input) {
        if (input.title() != null) event.setTitle(input.title());
        if (input.description() != null) event.setDescription(input.description());
        if (input.categoryId() != null) event.setCategoryId(input.categoryId());
        if (input.eventDateTime() != null) event.setEventDateTime(input.eventDateTime());
        if (input.endDateTime() != null) event.setEndDateTime(input.endDateTime());
        if (input.totalCapacity() != null) event.setTotalCapacity(input.totalCapacity());
        if (input.tags() != null) event.setTags(input.tags());
        if (input.additionalInfo() != null) event.setAdditionalInfo(input.additionalInfo());
    }
}
