package com.pml.catalog.repository;

import com.pml.catalog.domain.model.Event;
import com.pml.shared.constants.EventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

import java.time.Instant;

/**
 * Event Repository with cursor-based pagination support
 */
@Repository
public interface EventRepository extends ReactiveMongoRepository<Event, String> {

    // --- Events by City ---

    /**
     * Events by city - first page (no cursor)
     */
    @Query("{ 'city': { $regex: ?0, $options: 'i' }, 'published': true, 'isActive': true }")
    Flux<Event> findByCityFirstPage(String city, Pageable pageable);

    // ==========================================
    // Admin Pagination Methods (for dashboard tables)
    // ==========================================

    /**
     * All events - admin pagination
     */
    Flux<Event> findAllBy(Pageable pageable);

    /**
     * Events by status - admin pagination
     */
    Flux<Event> findByStatus(EventStatus status, Pageable pageable);

    /**
     * Draft events by organizer - admin pagination
     */
    @Query("{ 'organizerId': ?0, 'status': 'DRAFT' }")
    Flux<Event> findDraftEventsByOrganizer(String organizerId, Pageable pageable);

    /**
     * Pending approval events - admin pagination
     */
    @Query("{ 'status': 'PENDING_APPROVAL' }")
    Flux<Event> findPendingApprovalEvents(Pageable pageable);

    /**
     * Overdue approval events - admin pagination
     */
    @Query("{ 'status': 'PENDING_APPROVAL', 'approvalDeadline': { $lt: ?0 } }")
    Flux<Event> findOverdueApprovalEvents(Instant now, Pageable pageable);

    /**
     * Approved but not published events - admin pagination
     */
    @Query("{ 'status': 'APPROVED', 'published': false }")
    Flux<Event> findApprovedNotPublishedEvents(Pageable pageable);

    // Admin count queries
    Mono<Long> countByStatus(EventStatus status);

    // ==========================================
    // Flux-based Queries (for service layer)
    // ==========================================

    @Query("{ 'published': true, 'isActive': true, $or: [ { 'title': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } } ] }")
    Flux<Event> searchEvents(String query);

    Flux<Event> findByCategoryIdAndPublishedTrueAndIsActiveTrue(String categoryId);

    @Query("{ 'city': { $regex: ?0, $options: 'i' }, 'published': true, 'isActive': true }")
    Flux<Event> findByCityAndPublishedTrueAndIsActiveTrue(String city);

    Flux<Event> findByOrganizerId(String organizerId);

    Flux<Event> findByStatus(EventStatus status);

    Flux<Event> findByOrganizerIdAndStatus(String organizerId, EventStatus status);

    @Query("{ 'status': 'PENDING_APPROVAL', 'approvalDeadline': { $lt: ?0 } }")
    Flux<Event> findOverdueApprovalEvents(Instant now);

    @Query("{ 'status': 'APPROVED', 'published': false }")
    Flux<Event> findApprovedNotPublishedEvents();

    // Additional count queries
    Mono<Long> countByOrganizerId(String organizerId);

    Mono<Long> countByCategoryId(String categoryId);

    @Query(value = "{ 'city': { $regex: ?0, $options: 'i' } }", count = true)
    Mono<Long> countByCity(String city);

    /**
     * Find an event by id, restricted to the caller's organizations.
     *
     * <p>The boundary is the {@code IN} clause. An event owned by another
     * organization does not come back, so "not yours" and "no such id" are the same
     * empty {@code Mono} — which is what keeps {@code EVENT_UNKNOWN} from becoming
     * an enumeration oracle over real event ids.
     *
     * @param id              event id, caller-supplied and therefore untrusted
     * @param organizationIds the caller's active memberships; empty matches nothing
     */
    Mono<Event> findByIdAndOrganizationIdIn(String id, Collection<String> organizationIds);

    /**
     * Find an event by id, restricted to what an unauthenticated caller may see.
     *
     * <p>Every list query on the public discovery surface ends
     * {@code PublishedTrueAndIsActiveTrue} — {@code findByPublishedTrueAndIsActiveTrue},
     * {@code findByCategoryIdAndPublishedTrueAndIsActiveTrue} and the rest. A single-event
     * lookup without the same filter would let anyone holding an id read a draft, a rejected
     * event or a soft-deleted one. This is the by-id form of the filter the lists apply.
     *
     * <p>{@code isActive} rather than {@code isDeleted} is deliberate and matches the
     * existing finders: {@code deleteEventWithReason} sets both, and the discovery
     * surface has always keyed on the active flag.
     */
    Mono<Event> findByIdAndPublishedTrueAndIsActiveTrue(String id);
}