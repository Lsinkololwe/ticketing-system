package com.pml.catalog.repository;

import com.pml.catalog.domain.model.Event;
import com.pml.shared.constants.EventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Event Repository with cursor-based pagination support
 */
@Repository
public interface EventRepository extends ReactiveMongoRepository<Event, String> {

    // ==========================================
    // Cursor-based Pagination Methods
    // ==========================================

    // --- Published Events ---

    /**
     * Published events - first page (no cursor)
     */
    Flux<Event> findByPublishedTrueAndIsActiveTrueOrderByIdAsc(Pageable pageable);

    /**
     * Published events - after cursor
     */
    @Query("{ 'published': true, 'isActive': true, '_id': { $gt: ?0 } }")
    Flux<Event> findPublishedEventsAfterCursor(String afterId, Pageable pageable);

    // --- Search Events ---

    /**
     * Search events - first page (no cursor)
     */
    @Query("{ 'published': true, 'isActive': true, $or: [ { 'title': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } }, { 'tags': { $regex: ?0, $options: 'i' } } ] }")
    Flux<Event> searchEventsFirstPage(String query, Pageable pageable);

    /**
     * Search events - after cursor
     */
    @Query("{ 'published': true, 'isActive': true, '_id': { $gt: ?1 }, $or: [ { 'title': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } }, { 'tags': { $regex: ?0, $options: 'i' } } ] }")
    Flux<Event> searchEventsAfterCursor(String query, String afterId, Pageable pageable);

    // --- Events by Category ---

    /**
     * Events by category - first page (no cursor)
     */
    Flux<Event> findByCategoryIdAndPublishedTrueAndIsActiveTrueOrderByIdAsc(String categoryId, Pageable pageable);

    /**
     * Events by category - after cursor
     */
    @Query("{ 'categoryId': ?0, 'published': true, 'isActive': true, '_id': { $gt: ?1 } }")
    Flux<Event> findByCategoryAfterCursor(String categoryId, String afterId, Pageable pageable);

    // --- Events by City ---

    /**
     * Events by city - first page (no cursor)
     */
    @Query("{ 'city': { $regex: ?0, $options: 'i' }, 'published': true, 'isActive': true }")
    Flux<Event> findByCityFirstPage(String city, Pageable pageable);

    /**
     * Events by city - after cursor
     */
    @Query("{ 'city': { $regex: ?0, $options: 'i' }, 'published': true, 'isActive': true, '_id': { $gt: ?1 } }")
    Flux<Event> findByCityAfterCursor(String city, String afterId, Pageable pageable);

    // --- Upcoming Events ---

    /**
     * Upcoming events - first page (no cursor)
     */
    Flux<Event> findByEventDateTimeAfterAndPublishedTrueAndIsActiveTrueOrderByEventDateTimeAsc(
            LocalDateTime now, Pageable pageable);

    /**
     * Upcoming events - after cursor
     */
    @Query("{ 'eventDateTime': { $gt: ?0 }, 'published': true, 'isActive': true, '_id': { $gt: ?1 } }")
    Flux<Event> findUpcomingAfterCursor(LocalDateTime now, String afterId, Pageable pageable);

    // --- Events by Date Range ---

    /**
     * Events by date range - first page (no cursor)
     */
    Flux<Event> findByEventDateTimeBetweenAndPublishedTrueAndIsActiveTrueOrderByEventDateTimeAsc(
            LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Events by date range - after cursor
     */
    @Query("{ 'eventDateTime': { $gte: ?0, $lte: ?1 }, 'published': true, 'isActive': true, '_id': { $gt: ?2 } }")
    Flux<Event> findByDateRangeAfterCursor(LocalDateTime start, LocalDateTime end, String afterId, Pageable pageable);

    // --- Featured Events ---

    /**
     * Featured events - first page (no cursor)
     */
    Flux<Event> findByFeaturedTrueAndPublishedTrueAndIsActiveTrueOrderByIdAsc(Pageable pageable);

    /**
     * Featured events - after cursor
     */
    @Query("{ 'featured': true, 'published': true, 'isActive': true, '_id': { $gt: ?0 } }")
    Flux<Event> findFeaturedAfterCursor(String afterId, Pageable pageable);

    // --- Events by Organizer (for admin) ---

    /**
     * Events by organizer - first page (no cursor)
     */
    Flux<Event> findByOrganizerIdOrderByCreatedAtDesc(String organizerId, Pageable pageable);

    /**
     * Events by organizer - after cursor
     */
    @Query("{ 'organizerId': ?0, '_id': { $gt: ?1 } }")
    Flux<Event> findByOrganizerAfterCursor(String organizerId, String afterId, Pageable pageable);

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
    Flux<Event> findOverdueApprovalEvents(LocalDateTime now, Pageable pageable);

    /**
     * Approved but not published events - admin pagination
     */
    @Query("{ 'status': 'APPROVED', 'published': false }")
    Flux<Event> findApprovedNotPublishedEvents(Pageable pageable);

    // Admin count queries
    Mono<Long> countByStatus(EventStatus status);

    @Query(value = "{ 'status': 'PENDING_APPROVAL', 'approvalDeadline': { $lt: ?0 } }", count = true)
    Mono<Long> countOverdueApprovalEvents(LocalDateTime now);

    @Query(value = "{ 'status': 'APPROVED', 'published': false }", count = true)
    Mono<Long> countApprovedNotPublished();

    @Query(value = "{ 'organizerId': ?0, 'status': 'DRAFT' }", count = true)
    Mono<Long> countDraftEventsByOrganizer(String organizerId);

    // ==========================================
    // Flux-based Queries (for service layer)
    // ==========================================

    Flux<Event> findByPublishedTrueAndIsActiveTrue();

    @Query("{ 'published': true, 'isActive': true, $or: [ { 'title': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } } ] }")
    Flux<Event> searchEvents(String query);

    Flux<Event> findByEventDateTimeAfterAndPublishedTrueAndIsActiveTrue(LocalDateTime now);

    Flux<Event> findByCategoryIdAndPublishedTrueAndIsActiveTrue(String categoryId);

    @Query("{ 'city': { $regex: ?0, $options: 'i' }, 'published': true, 'isActive': true }")
    Flux<Event> findByCityAndPublishedTrueAndIsActiveTrue(String city);

    Flux<Event> findByEventDateTimeBetweenAndPublishedTrueAndIsActiveTrue(LocalDateTime start, LocalDateTime end);

    @Query("{ 'minPrice': { $gte: ?0 }, 'maxPrice': { $lte: ?1 }, 'published': true, 'isActive': true }")
    Flux<Event> findByPriceRangeAndPublishedTrueAndIsActiveTrue(java.math.BigDecimal minPrice, java.math.BigDecimal maxPrice);

    Flux<Event> findByFeaturedTrueAndPublishedTrueAndIsActiveTrue();

    Flux<Event> findByIsFreeEventTrueAndPublishedTrueAndIsActiveTrue();

    Flux<Event> findByOrganizerId(String organizerId);

    Flux<Event> findByStatus(EventStatus status);

    Flux<Event> findByOrganizerIdAndStatus(String organizerId, EventStatus status);

    @Query("{ 'status': 'PENDING_APPROVAL', 'approvalDeadline': { $lt: ?0 } }")
    Flux<Event> findOverdueApprovalEvents(LocalDateTime now);

    @Query("{ 'status': 'APPROVED', 'published': false }")
    Flux<Event> findApprovedNotPublishedEvents();

    // Additional count queries
    Mono<Long> countByOrganizerId(String organizerId);

    Mono<Long> countByCategoryId(String categoryId);

    @Query(value = "{ 'city': { $regex: ?0, $options: 'i' } }", count = true)
    Mono<Long> countByCity(String city);
}
