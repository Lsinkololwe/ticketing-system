package com.pml.booking.repository;

import com.pml.booking.domain.enums.CheckInConflictStatus;
import com.pml.booking.domain.model.CheckInConflict;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive repository for refused scans.
 *
 * <p>Scoped by {@code organizerId} for the same reason as
 * {@link CheckInRepository} — a conflict row names a ticket and a gate, and
 * neither belongs to a stranger.
 *
 * @see CheckInConflict
 */
@Repository
public interface CheckInConflictRepository extends ReactiveMongoRepository<CheckInConflict, String> {

    Flux<CheckInConflict> findByEventIdAndOrganizerIdOrderByDetectedAtDesc(
            String eventId, String organizerId, Pageable pageable);

    Mono<Long> countByEventIdAndOrganizerId(String eventId, String organizerId);

    Mono<Long> countByEventIdAndOrganizerIdAndStatus(
            String eventId, String organizerId, CheckInConflictStatus status);
}
