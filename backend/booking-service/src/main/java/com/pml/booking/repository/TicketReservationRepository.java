package com.pml.booking.repository;

import com.pml.shared.constants.ReservationStatus;
import com.pml.booking.domain.model.TicketReservation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Ticket Reservation Repository
 *
 * Provides reactive MongoDB operations for ticket reservations.
 */
@Repository
public interface TicketReservationRepository extends ReactiveMongoRepository<TicketReservation, String> {

    /**
     * Find all reservations for a user with a specific status.
     */
    Flux<TicketReservation> findByUserIdAndStatus(String userId, ReservationStatus status);

    /**
     * Find all reservations for an event with a specific status.
     */
    Flux<TicketReservation> findByEventIdAndStatus(String eventId, ReservationStatus status);

    /**
     * Find all reservations with a specific status that expired before a given time.
     * Used by the expiration scheduler to clean up expired reservations.
     */
    Flux<TicketReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, Instant time);

    /**
     * Count active reservations for a specific event and tier.
     * Used to determine how many tickets are currently held in reservations.
     */
    Mono<Long> countByEventIdAndStatus(String eventId, ReservationStatus status);

    /**
     * Find all reservations for an event.
     */
    Flux<TicketReservation> findByEventId(String eventId);

    /**
     * Find reservations for an event with a specific status that expired before a given time.
     */
    Flux<TicketReservation> findByEventIdAndStatusAndExpiresAtBefore(
            String eventId, ReservationStatus status, Instant time);

    /** Every reservation in a given state — what boot adoption reads to resume purchase workflows. */
    Flux<TicketReservation> findByStatus(ReservationStatus status);

    /**
     * The reservation a given purchase attempt produced.
     *
     * <p>The lookup that makes a retry safe: a client re-sending a request whose
     * response it never received gets the hold it already has, rather than a
     * second block of inventory.
     */
    Mono<TicketReservation> findByIdempotencyKey(String idempotencyKey);

    /**
     * Find all active reservations for a user.
     */
    default Flux<TicketReservation> findActiveByUserId(String userId) {
        return findByUserIdAndStatus(userId, ReservationStatus.HELD);
    }
}
