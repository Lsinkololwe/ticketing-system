package com.pml.booking.repository;

import com.pml.booking.domain.model.CommissionRecord;
import com.pml.booking.domain.model.CommissionRecord.CommissionStatus;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository for CommissionRecord entities.
 *
 * Provides reactive access to commission records stored in MongoDB.
 * Supports the Two-Stage Commission Model (PENDING → EARNED).
 */
@Repository
public interface CommissionRecordRepository extends ReactiveMongoRepository<CommissionRecord, String> {

    /**
     * Find commission by ticket ID (unique).
     */
    Mono<CommissionRecord> findByTicketId(String ticketId);

    /**
     * Find all commissions for an event.
     */
    Flux<CommissionRecord> findByEventId(String eventId);

    /**
     * Find commissions by event and status.
     */
    Flux<CommissionRecord> findByEventIdAndStatus(String eventId, CommissionStatus status);

    /**
     * Find all commissions for an organizer.
     */
    Flux<CommissionRecord> findByOrganizerId(String organizerId);

    /**
     * Find commissions by organizer and status.
     */
    Flux<CommissionRecord> findByOrganizerIdAndStatus(String organizerId, CommissionStatus status);

    /**
     * Find all commissions by status.
     */
    Flux<CommissionRecord> findByStatus(CommissionStatus status);

    /**
     * Count commissions by event and status.
     */
    Mono<Long> countByEventIdAndStatus(String eventId, CommissionStatus status);

    /**
     * Check if commission exists for a ticket.
     */
    Mono<Boolean> existsByTicketId(String ticketId);
}
