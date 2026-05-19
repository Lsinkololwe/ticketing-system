package com.pml.booking.repository;

import com.pml.booking.domain.model.PromoCode;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Promo Code Repository
 *
 * Provides reactive MongoDB operations for promo codes.
 */
@Repository
public interface PromoCodeRepository extends ReactiveMongoRepository<PromoCode, String> {

    /**
     * Find a promo code by its code (case-insensitive).
     */
    Mono<PromoCode> findByCodeIgnoreCase(String code);

    /**
     * Find all promo codes for a specific event.
     */
    Flux<PromoCode> findByEventId(String eventId);

    /**
     * Find all promo codes created by an organizer.
     */
    Flux<PromoCode> findByOrganizerId(String organizerId);

    /**
     * Find all active promo codes for an event.
     */
    Flux<PromoCode> findByEventIdAndIsActiveTrue(String eventId);

    /**
     * Check if a promo code exists for a given code.
     */
    Mono<Boolean> existsByCodeIgnoreCase(String code);

    /**
     * Find a promo code by code and event ID (for validation).
     */
    Mono<PromoCode> findByCodeIgnoreCaseAndEventId(String code, String eventId);
}
