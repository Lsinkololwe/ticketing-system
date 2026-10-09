package com.pml.catalog.service;

import com.pml.catalog.domain.model.Event;
import com.pml.shared.constants.EventStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Event Service Interface
 */
public interface EventService {

    // ==========================================
    // Single Event Operations
    // ==========================================

    Mono<Event> findById(String id);

    /**
     * An event as the current caller is entitled to see it, or empty.
     *
     * <p>Distinct from {@link #findById}, which loads the document whoever is asking.
     * That one is correct for service-to-service reads over
     * {@code /api/internal/events}, where the caller is another service and there is no
     * tenancy to apply; it is not correct behind a query the schema calls PUBLIC.
     */
    Mono<Event> findVisibleById(String id);

    /**
     * A new DRAFT event with its venue and tiers, written in one transaction: either all of it
     * exists afterwards or none of it does.
     */
    Mono<Event> createEvent(com.pml.catalog.web.graphql.dto.CreateEventInput input, String actorId, String organizationId);

    /**
     * An organizer's edit, under the material-change rule: a material change sends an APPROVED event
     * back to DRAFT and is refused while the event is under review or published.
     *
     * @param platformAdmin whether the caller may set {@code featured}
     */
    /**
     * A DRAFT copy of {@code original} in the caller's organization, with copies of its tiers and
     * nothing sold. The copy never shares a tier with the original.
     */
    Mono<Event> duplicateEvent(Event original, String newTitle, String actorId, String organizationId);

    Mono<Event> updateEvent(Event existing, com.pml.catalog.web.graphql.dto.UpdateEventInput input,
                            String actorId, boolean platformAdmin);

    /** APPROVED → PUBLISHED, staging {@code catalog.EventPublished}; a PUBLISHED event is returned unchanged. */
    Mono<Event> publishEvent(String id);

    /**
     * Confirms a scheduled publication: an APPROVED event whose {@code publishAt} is still ahead is
     * marked {@code publishScheduled}. Idempotent; the timer itself belongs to the schedule workflow.
     */
    Mono<Event> scheduleEventPublish(String id);

    /** Stops a scheduled publication and clears {@code publishAt}; an event that has none is returned unchanged. */
    Mono<Event> clearPublishSchedule(String id);

    /** PUBLISHED → APPROVED while no ticket is sold. */
    /**
     * {@code soldCount} is booking's own count of non-refunded tickets, verified by
     * the caller before this method runs — this service does not trust {@code Event.soldTickets},
     * which is a display figure, not the authority.
     */
    Mono<Event> unpublishEvent(String id, long soldCount);

    /** A PUBLISHED event moves to {@code newDateTime}, at most three times. */
    Mono<Event> rescheduleEvent(String id, Instant newDateTime, String reason);

    Mono<Event> cancelEvent(String id);

    /**
     * Cancel an event with a specific reason.
     * Publishes EventCancelledEvent for automatic refund processing.
     */
    Mono<Event> cancelEventWithReason(String id, String reason);

    /**
     * Cancel an event with full details (triggers refund workflow).
     */
    Mono<Event> cancelEventWithDetails(String id, String reason, boolean notifyAttendees, boolean triggerRefunds);

    /**
     * Complete an event (called after event end date passes).
     */
    Mono<Event> completeEvent(String id);

    /**
     * Feature or unfeature an event for homepage promotion.
     */
    Mono<Event> setEventFeatured(String id, boolean featured);

    /**
     * Send a publish reminder to the organizer for an approved event.
     * The event must be in APPROVED status.
     *
     * @param eventId the event ID
     * @param triggeredBy the admin who triggered the reminder
     * @return the event after reminder is sent
     */
    Mono<Event> sendPublishReminder(String eventId, String triggeredBy);

    Mono<Void> deleteEvent(String id);

    /**
     * Soft delete an event with reason and audit trail.
     * Validates that no tickets have been sold.
     *
     * @param id Event ID
     * @param deletedBy User ID who is deleting
     * @param reason Reason for deletion
     * @return Empty Mono on success
     */
    Mono<Void> deleteEventWithReason(String id, String deletedBy, String reason);

    // ==========================================
    // Flux-based Queries (for resolver pagination helpers)
    // ==========================================

    Flux<Event> findAllEvents();

    Flux<Event> searchEvents(String query);

    Flux<Event> findEventsByCategory(String categoryId);

    Flux<Event> findEventsByCity(String city);

    Flux<Event> findEventsByOrganizer(String organizerId);

    Flux<Event> findEventsByStatus(EventStatus status);

    Flux<Event> findDraftEventsByOrganizer(String organizerId);

    Flux<Event> findPendingApprovalEvents();

    Flux<Event> findOverdueApprovalEvents();

    Flux<Event> findApprovedNotPublishedEvents();

    Flux<Event> findCancelledEvents();

    Flux<Event> findCompletedEvents();

    // ==========================================
    // Count Operations
    // ==========================================

    Mono<Long> countAll();

    Mono<Long> countByOrganizer(String organizerId);

    Mono<Long> countByCategory(String categoryId);

    Mono<Long> countByCity(String city);

    Mono<Long> countByStatus(EventStatus status);
}
