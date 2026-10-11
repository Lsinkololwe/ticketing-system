package com.pml.catalog.web.graphql.mutation;

import com.pml.shared.security.Permission;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.catalog.web.graphql.dto.EventCancellationInputDto;
import com.pml.catalog.web.graphql.dto.EventCancellationResponseDto;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.web.graphql.dto.BulkReminderResponse;
import com.pml.catalog.web.graphql.dto.CreateEventInput;
import com.pml.catalog.web.graphql.dto.EventMutationResponse;
import com.pml.catalog.web.graphql.dto.RescheduleEventInput;
import com.pml.catalog.web.graphql.dto.UpdateEventInput;
import com.pml.catalog.workflow.approval.EventApprovalProcess;
import com.pml.catalog.workflow.lifecycle.EventLifecycleProcess;
import com.pml.catalog.workflow.schedule.EventPublishScheduleProcess;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.exception.EventNotFoundException;
import com.pml.catalog.security.Callers;
import com.pml.catalog.security.EventWriteGuard;
import com.pml.catalog.service.EventService;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;

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
@Validated
@RequiredArgsConstructor
public class EventMutationResolver {

    private final EventService eventService;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;
    private final IdentityServiceClient identityServiceClient;

    /**
     * The only way an event is loaded in order to be changed.
     *
     * <p>Both locks in one call: the repository filter that will not return another
     * organization's event, and the identity-service check for the permission this particular
     * mutation needs. Neither substitutes for the other — see {@link EventWriteGuard}.
     */
    private final EventWriteGuard eventWriteGuard;

    /** Publish, unpublish, reschedule and cancel go through the event's lifecycle workflow. */
    private final EventLifecycleProcess lifecycle;

    /** Submission and the reviewer's decisions go through the event's review workflow. */
    private final EventApprovalProcess approvals;

    /** A publication the organizer scheduled for later waits in its own workflow. */
    private final EventPublishScheduleProcess schedules;

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
    public Mono<Event> createEvent(
            @Valid @InputArgument CreateEventInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Creating event for user: {}", userId))
                .flatMap(userId ->
                        // Step 1: Verify user holds event:create and get their organization
                        identityServiceClient.checkAuthorization(AuthorizationRequest.builder()
                                        .userId(userId)
                                        .requiredPermission(Permission.EVENT_CREATE.code())
                                        .build())
                                .flatMap(authResult -> {
                                    if (!authResult.isAuthorized()) {
                                        return Mono.error(new AccessDeniedException(authResult.getReason()));
                                    }

                                    // Step 2: the event, its venue and its tiers, in one transaction
                                    return eventService.createEvent(input, userId, authResult.getOrganizationId());
                                })
                );
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
    public Mono<Event> updateEvent(
            @InputArgument String id,
            @Valid @InputArgument UpdateEventInput input
    ) {
        log.info("Updating event: {}", id);
        return eventWriteGuard.forWrite(id, Permission.EVENT_EDIT)
                .flatMap(existingEvent -> Mono.zip(SecurityContextUtils.requireCurrentUserId(), Callers.platformAdministrator())
                        .flatMap(caller -> {
                            boolean wasScheduled = existingEvent.isPublishScheduled();
                            return eventService.updateEvent(existingEvent, input, caller.getT1(), caller.getT2())
                                    .flatMap(updated -> followSchedule(wasScheduled, updated).thenReturn(updated));
                        }));
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
    public Mono<String> deleteEvent(
            @InputArgument String id
    ) {
        log.info("Deleting event: {}", id);
        return eventWriteGuard.forWrite(id, Permission.EVENT_DELETE)
                .flatMap(existingEvent -> eventService.deleteEvent(id).thenReturn(id));
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
    public Mono<Event> publishEvent(
            @InputArgument String id
    ) {
        log.info("Publishing event: {}", id);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> eventWriteGuard.forWrite(id, Permission.EVENT_PUBLISH)
                        .flatMap(existingEvent -> {
                            // An approved event with a go-live time still ahead is scheduled, not
                            // published: it stays APPROVED, hidden from buyers, and the schedule
                            // workflow publishes it through the same path at that time.
                            if (existingEvent.getStatus() == EventStatus.APPROVED
                                    && existingEvent.getPublishAt() != null
                                    && existingEvent.getPublishAt().isAfter(clock.instant())) {
                                return eventService.scheduleEventPublish(id)
                                        .flatMap(scheduled -> schedules.schedule(id, scheduled.getPublishAt())
                                                .thenReturn(scheduled));
                            }
                            return lifecycle.publish(id, userId);
                        }));
    }

    /**
     * Withdraw a scheduled publication: the event stays APPROVED and its go-live time is cleared.
     *
     * <p>Security: EVENT_PUBLISH permission verified on the owning organization's event.</p>
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<Event> cancelScheduledPublish(@InputArgument String eventId) {
        log.info("Cancelling scheduled publication of event: {}", eventId);
        return eventWriteGuard.forWrite(eventId, Permission.EVENT_PUBLISH)
                .flatMap(existingEvent -> eventService.clearPublishSchedule(eventId)
                        .flatMap(cleared -> schedules.cancel(eventId).thenReturn(cleared)));
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
    public Mono<Event> unpublishEvent(
            @InputArgument String id
    ) {
        log.info("Unpublishing event: {}", id);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> eventWriteGuard.forWrite(id, Permission.EVENT_PUBLISH)
                        .flatMap(existingEvent -> lifecycle.unpublish(id, userId)));
    }

    /**
     * Move a published event to a new start. Tickets stay valid; booking opens the
     * holders' refund window from {@code catalog.EventRescheduled}.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<Event> rescheduleEvent(
            @Valid @InputArgument RescheduleEventInput input
    ) {
        log.info("Rescheduling event: {}", input.eventId());
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> eventWriteGuard.forWrite(input.eventId(), Permission.EVENT_PUBLISH)
                        .flatMap(existingEvent -> lifecycle.reschedule(
                                input.eventId(), userId, input.newStartsAt(), input.reason())));
    }

    /**
     * Cancel an event with optional refunds and notifications.
     *
     * <p>Security: User ID extracted from JWT, EVENT_DELETE permission verified (cancellation is destructive).</p>
     *
     * @param id Event ID to cancel
     * @param input Cancellation options (reason, notify attendees, trigger refunds)
     * @return Cancellation response, with the refund workflow's id if refunds were started
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<EventCancellationResponseDto> cancelEvent(
            @InputArgument String id,
            @InputArgument EventCancellationInputDto input
    ) {
        log.info("Cancelling event: {} reason: {}", id, input.getReason());
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> eventWriteGuard.forWrite(id, Permission.EVENT_CANCEL)
                        .flatMap(existingEvent -> lifecycle.cancel(id, userId, input.getReason())
                                // Every ticket is refunded in full: booking's consumer
                                // of catalog.EventCancelled owns the refunds, so no id is invented here.
                                .map(cancelled -> EventCancellationResponseDto.builder()
                                        .event(cancelled)
                                        .ticketsAffected(cancelled.getSoldTickets())
                                        .refundSagaInitiated(true)
                                        .sagaId(null)
                                        .build())));
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
    public Mono<Event> submitEventForApproval(
            @InputArgument String eventId
    ) {
        log.info("Submitting event for approval: {}", eventId);
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userId -> eventWriteGuard.forWrite(eventId, Permission.EVENT_EDIT)
                        .flatMap(existingEvent -> approvals.submit(eventId, userId)));
    }

    /**
     * Approve event.
     * reviewerId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Event> approveEvent(
            @InputArgument String eventId,
            @InputArgument String comments
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewerId -> log.info("Approving event: {} by reviewer: {} comments: {}", eventId, reviewerId, comments))
                .flatMap(reviewerId -> approvals.approve(eventId, reviewerId, comments));
    }

    /**
     * Reject event.
     * reviewerId is extracted from JWT - OWASP A01:2021 compliance
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Event> rejectEvent(
            @InputArgument String eventId,
            @InputArgument String comments
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reviewerId -> log.info("Rejecting event: {} by reviewer: {} reason: {}", eventId, reviewerId, comments))
                .flatMap(reviewerId -> approvals.reject(eventId, reviewerId, comments));
    }

    /**
     * Duplicate an event with a new title.
     *
     * <p>Security: User ID extracted from JWT, event:create verified.</p>
     *
     * @param eventId Original event ID to duplicate
     * @param newTitle Title for the duplicated event
     * @return Mutation response with duplicated event
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Mono<Event> duplicateEvent(
            @InputArgument String eventId,
            @InputArgument String newTitle
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(userId -> log.info("Duplicating event: {} by user: {} with title: {}",
                        eventId, userId, newTitle))
                .flatMap(userId ->
                        // findVisibleById, not findById. Duplication copies the source
                        // event's title, description, capacity and tier structure into the
                        // caller's own draft, so reading it is the whole operation. Under the
                        // bare findById any ORGANIZER could lift a rival's unannounced line-up
                        // and pricing by id. A published event is public and stays duplicable;
                        // a draft is duplicable only by the organization that owns it.
                        eventService.findVisibleById(eventId)
                                .switchIfEmpty(Mono.error(new EventNotFoundException("event not found: " + eventId)))
                                .flatMap(original ->
                                        // event:create is about the duplicate, not the original:
                                        // the caller is creating an event in their own
                                        // organization, so this is the same check createEvent makes.
                                        identityServiceClient.checkAuthorization(AuthorizationRequest.builder()
                                                        .userId(userId)
                                                        .requiredPermission(Permission.EVENT_CREATE.code())
                                                        .build())
                                                .flatMap(authResult -> {
                                                    if (!authResult.isAuthorized()) {
                                                        return Mono.error(new AccessDeniedException(authResult.getReason()));
                                                    }
                                                    return eventService.duplicateEvent(original, newTitle, userId,
                                                            authResult.getOrganizationId());
                                                })
                                )
                );
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
    public Mono<Event> updateEventCapacity(
            @InputArgument String eventId,
            @InputArgument int newCapacity
    ) {
        log.info("Updating capacity for event: {} to: {}", eventId, newCapacity);
        return eventWriteGuard.forWrite(eventId, Permission.EVENT_EDIT)
                .flatMap(existingEvent -> SecurityContextUtils.requireCurrentUserId()
                        .flatMap(actorId -> eventService.updateEvent(existingEvent, UpdateEventInput.capacity(newCapacity),
                                actorId, false)));
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
                        Map.of("completedAt", clock.instant().toString())
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
                        Map.of("sentAt", clock.instant().toString(), "triggeredBy", triggeredBy)
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
                    return BulkReminderResponse.of(sentCount, failedCount);
                });
    }

    /**
     * Keeps the schedule workflow in step with an edit: a new go-live time moves the wait, and an
     * edit that sent the event back for review ended the schedule, so the wait is stopped.
     */
    private Mono<Void> followSchedule(boolean wasScheduled, Event updated) {
        if (!wasScheduled) {
            return Mono.empty();
        }
        if (!updated.isPublishScheduled() || updated.getPublishAt() == null) {
            return schedules.cancel(updated.getId());
        }
        return schedules.move(updated.getId(), updated.getPublishAt());
    }
}
