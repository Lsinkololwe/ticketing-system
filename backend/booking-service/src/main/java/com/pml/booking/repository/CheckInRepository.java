package com.pml.booking.repository;

import com.pml.booking.domain.enums.ValidationMethod;
import com.pml.booking.domain.model.CheckIn;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for accepted check-ins.
 *
 * <p>Every read here is scoped by {@code organizerId} as well as
 * {@code eventId}. Scoping by event alone would make the gate log addressable
 * by anyone who knows an event id, and attendance is commercially sensitive.
 *
 * @see CheckIn
 */
@Repository
public interface CheckInRepository extends ReactiveMongoRepository<CheckIn, String> {

    /**
     * The winning check-in for a ticket, if it has been admitted.
     *
     * <p>The unique index is what guarantees there is at most one; this lookup
     * exists so a refused duplicate can report <em>when</em> the ticket was
     * first admitted rather than just that it was.
     */
    Mono<CheckIn> findByTicketId(String ticketId);

    Mono<CheckIn> findByScanId(String scanId);

    Mono<Long> countByEventIdAndOrganizerId(String eventId, String organizerId);

    /** Backs the "recent check-ins" panel; the caller bounds the limit. */
    Flux<CheckIn> findByEventIdAndOrganizerIdOrderByRecordedAtDesc(
            String eventId, String organizerId, Pageable pageable);

    Mono<Long> countByEventIdAndOrganizerIdAndMethod(
            String eventId, String organizerId, ValidationMethod method);
}
