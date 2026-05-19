package com.pml.catalog.service.impl;

import com.pml.catalog.dto.CursorPaginationInput;
import com.pml.catalog.dto.EventConnection;
import com.pml.catalog.dto.EventEdge;
import com.pml.catalog.dto.PageableInput;
import com.pml.catalog.dto.PagedResult;
import com.pml.catalog.dto.PageInfo;
import com.pml.catalog.event.domain.EventApprovedEvent;
import com.pml.catalog.event.domain.EventCancelledEvent;
import com.pml.catalog.event.domain.EventCompletedEvent;
import com.pml.catalog.event.domain.EventCreatedEvent;
import com.pml.catalog.event.domain.EventDeletedEvent;
import com.pml.catalog.event.domain.EventPublishedEvent;
import com.pml.catalog.event.domain.EventRejectedEvent;
import com.pml.catalog.event.domain.EventRescheduledEvent;
import com.pml.catalog.event.domain.EventSubmittedEvent;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.exception.InvalidEventStateException;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.EventService;
import com.pml.catalog.util.CursorUtils;
import com.pml.shared.constants.EventStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Event Service Implementation with cursor-based pagination.
 * Publishes domain events via Spring Modulith for cross-service communication.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ==========================================
    // Single Event Operations
    // ==========================================

    @Override
    public Mono<Event> findById(String id) {
        return eventRepository.findById(id);
    }

    @Override
    @Transactional
    public Mono<Event> createEvent(Event event) {
        log.info("Creating new event: {}", event.getTitle());
        event.setStatus(EventStatus.DRAFT);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        event.setAvailableTickets(event.getTotalCapacity());
        event.setDeleted(false);
        return eventRepository.save(event)
                .doOnSuccess(e -> {
                    log.info("Event created: {}", e.getId());
                    // Publish domain event for cross-service communication
                    EventCreatedEvent domainEvent = new EventCreatedEvent(
                            e.getId(),
                            e.getOrganizerId(),
                            e.getOrganizationId(),
                            e.getTitle(),
                            e.getEventDateTime(),
                            e.getTotalCapacity(),
                            e.getCreatedBy()
                    );
                    eventPublisher.publishEvent(domainEvent);
                    log.info("Published EventCreatedEvent for event: {}", e.getId());
                });
    }

    @Override
    public Mono<Event> updateEvent(String id, Event event) {
        return eventRepository.findById(id)
                .flatMap(existing -> {
                    existing.setTitle(event.getTitle());
                    existing.setDescription(event.getDescription());
                    existing.setCategoryId(event.getCategoryId());
                    existing.setEventDateTime(event.getEventDateTime());
                    existing.setEndDateTime(event.getEndDateTime());
                    existing.setLocationId(event.getLocationId());
                    existing.setLocationName(event.getLocationName());
                    existing.setLocationAddress(event.getLocationAddress());
                    existing.setTotalCapacity(event.getTotalCapacity());
                    existing.setTicketCategories(event.getTicketCategories());
                    existing.setBannerImageUrl(event.getBannerImageUrl());
                    existing.setTags(event.getTags());
                    existing.setUpdatedAt(LocalDateTime.now());
                    return eventRepository.save(existing);
                });
    }

    @Override
    @Transactional
    public Mono<Event> publishEvent(String id) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                .flatMap(event -> {
                    // STATE VALIDATION: Only APPROVED events can be published
                    if (event.getStatus() != EventStatus.APPROVED) {
                        return Mono.error(new InvalidEventStateException(
                                id,
                                event.getStatus().name(),
                                EventStatus.APPROVED.name()
                        ));
                    }
                    event.setStatus(EventStatus.PUBLISHED);
                    event.setPublished(true);
                    event.setPublishedAt(LocalDateTime.now());
                    event.setUpdatedAt(LocalDateTime.now());
                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> {
                    log.info("Event published: {}", id);
                    // Publish domain event for cross-service communication
                    EventPublishedEvent domainEvent = new EventPublishedEvent(
                            event.getId(),
                            event.getOrganizerId(),
                            event.getOrganizerName(),
                            event.getTitle(),
                            event.getEventDateTime(),
                            event.getEndDateTime(),
                            event.getTotalCapacity(),
                            "ZMW",
                            new BigDecimal("0.05")
                    );
                    eventPublisher.publishEvent(domainEvent);
                    log.info("Published EventPublishedEvent for event: {}", id);
                });
    }

    /**
     * Submit event for approval.
     * Transitions event from DRAFT to PENDING_APPROVAL.
     *
     * STATE VALIDATION: Only DRAFT or REJECTED events can be submitted for approval.
     */
    @Override
    @Transactional
    public Mono<Event> submitForApproval(String id) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                .flatMap(event -> {
                    // STATE VALIDATION: Only DRAFT or REJECTED events can be submitted
                    if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.REJECTED) {
                        return Mono.error(new InvalidEventStateException(
                                String.format("Event %s cannot be submitted for approval. Current status: %s. " +
                                        "Only DRAFT or REJECTED events can be submitted.",
                                        id, event.getStatus())
                        ));
                    }

                    // Calculate approval deadline (e.g., 48 hours from submission)
                    LocalDateTime approvalDeadline = LocalDateTime.now().plusHours(48);

                    event.setStatus(EventStatus.PENDING_APPROVAL);
                    event.setSubmittedForApprovalAt(LocalDateTime.now());
                    event.setApprovalDeadline(approvalDeadline);
                    event.setSubmissionCount(event.getSubmissionCount() + 1);
                    event.setUpdatedAt(LocalDateTime.now());

                    // Clear any previous rejection data
                    event.setRejectedAt(null);
                    event.setRejectedBy(null);
                    event.setRejectionReason(null);

                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> {
                    log.info("Event submitted for approval: {}", id);
                    // Publish domain event for admin notification
                    EventSubmittedEvent domainEvent = new EventSubmittedEvent(
                            event.getId(),
                            event.getOrganizerId(),
                            event.getOrganizationId(),
                            event.getTitle(),
                            event.getEventDateTime(),
                            event.getSubmissionCount(),
                            event.getApprovalDeadline()
                    );
                    eventPublisher.publishEvent(domainEvent);
                    log.info("Published EventSubmittedEvent for event: {}", id);
                });
    }

    @Override
    @Transactional
    public Mono<Event> cancelEvent(String id) {
        return cancelEventWithReason(id, "Event cancelled by organizer");
    }

    /**
     * Cancel event with a specific reason.
     * Publishes EventCancelledEvent for automatic refund processing.
     */
    @Override
    @Transactional
    public Mono<Event> cancelEventWithReason(String id, String reason) {
        return cancelEventWithDetails(id, reason, true, true);
    }

    /**
     * Valid statuses from which an event can be cancelled.
     */
    private static final Set<EventStatus> CANCELLABLE_STATUSES = EnumSet.of(
            EventStatus.DRAFT,
            EventStatus.PENDING_APPROVAL,
            EventStatus.APPROVED,
            EventStatus.PUBLISHED
    );

    @Override
    @Transactional
    public Mono<Event> cancelEventWithDetails(String id, String reason, boolean notifyAttendees, boolean triggerRefunds) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                .flatMap(event -> {
                    // STATE VALIDATION: Only certain statuses can be cancelled
                    if (!CANCELLABLE_STATUSES.contains(event.getStatus())) {
                        return Mono.error(new InvalidEventStateException(
                                String.format("Event %s cannot be cancelled. Current status: %s. Cancellation only allowed from: %s",
                                        id, event.getStatus(), CANCELLABLE_STATUSES)
                        ));
                    }
                    event.setStatus(EventStatus.CANCELLED);
                    event.setActive(false);
                    event.setUpdatedAt(LocalDateTime.now());
                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> {
                    log.info("Event cancelled: {}", id);
                    // Publish domain event for automatic refunds
                    EventCancelledEvent domainEvent = new EventCancelledEvent(
                            event.getId(),
                            event.getOrganizerId(),
                            event.getTitle(),
                            event.getEventDateTime(),
                            reason,
                            "ORGANIZER",
                            event.getSoldTickets()
                    );
                    eventPublisher.publishEvent(domainEvent);
                    log.info("Published EventCancelledEvent for event: {}", id);
                });
    }

    @Override
    @Transactional
    public Mono<Event> approveEvent(String id) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                .flatMap(event -> {
                    // STATE VALIDATION: Only PENDING_APPROVAL events can be approved
                    if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
                        return Mono.error(new InvalidEventStateException(
                                id,
                                event.getStatus().name(),
                                EventStatus.PENDING_APPROVAL.name()
                        ));
                    }
                    event.setStatus(EventStatus.APPROVED);
                    event.setApprovedAt(LocalDateTime.now());
                    event.setUpdatedAt(LocalDateTime.now());
                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> {
                    log.info("Event approved: {}", id);
                    // Publish domain event
                    EventApprovedEvent domainEvent = new EventApprovedEvent(
                            event.getId(),
                            event.getOrganizerId(),
                            event.getTitle(),
                            event.getApprovedBy() != null ? event.getApprovedBy() : "SYSTEM",
                            ""
                    );
                    eventPublisher.publishEvent(domainEvent);
                    log.info("Published EventApprovedEvent for event: {}", id);
                });
    }

    /**
     * Mark event as completed.
     * Publishes EventCompletedEvent to lock escrow and start hold period.
     *
     * STATE VALIDATION: Only PUBLISHED events can be completed.
     */
    @Override
    @Transactional
    public Mono<Event> completeEvent(String id) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                .flatMap(event -> {
                    // STATE VALIDATION: Only PUBLISHED events can be completed
                    if (event.getStatus() != EventStatus.PUBLISHED) {
                        return Mono.error(new InvalidEventStateException(
                                id,
                                event.getStatus().name(),
                                EventStatus.PUBLISHED.name()
                        ));
                    }
                    event.setStatus(EventStatus.COMPLETED);
                    event.setUpdatedAt(LocalDateTime.now());
                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> {
                    log.info("Event completed: {}", id);
                    EventCompletedEvent domainEvent = new EventCompletedEvent(
                            event.getId(),
                            event.getOrganizerId(),
                            event.getTitle(),
                            event.getEventDateTime(),
                            event.getSoldTickets(),
                            event.getSoldTickets(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO
                    );
                    eventPublisher.publishEvent(domainEvent);
                    log.info("Published EventCompletedEvent for event: {}", id);
                });
    }

    @Override
    @Transactional
    public Mono<Event> setEventFeatured(String id, boolean featured) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                .flatMap(event -> {
                    event.setFeatured(featured);
                    event.setUpdatedAt(LocalDateTime.now());
                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> {
                    log.info("Event {} featured status set to: {}", id, featured);
                });
    }

    @Override
    @Transactional
    public Mono<Event> sendPublishReminder(String eventId, String triggeredBy) {
        log.info("Sending publish reminder for event: {} by: {}", eventId, triggeredBy);
        return eventRepository.findById(eventId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + eventId)))
                .flatMap(event -> {
                    // Validate event is in a state that allows publish reminders (approved but not published)
                    if (event.getStatus() != EventStatus.APPROVED) {
                        return Mono.error(new IllegalStateException(
                                "Can only send publish reminders for approved events. Current status: " + event.getStatus()));
                    }
                    if (event.isPublished()) {
                        return Mono.error(new IllegalStateException("Event is already published"));
                    }

                    // Update the event with reminder tracking
                    event.setUpdatedAt(LocalDateTime.now());

                    // TODO: Publish a notification event to send actual reminder
                    // For now, we just log and return the event
                    log.info("Publish reminder sent for event: {} to organizer: {}", eventId, event.getOrganizerId());

                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> log.info("Publish reminder processed for event: {}", eventId));
    }

    /**
     * Reschedule event to a new date/time.
     * Publishes EventRescheduledEvent to open refund window for ticket holders.
     */
    @Transactional
    public Mono<Event> rescheduleEvent(String id, LocalDateTime newDateTime, String reason) {
        return eventRepository.findById(id)
                .flatMap(event -> {
                    LocalDateTime originalDateTime = event.getEventDateTime();
                    event.setEventDateTime(newDateTime);
                    event.setUpdatedAt(LocalDateTime.now());
                    return eventRepository.save(event)
                            .map(saved -> new Object[]{saved, originalDateTime});
                })
                .map(result -> {
                    Event event = (Event) ((Object[]) result)[0];
                    LocalDateTime original = (LocalDateTime) ((Object[]) result)[1];

                    log.info("Event rescheduled: {} from {} to {}", id, original, event.getEventDateTime());
                    EventRescheduledEvent domainEvent = new EventRescheduledEvent(
                            event.getId(),
                            event.getOrganizerId(),
                            event.getTitle(),
                            original,
                            event.getEventDateTime(),
                            original,
                            event.getEndDateTime(),
                            reason,
                            event.getSoldTickets()
                    );
                    eventPublisher.publishEvent(domainEvent);
                    log.info("Published EventRescheduledEvent for event: {}", id);
                    return event;
                });
    }

    @Override
    @Transactional
    public Mono<Event> rejectEvent(String id, String reason) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                .flatMap(event -> {
                    // STATE VALIDATION: Only PENDING_APPROVAL events can be rejected
                    if (event.getStatus() != EventStatus.PENDING_APPROVAL) {
                        return Mono.error(new InvalidEventStateException(
                                id,
                                event.getStatus().name(),
                                EventStatus.PENDING_APPROVAL.name()
                        ));
                    }
                    event.setStatus(EventStatus.REJECTED);
                    event.setRejectedAt(LocalDateTime.now());
                    event.setRejectionReason(reason);
                    event.setUpdatedAt(LocalDateTime.now());
                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> {
                    log.info("Event rejected: {}, reason: {}", id, reason);
                    // Publish domain event for rejection notification
                    EventRejectedEvent domainEvent = new EventRejectedEvent(
                            event.getId(),
                            event.getOrganizerId(),
                            event.getOrganizationId(),
                            event.getTitle(),
                            event.getRejectedBy() != null ? event.getRejectedBy() : "SYSTEM",
                            reason
                    );
                    eventPublisher.publishEvent(domainEvent);
                    log.info("Published EventRejectedEvent for event: {}", id);
                });
    }

    @Override
    public Mono<Event> updateSoldTickets(String id, int soldCount) {
        return eventRepository.findById(id)
                .flatMap(event -> {
                    event.setSoldTickets(event.getSoldTickets() + soldCount);
                    event.setAvailableTickets(event.getTotalCapacity() - event.getSoldTickets());
                    event.setUpdatedAt(LocalDateTime.now());
                    return eventRepository.save(event);
                });
    }

    /**
     * Soft delete an event.
     *
     * <p>Instead of physically deleting, sets isDeleted=true with audit trail.
     * Validates that no tickets have been sold before allowing deletion.</p>
     *
     * OWASP A09:2021 Compliance: Maintains audit trail for security logging.
     *
     * @param id Event ID to delete
     * @param deletedBy User ID who is deleting
     * @param reason Reason for deletion
     * @return Empty Mono on success
     */
    @Override
    @Transactional
    public Mono<Void> deleteEvent(String id) {
        return deleteEventWithReason(id, null, "Deleted by user");
    }

    /**
     * Soft delete an event with reason and audit trail.
     */
    @Override
    @Transactional
    public Mono<Void> deleteEventWithReason(String id, String deletedBy, String reason) {
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Event not found: " + id)))
                .flatMap(event -> {
                    // BUSINESS RULE: Cannot delete events that have sold tickets
                    if (event.getSoldTickets() > 0) {
                        return Mono.error(new IllegalStateException(
                                String.format("Cannot delete event %s with %d sold tickets. Cancel the event instead.",
                                        id, event.getSoldTickets())
                        ));
                    }

                    // BUSINESS RULE: Cannot delete already deleted events
                    if (event.isDeleted()) {
                        return Mono.error(new IllegalStateException("Event is already deleted: " + id));
                    }

                    // Soft delete with audit trail
                    event.setDeleted(true);
                    event.setDeletedAt(LocalDateTime.now());
                    event.setDeletedBy(deletedBy);
                    event.setDeletionReason(reason);
                    event.setActive(false);
                    event.setUpdatedAt(LocalDateTime.now());

                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> {
                    log.info("Event soft deleted: {}", id);
                    // Publish domain event for cross-service cleanup
                    EventDeletedEvent domainEvent = new EventDeletedEvent(
                            event.getId(),
                            event.getOrganizerId(),
                            event.getOrganizationId(),
                            event.getTitle(),
                            event.getDeletedBy(),
                            event.getDeletionReason(),
                            event.getSoldTickets()
                    );
                    eventPublisher.publishEvent(domainEvent);
                    log.info("Published EventDeletedEvent for event: {}", id);
                })
                .then();
    }

    // ==========================================
    // Flux-based Queries (for resolver pagination helpers)
    // ==========================================

    @Override
    public Flux<Event> findAllEvents() {
        return eventRepository.findAll();
    }

    @Override
    public Flux<Event> findPublishedEvents() {
        return eventRepository.findByPublishedTrueAndIsActiveTrue();
    }

    @Override
    public Flux<Event> searchEvents(String query) {
        return eventRepository.searchEvents(query);
    }

    @Override
    public Flux<Event> findUpcomingEvents() {
        return eventRepository.findByEventDateTimeAfterAndPublishedTrueAndIsActiveTrue(LocalDateTime.now());
    }

    @Override
    public Flux<Event> findEventsByCategory(String categoryId) {
        return eventRepository.findByCategoryIdAndPublishedTrueAndIsActiveTrue(categoryId);
    }

    @Override
    public Flux<Event> findEventsByCity(String city) {
        return eventRepository.findByCityAndPublishedTrueAndIsActiveTrue(city);
    }

    @Override
    public Flux<Event> findEventsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        return eventRepository.findByEventDateTimeBetweenAndPublishedTrueAndIsActiveTrue(startDate, endDate);
    }

    @Override
    public Flux<Event> findEventsByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        return eventRepository.findByPriceRangeAndPublishedTrueAndIsActiveTrue(minPrice, maxPrice);
    }

    @Override
    public Flux<Event> findFeaturedEvents() {
        return eventRepository.findByFeaturedTrueAndPublishedTrueAndIsActiveTrue();
    }

    @Override
    public Flux<Event> findFreeEvents() {
        return eventRepository.findByIsFreeEventTrueAndPublishedTrueAndIsActiveTrue();
    }

    @Override
    public Flux<Event> findEventsByOrganizer(String organizerId) {
        return eventRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Flux<Event> findEventsByStatus(EventStatus status) {
        return eventRepository.findByStatus(status);
    }

    @Override
    public Flux<Event> findDraftEventsByOrganizer(String organizerId) {
        return eventRepository.findByOrganizerIdAndStatus(organizerId, EventStatus.DRAFT);
    }

    @Override
    public Flux<Event> findPendingApprovalEvents() {
        return eventRepository.findByStatus(EventStatus.PENDING_APPROVAL);
    }

    @Override
    public Flux<Event> findOverdueApprovalEvents() {
        return eventRepository.findOverdueApprovalEvents(LocalDateTime.now());
    }

    @Override
    public Flux<Event> findApprovedNotPublishedEvents() {
        return eventRepository.findApprovedNotPublishedEvents();
    }

    @Override
    public Flux<Event> findCancelledEvents() {
        return eventRepository.findByStatus(EventStatus.CANCELLED);
    }

    @Override
    public Flux<Event> findCompletedEvents() {
        return eventRepository.findByStatus(EventStatus.COMPLETED);
    }

    // ==========================================
    // Count Operations
    // ==========================================

    @Override
    public Mono<Long> countAll() {
        return eventRepository.count();
    }

    @Override
    public Mono<Long> countByOrganizer(String organizerId) {
        return eventRepository.countByOrganizerId(organizerId);
    }

    @Override
    public Mono<Long> countByCategory(String categoryId) {
        return eventRepository.countByCategoryId(categoryId);
    }

    @Override
    public Mono<Long> countByCity(String city) {
        return eventRepository.countByCity(city);
    }

    @Override
    public Mono<Long> countByStatus(EventStatus status) {
        return eventRepository.countByStatus(status);
    }

    // ==========================================
    // Cursor-based Pagination Methods
    // ==========================================

    @Override
    public Mono<EventConnection> findPublishedEventsCursor(CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Event> eventsFlux;
        if (afterId != null) {
            eventsFlux = eventRepository.findPublishedEventsAfterCursor(afterId, pageable);
        } else {
            eventsFlux = eventRepository.findByPublishedTrueAndIsActiveTrueOrderByIdAsc(pageable);
        }

        return buildConnection(eventsFlux, limit, afterId != null);
    }

    @Override
    public Mono<EventConnection> searchEventsCursor(String query, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Event> eventsFlux;
        if (afterId != null) {
            eventsFlux = eventRepository.searchEventsAfterCursor(query, afterId, pageable);
        } else {
            eventsFlux = eventRepository.searchEventsFirstPage(query, pageable);
        }

        return buildConnection(eventsFlux, limit, afterId != null);
    }

    @Override
    public Mono<EventConnection> findUpcomingEventsCursor(CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);
        LocalDateTime now = LocalDateTime.now();

        Flux<Event> eventsFlux;
        if (afterId != null) {
            eventsFlux = eventRepository.findUpcomingAfterCursor(now, afterId, pageable);
        } else {
            eventsFlux = eventRepository.findByEventDateTimeAfterAndPublishedTrueAndIsActiveTrueOrderByEventDateTimeAsc(now, pageable);
        }

        return buildConnection(eventsFlux, limit, afterId != null);
    }

    @Override
    public Mono<EventConnection> findEventsByCategoryCursor(String category, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Event> eventsFlux;
        if (afterId != null) {
            eventsFlux = eventRepository.findByCategoryAfterCursor(category, afterId, pageable);
        } else {
            eventsFlux = eventRepository.findByCategoryIdAndPublishedTrueAndIsActiveTrueOrderByIdAsc(category, pageable);
        }

        return buildConnection(eventsFlux, limit, afterId != null);
    }

    @Override
    public Mono<EventConnection> findEventsByCityCursor(String city, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Event> eventsFlux;
        if (afterId != null) {
            eventsFlux = eventRepository.findByCityAfterCursor(city, afterId, pageable);
        } else {
            eventsFlux = eventRepository.findByCityFirstPage(city, pageable);
        }

        return buildConnection(eventsFlux, limit, afterId != null);
    }

    @Override
    public Mono<EventConnection> findEventsByDateRangeCursor(
            LocalDateTime startDate, LocalDateTime endDate, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Event> eventsFlux;
        if (afterId != null) {
            eventsFlux = eventRepository.findByDateRangeAfterCursor(startDate, endDate, afterId, pageable);
        } else {
            eventsFlux = eventRepository.findByEventDateTimeBetweenAndPublishedTrueAndIsActiveTrueOrderByEventDateTimeAsc(
                    startDate, endDate, pageable);
        }

        return buildConnection(eventsFlux, limit, afterId != null);
    }

    @Override
    public Mono<EventConnection> findFeaturedEventsCursor(CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Event> eventsFlux;
        if (afterId != null) {
            eventsFlux = eventRepository.findFeaturedAfterCursor(afterId, pageable);
        } else {
            eventsFlux = eventRepository.findByFeaturedTrueAndPublishedTrueAndIsActiveTrueOrderByIdAsc(pageable);
        }

        return buildConnection(eventsFlux, limit, afterId != null);
    }

    @Override
    public Mono<EventConnection> findEventsByOrganizerCursor(String organizerId, CursorPaginationInput pagination) {
        int limit = pagination.getLimit();
        String afterId = CursorUtils.decodeCursor(pagination.getAfter());
        Pageable pageable = PageRequest.of(0, limit + 1);

        Flux<Event> eventsFlux;
        if (afterId != null) {
            eventsFlux = eventRepository.findByOrganizerAfterCursor(organizerId, afterId, pageable);
        } else {
            eventsFlux = eventRepository.findByOrganizerIdOrderByCreatedAtDesc(organizerId, pageable);
        }

        return buildConnection(eventsFlux, limit, afterId != null);
    }

    // ==========================================
    // Admin Pagination Methods (Dashboard Tables)
    // ==========================================

    @Override
    public Mono<PagedResult<Event>> findEventsAdmin(PageableInput pageable) {
        Pageable springPageable = pageable.toPageable();

        return Mono.zip(
                eventRepository.findAllBy(springPageable).collectList(),
                eventRepository.count()
        ).map(tuple -> PagedResult.of(
                tuple.getT1(),
                springPageable.getPageNumber(),
                springPageable.getPageSize(),
                tuple.getT2()
        ));
    }

    @Override
    public Mono<PagedResult<Event>> findEventsByStatusAdmin(EventStatus status, PageableInput pageable) {
        Pageable springPageable = pageable.toPageable();

        return Mono.zip(
                eventRepository.findByStatus(status, springPageable).collectList(),
                eventRepository.countByStatus(status)
        ).map(tuple -> PagedResult.of(
                tuple.getT1(),
                springPageable.getPageNumber(),
                springPageable.getPageSize(),
                tuple.getT2()
        ));
    }

    @Override
    public Mono<PagedResult<Event>> findDraftEventsAdmin(String organizerId, PageableInput pageable) {
        Pageable springPageable = pageable.toPageable();

        return Mono.zip(
                eventRepository.findDraftEventsByOrganizer(organizerId, springPageable).collectList(),
                eventRepository.countDraftEventsByOrganizer(organizerId)
        ).map(tuple -> PagedResult.of(
                tuple.getT1(),
                springPageable.getPageNumber(),
                springPageable.getPageSize(),
                tuple.getT2()
        ));
    }

    @Override
    public Mono<PagedResult<Event>> findPendingApprovalEventsAdmin(PageableInput pageable) {
        Pageable springPageable = pageable.toPageable();

        return Mono.zip(
                eventRepository.findPendingApprovalEvents(springPageable).collectList(),
                eventRepository.countByStatus(EventStatus.PENDING_APPROVAL)
        ).map(tuple -> PagedResult.of(
                tuple.getT1(),
                springPageable.getPageNumber(),
                springPageable.getPageSize(),
                tuple.getT2()
        ));
    }

    @Override
    public Mono<PagedResult<Event>> findOverdueApprovalEventsAdmin(PageableInput pageable) {
        Pageable springPageable = pageable.toPageable();
        LocalDateTime now = LocalDateTime.now();

        return Mono.zip(
                eventRepository.findOverdueApprovalEvents(now, springPageable).collectList(),
                eventRepository.countOverdueApprovalEvents(now)
        ).map(tuple -> PagedResult.of(
                tuple.getT1(),
                springPageable.getPageNumber(),
                springPageable.getPageSize(),
                tuple.getT2()
        ));
    }

    @Override
    public Mono<PagedResult<Event>> findApprovedNotPublishedEventsAdmin(PageableInput pageable) {
        Pageable springPageable = pageable.toPageable();

        return Mono.zip(
                eventRepository.findApprovedNotPublishedEvents(springPageable).collectList(),
                eventRepository.countApprovedNotPublished()
        ).map(tuple -> PagedResult.of(
                tuple.getT1(),
                springPageable.getPageNumber(),
                springPageable.getPageSize(),
                tuple.getT2()
        ));
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    /**
     * Build EventConnection from Flux with pagination metadata.
     * Fetches limit+1 items to determine hasNextPage.
     */
    private Mono<EventConnection> buildConnection(Flux<Event> eventsFlux, int limit, boolean hasPreviousPage) {
        return eventsFlux.collectList().map(events -> {
            boolean hasNextPage = events.size() > limit;

            // Remove extra item used for hasNextPage check
            List<Event> pageEvents = hasNextPage
                    ? events.subList(0, limit)
                    : events;

            // Convert to edges
            List<EventEdge> edges = pageEvents.stream()
                    .map(EventEdge::from)
                    .toList();

            // Build page info
            PageInfo pageInfo = PageInfo.builder()
                    .hasNextPage(hasNextPage)
                    .hasPreviousPage(hasPreviousPage)
                    .startCursor(edges.isEmpty() ? null : edges.get(0).getCursor())
                    .endCursor(edges.isEmpty() ? null : edges.get(edges.size() - 1).getCursor())
                    .build();

            return EventConnection.builder()
                    .edges(edges)
                    .pageInfo(pageInfo)
                    .build();
        });
    }
}
