package com.pml.catalog.service;

import com.pml.catalog.dto.CursorPaginationInput;
import com.pml.catalog.dto.EventConnection;
import com.pml.catalog.dto.PageableInput;
import com.pml.catalog.dto.PagedResult;
import com.pml.catalog.domain.model.Event;
import com.pml.shared.constants.EventStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event Service Interface
 */
public interface EventService {

    // ==========================================
    // Single Event Operations
    // ==========================================

    Mono<Event> findById(String id);

    Mono<Event> createEvent(Event event);

    Mono<Event> updateEvent(String id, Event event);

    Mono<Event> publishEvent(String id);

    /**
     * Submit event for approval.
     * Transitions event from DRAFT to PENDING_APPROVAL.
     * Publishes EventSubmittedEvent for admin notification.
     *
     * @param id Event ID
     * @return Updated event
     */
    Mono<Event> submitForApproval(String id);

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

    Mono<Event> approveEvent(String id);

    Mono<Event> rejectEvent(String id, String reason);

    Mono<Event> updateSoldTickets(String id, int soldCount);

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

    Flux<Event> findPublishedEvents();

    Flux<Event> searchEvents(String query);

    Flux<Event> findUpcomingEvents();

    Flux<Event> findEventsByCategory(String categoryId);

    Flux<Event> findEventsByCity(String city);

    Flux<Event> findEventsByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    Flux<Event> findEventsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice);

    Flux<Event> findFeaturedEvents();

    Flux<Event> findFreeEvents();

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

    // ==========================================
    // Cursor-based Pagination Methods
    // ==========================================

    /**
     * Find published events with cursor pagination
     */
    Mono<EventConnection> findPublishedEventsCursor(CursorPaginationInput pagination);

    /**
     * Search events with cursor pagination
     */
    Mono<EventConnection> searchEventsCursor(String query, CursorPaginationInput pagination);

    /**
     * Find upcoming events with cursor pagination
     */
    Mono<EventConnection> findUpcomingEventsCursor(CursorPaginationInput pagination);

    /**
     * Find events by category with cursor pagination
     */
    Mono<EventConnection> findEventsByCategoryCursor(String category, CursorPaginationInput pagination);

    /**
     * Find events by city with cursor pagination
     */
    Mono<EventConnection> findEventsByCityCursor(String city, CursorPaginationInput pagination);

    /**
     * Find events by date range with cursor pagination
     */
    Mono<EventConnection> findEventsByDateRangeCursor(
            LocalDateTime startDate, LocalDateTime endDate, CursorPaginationInput pagination);

    /**
     * Find featured events with cursor pagination
     */
    Mono<EventConnection> findFeaturedEventsCursor(CursorPaginationInput pagination);

    /**
     * Find events by organizer with cursor pagination
     */
    Mono<EventConnection> findEventsByOrganizerCursor(String organizerId, CursorPaginationInput pagination);

    // ==========================================
    // Admin Pagination Methods (Dashboard Tables)
    // ==========================================

    /**
     * Find all events with admin pagination
     */
    Mono<PagedResult<Event>> findEventsAdmin(PageableInput pageable);

    /**
     * Find events by status with admin pagination
     */
    Mono<PagedResult<Event>> findEventsByStatusAdmin(EventStatus status, PageableInput pageable);

    /**
     * Find draft events by organizer with admin pagination
     */
    Mono<PagedResult<Event>> findDraftEventsAdmin(String organizerId, PageableInput pageable);

    /**
     * Find pending approval events with admin pagination
     */
    Mono<PagedResult<Event>> findPendingApprovalEventsAdmin(PageableInput pageable);

    /**
     * Find overdue approval events with admin pagination
     */
    Mono<PagedResult<Event>> findOverdueApprovalEventsAdmin(PageableInput pageable);

    /**
     * Find approved but not published events with admin pagination
     */
    Mono<PagedResult<Event>> findApprovedNotPublishedEventsAdmin(PageableInput pageable);
}
