package com.pml.booking.repository;

import com.pml.booking.domain.enums.ReservationStatus;
import com.pml.booking.domain.model.TicketReservation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

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
    Flux<TicketReservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime time);

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
            String eventId, ReservationStatus status, LocalDateTime time);

    /**
     * Find all active reservations for a user.
     */
    default Flux<TicketReservation> findActiveByUserId(String userId) {
        return findByUserIdAndStatus(userId, ReservationStatus.ACTIVE);
    }

    /**
     * Find expired active reservations.
     */
    default Flux<TicketReservation> findExpiredReservations() {
        return findByStatusAndExpiresAtBefore(ReservationStatus.ACTIVE, LocalDateTime.now());
    }
}
