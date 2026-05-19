package com.pml.booking.service;

import com.pml.booking.web.graphql.dto.CreatePromoCodeInput;
import com.pml.booking.domain.model.PromoCode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

/**
 * Promo Code Service Interface
 *
 * Business Intent: Manages discount codes for events with usage tracking
 * and validation.
 */
public interface PromoCodeService {

    /**
     * Validate a promo code for a specific purchase.
     * Checks code existence, validity period, usage limits, and tier restrictions.
     *
     * @param code Promo code string
     * @param eventId Event ID
     * @param totalAmount Purchase total
     * @param tierIds List of tier IDs in the purchase
     * @return Validated promo code
     */
    Mono<PromoCode> validatePromoCode(String code, String eventId, BigDecimal totalAmount, java.util.List<String> tierIds);

    /**
     * Create a new promo code.
     *
     * @param input Promo code details
     * @param organizerId Organizer creating the code
     * @return Created promo code
     */
    Mono<PromoCode> createPromoCode(CreatePromoCodeInput input, String organizerId);

    /**
     * Update an existing promo code.
     *
     * @param id Promo code ID
     * @param input Updated details
     * @return Updated promo code
     */
    Mono<PromoCode> updatePromoCode(String id, CreatePromoCodeInput input);

    /**
     * Activate a promo code.
     *
     * @param id Promo code ID
     * @return Activated promo code
     */
    Mono<PromoCode> activatePromoCode(String id);

    /**
     * Deactivate a promo code.
     *
     * @param id Promo code ID
     * @return Deactivated promo code
     */
    Mono<PromoCode> deactivatePromoCode(String id);

    /**
     * Increment usage count for a promo code (atomic operation).
     *
     * @param id Promo code ID
     * @return Updated promo code
     */
    Mono<PromoCode> incrementUsage(String id);

    /**
     * Find a promo code by code string.
     */
    Mono<PromoCode> findByCode(String code);

    /**
     * Find all promo codes for an event.
     */
    Flux<PromoCode> findByEventId(String eventId);

    /**
     * Find all promo codes created by an organizer.
     */
    Flux<PromoCode> findByOrganizerId(String organizerId);

    /**
     * Delete a promo code.
     * Only inactive or unused promo codes can be deleted.
     *
     * @param id Promo code ID
     * @return true if deleted successfully
     */
    Mono<Boolean> deletePromoCode(String id);

    /**
     * Find promo code by ID.
     */
    Mono<PromoCode> findById(String id);
}
