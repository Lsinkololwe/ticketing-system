package com.pml.booking.service;

import com.pml.booking.web.graphql.dto.CompleteReservationInput;
import com.pml.booking.web.graphql.dto.ReserveTicketsInput;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketReservation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reservation Service Interface
 *
 * Business Intent: Manages temporary ticket reservations with automatic expiration.
 * Prevents cart abandonment from blocking inventory for other buyers.
 */
public interface ReservationService {

    /**
     * Create a new ticket reservation for a user.
     * Validates availability and sets expiration time (default 10 minutes).
     *
     * @param userId User creating the reservation
     * @param input Reservation details
     * @return Created reservation
     */
    Mono<TicketReservation> createReservation(String userId, ReserveTicketsInput input);

    /**
     * Complete a reservation by processing payment and creating tickets.
     * Validates that reservation is not expired.
     *
     * @param reservationId ID of the reservation
     * @param input Payment and completion details
     * @return List of created tickets
     */
    Mono<List<Ticket>> completeReservation(String reservationId, CompleteReservationInput input);

    /**
     * Cancel a reservation and release inventory.
     *
     * @param reservationId ID of the reservation to cancel
     * @return true if cancelled successfully
     */
    Mono<Boolean> cancelReservation(String reservationId);

    /**
     * Extend the expiration time of a reservation.
     *
     * @param reservationId ID of the reservation
     * @param minutes Number of minutes to extend
     * @return Updated reservation
     */
    Mono<TicketReservation> extendReservation(String reservationId, int minutes);

    /**
     * Find a reservation by ID.
     */
    Mono<TicketReservation> findById(String id);

    /**
     * Find all active reservations for a user.
     */
    Flux<TicketReservation> findActiveByUserId(String userId);

    /**
     * Find all reservations for an event.
     */
    Flux<TicketReservation> findByEventId(String eventId);

    /**
     * Find expired reservations for an event since a given time.
     */
    Flux<TicketReservation> findExpiredByEventId(String eventId, LocalDateTime since);

    /**
     * Find all expired reservations since a given time.
     */
    Flux<TicketReservation> findExpiredSince(LocalDateTime since);

    /**
     * Expire all reservations that have passed their expiration time.
     * Called by scheduled task every 30 seconds.
     */
    Mono<Void> expireReservations();
}
