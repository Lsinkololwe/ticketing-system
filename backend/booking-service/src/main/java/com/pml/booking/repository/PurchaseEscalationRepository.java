package com.pml.booking.repository;

import com.pml.booking.domain.model.PurchaseEscalation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Purchases awaiting an operator in transaction recovery.
 */
@Repository
public interface PurchaseEscalationRepository extends ReactiveMongoRepository<PurchaseEscalation, String> {

    Mono<PurchaseEscalation> findByReservationId(String reservationId);
}
