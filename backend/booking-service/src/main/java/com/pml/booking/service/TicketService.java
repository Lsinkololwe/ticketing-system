package com.pml.booking.service;

import com.pml.booking.domain.model.Ticket;
import com.pml.booking.repository.dto.RevenueResult;
import com.pml.booking.repository.dto.SpentResult;
import com.pml.booking.web.graphql.dto.AdminTicketUpdateInput;
import com.pml.booking.web.graphql.dto.BulkOperationResponse;
import com.pml.shared.constants.TicketStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;

/**
 * Ticket Service Interface
 */
public interface TicketService {

    Mono<Ticket> findById(String id);

    Mono<Ticket> findByTicketNumber(String ticketNumber);

    Flux<Ticket> findAll();

    Flux<Ticket> findByEventId(String eventId);

    Flux<Ticket> findByBuyerId(String buyerId);

    Flux<Ticket> findByBuyerIdAndStatus(String buyerId, TicketStatus status);

    Flux<Ticket> findByEventIdAndStatus(String eventId, TicketStatus status);

    Flux<Ticket> findByStatus(TicketStatus status);

    Flux<Ticket> findByOrganizerId(String organizerId);

    Mono<Ticket> createTicket(Ticket ticket);

    Mono<Ticket> updateTicket(String id, Ticket ticket);

    Mono<Ticket> validateTicket(String ticketNumber);

    Mono<Ticket> useTicket(String ticketNumber);

    Mono<Ticket> refundTicket(String ticketNumber, String reason, String processedBy);

    Mono<Ticket> cancelTicket(String ticketNumber, String reason, String processedBy);

    Mono<Ticket> transferTicket(String ticketId, String newBuyerId, String reason);

    Mono<Void> deleteTicket(String id);

    Mono<Long> countByEventId(String eventId);

    Mono<Long> countByBuyerId(String buyerId);

    // ========================================================================
    // FEDERATION EXTENSION METHODS
    // ========================================================================

    /**
     * Count tickets by event ID and statuses.
     * Used by EventExtensionResolver for Event.ticketsSold field.
     *
     * @param eventId  The event ID
     * @param statuses Collection of ticket statuses to filter by
     * @return Count of matching tickets
     */
    Mono<Long> countByEventIdAndStatusIn(String eventId, Collection<TicketStatus> statuses);

    /**
     * Count tickets by buyer ID and statuses.
     * Used by UserExtensionResolver for User.activeTicketCount field.
     *
     * @param buyerId  The buyer/user ID
     * @param statuses Collection of ticket statuses to filter by
     * @return Count of matching tickets
     */
    Mono<Long> countByBuyerIdAndStatusIn(String buyerId, Collection<TicketStatus> statuses);

    /**
     * Calculate total revenue for an event.
     * Used by EventExtensionResolver for Event.revenue field.
     *
     * @param eventId The event ID
     * @return Revenue calculation result
     */
    Mono<RevenueResult> calculateRevenueByEventId(String eventId);

    /**
     * Calculate total amount spent by a buyer.
     * Used by UserExtensionResolver for User.totalSpent field.
     *
     * @param buyerId The buyer/user ID
     * @return Spent calculation result
     */
    Mono<SpentResult> calculateTotalSpentByBuyerId(String buyerId);

    // ========================================================================
    // ADMIN TICKET OPERATIONS
    // ========================================================================

    /**
     * Admin update ticket details (buyer info, category, notes).
     *
     * @param ticketId The ticket ID to update
     * @param input    The update input with new values
     * @return Updated ticket
     */
    Mono<Ticket> adminUpdateTicket(String ticketId, AdminTicketUpdateInput input);

    /**
     * Regenerate QR code for a ticket.
     * Used when QR code is compromised or needs refresh.
     *
     * @param ticketId The ticket ID
     * @return Updated ticket with new QR code
     */
    Mono<Ticket> regenerateTicketQrCode(String ticketId);

    /**
     * Bulk cancel tickets for admin operations.
     * Used for event cancellation or fraud prevention.
     *
     * @param ticketIds   List of ticket IDs to cancel
     * @param reason      Cancellation reason
     * @param processedBy Admin user ID
     * @return Bulk operation response with counts and errors
     */
    Mono<BulkOperationResponse> bulkCancelTickets(List<String> ticketIds, String reason, String processedBy);
}
