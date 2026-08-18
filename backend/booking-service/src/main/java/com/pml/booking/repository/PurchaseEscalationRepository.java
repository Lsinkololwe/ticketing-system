package com.pml.booking.repository;

import com.pml.booking.domain.model.PurchaseEscalation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Purchases awaiting an operator (ET-TKT-001 R7, R8 → ET-ADM-003).
 */
@Repository
public interface PurchaseEscalationRepository extends ReactiveMongoRepository<PurchaseEscalation, String> {

    Mono<PurchaseEscalation> findByReservationId(String reservationId);

    /** The workbench queue: what still needs a human. */
    Flux<PurchaseEscalation> findByResolvedFalse();

    Flux<PurchaseEscalation> findByReasonAndResolvedFalse(PurchaseEscalation.Reason reason);
}
