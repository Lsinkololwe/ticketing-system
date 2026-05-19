package com.pml.booking.repository;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.dto.RevenueResult;
import com.pml.booking.repository.dto.SpentResult;
import com.pml.shared.constants.TicketStatus;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * ============================================================================
 * TICKET REPOSITORY
 * ============================================================================
 *
 * Reactive MongoDB repository for Ticket entities.
 *
 * This repository supports GraphQL Federation by providing efficient queries
 * for entity resolution and extended field computation.
 *
 * KEY METHODS FOR FEDERATION:
 * - findByEventId: For Event.tickets extension
 * - findByBuyerId: For User.purchasedTickets extension
 * - countByEventIdAndStatusIn: For Event.ticketsSold extension
 * - calculateRevenueByEventId: For Event.revenue extension
 *
 * ============================================================================
 */
@Repository
public interface TicketRepository extends ReactiveMongoRepository<Ticket, String> {

    // ========================================================================
    // BASIC LOOKUPS
    // ========================================================================

    Mono<Ticket> findByTicketNumber(String ticketNumber);

    Flux<Ticket> findByEventId(String eventId);

    Flux<Ticket> findByBuyerId(String buyerId);

    Flux<Ticket> findByCorrelationId(String correlationId);

    Mono<Boolean> existsByTicketNumber(String ticketNumber);

    // ========================================================================
    // STATUS-BASED QUERIES
    // ========================================================================

    Flux<Ticket> findByBuyerIdAndStatus(String buyerId, TicketStatus status);

    Flux<Ticket> findByEventIdAndStatus(String eventId, TicketStatus status);

    Flux<Ticket> findByStatus(TicketStatus status);

    /**
     * Find tickets by organizer ID.
     * Used for Organizer's ticket management dashboard.
     */
    Flux<Ticket> findByOrganizerId(String organizerId);

    /**
     * Find tickets by event ID with status in a set of statuses.
     * Used for filtering sold/active tickets.
     */
    Flux<Ticket> findByEventIdAndStatusIn(String eventId, Collection<TicketStatus> statuses);

    /**
     * Find tickets by buyer ID with status in a set of statuses.
     * Used for active ticket counts.
     */
    Flux<Ticket> findByBuyerIdAndStatusIn(String buyerId, Collection<TicketStatus> statuses);

    // ========================================================================
    // COUNT QUERIES
    // ========================================================================

    Mono<Long> countByEventId(String eventId);

    Mono<Long> countByBuyerId(String buyerId);

    Mono<Long> countByEventIdAndStatus(String eventId, TicketStatus status);

    /**
     * Count tickets for an event with status in a set of statuses.
     * Used for Event.ticketsSold federation extension.
     */
    Mono<Long> countByEventIdAndStatusIn(String eventId, Collection<TicketStatus> statuses);

    /**
     * Count tickets for a buyer with status in a set of statuses.
     * Used for User.activeTicketCount federation extension.
     */
    Mono<Long> countByBuyerIdAndStatusIn(String buyerId, Collection<TicketStatus> statuses);

    // ========================================================================
    // AGGREGATION QUERIES (For Federation Extended Fields)
    // ========================================================================

    /**
     * Calculate total revenue for an event.
     * Sums the price field for all tickets in SOLD statuses.
     *
     * Used for: Event.revenue federation extension
     *
     * This aggregation:
     * 1. Matches tickets for the given event ID
     * 2. Filters to only sold statuses (PURCHASED, CONFIRMED, VALIDATED, USED)
     * 3. Sums the price field
     *
     * NOTE: Returns a wrapper DTO (RevenueResult) instead of raw BigDecimal
     * to avoid Java 21+ module encapsulation issues with BigDecimal reflection.
     */
    @Aggregation(pipeline = {
            "{ $match: { eventId: ?0, status: { $in: ['PURCHASED', 'CONFIRMED', 'VALIDATED', 'USED'] } } }",
            "{ $group: { _id: null, totalRevenue: { $sum: { $toDecimal: '$price' } } } }",
            "{ $project: { _id: 0, totalRevenue: 1 } }"
    })
    Mono<RevenueResult> calculateRevenueByEventId(String eventId);

    /**
     * Calculate total amount spent by a buyer.
     * Sums the price field for all tickets in non-refunded statuses.
     *
     * Used for: User.totalSpent federation extension
     *
     * NOTE: Returns a wrapper DTO (SpentResult) instead of raw BigDecimal
     * to avoid Java 21+ module encapsulation issues with BigDecimal reflection.
     */
    @Aggregation(pipeline = {
            "{ $match: { buyerId: ?0, status: { $in: ['PURCHASED', 'CONFIRMED', 'VALIDATED', 'USED'] } } }",
            "{ $group: { _id: null, totalSpent: { $sum: { $toDecimal: '$price' } } } }",
            "{ $project: { _id: 0, totalSpent: 1 } }"
    })
    Mono<SpentResult> calculateTotalSpentByBuyerId(String buyerId);
}
