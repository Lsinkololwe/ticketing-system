package com.pml.booking.service.impl;

import com.pml.booking.web.graphql.dto.CreatePromoCodeInput;
import com.pml.booking.domain.model.PromoCode;
import com.pml.booking.repository.PromoCodeRepository;
import com.pml.booking.service.PromoCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Promo Code Service Implementation
 *
 * Business Intent: Manages discount codes with validation and usage tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoCodeServiceImpl implements PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    @Override
    public Mono<PromoCode> validatePromoCode(
        String code,
        String eventId,
        BigDecimal totalAmount,
        List<String> tierIds
    ) {
        log.info("Validating promo code: {} for event: {}", code, eventId);

        return promoCodeRepository.findByCodeIgnoreCaseAndEventId(code, eventId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Promo code not found")))
            .flatMap(promoCode -> {
                // Validate promo code is currently valid
                if (!promoCode.isCurrentlyValid()) {
                    if (!promoCode.isActive()) {
                        return Mono.error(new IllegalStateException("Promo code is inactive"));
                    }
                    if (promoCode.hasReachedUsageLimit()) {
                        return Mono.error(new IllegalStateException("Promo code usage limit reached"));
                    }
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isBefore(promoCode.getValidFrom())) {
                        return Mono.error(new IllegalStateException("Promo code not yet valid"));
                    }
                    if (now.isAfter(promoCode.getValidUntil())) {
                        return Mono.error(new IllegalStateException("Promo code has expired"));
                    }
                }

                // Validate minimum purchase amount
                if (!promoCode.meetsMinimumPurchase(totalAmount)) {
                    return Mono.error(new IllegalStateException(
                        "Purchase amount does not meet minimum requirement"
                    ));
                }

                // Validate applicable tiers
                boolean hasValidTier = tierIds.stream()
                    .anyMatch(promoCode::isValidForTier);

                if (!hasValidTier) {
                    return Mono.error(new IllegalStateException(
                        "Promo code is not valid for selected ticket tiers"
                    ));
                }

                return Mono.just(promoCode);
            });
    }

    @Override
    public Mono<PromoCode> createPromoCode(CreatePromoCodeInput input, String organizerId) {
        log.info("Creating promo code: {} for organizer: {}", input.code(), organizerId);

        // Check if code already exists
        return promoCodeRepository.existsByCodeIgnoreCase(input.code())
            .flatMap(exists -> {
                if (exists) {
                    return Mono.error(new IllegalArgumentException("Promo code already exists"));
                }

                PromoCode promoCode = PromoCode.builder()
                    .code(input.code().toUpperCase())
                    .eventId(input.eventId())
                    .organizerId(organizerId)
                    .discountType(input.discountType())
                    .discountValue(input.discountValue())
                    .maxUses(input.maxUses())
                    .currentUses(0)
                    .validFrom(input.validFrom())
                    .validUntil(input.validUntil())
                    .minPurchaseAmount(input.minPurchaseAmount())
                    .maxDiscountAmount(input.maxDiscountAmount())
                    .applicableTiers(input.applicableTiers())
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .build();

                return promoCodeRepository.save(promoCode);
            });
    }

    @Override
    public Mono<PromoCode> updatePromoCode(String id, CreatePromoCodeInput input) {
        log.info("Updating promo code: {}", id);

        return promoCodeRepository.findById(id)
            .flatMap(existing -> {
                existing.setDiscountType(input.discountType());
                existing.setDiscountValue(input.discountValue());
                existing.setMaxUses(input.maxUses());
                existing.setValidFrom(input.validFrom());
                existing.setValidUntil(input.validUntil());
                existing.setMinPurchaseAmount(input.minPurchaseAmount());
                existing.setMaxDiscountAmount(input.maxDiscountAmount());
                existing.setApplicableTiers(input.applicableTiers());

                return promoCodeRepository.save(existing);
            });
    }

    @Override
    public Mono<PromoCode> activatePromoCode(String id) {
        log.info("Activating promo code: {}", id);

        return promoCodeRepository.findById(id)
            .flatMap(promoCode -> {
                promoCode.setActive(true);
                return promoCodeRepository.save(promoCode);
            });
    }

    @Override
    public Mono<PromoCode> deactivatePromoCode(String id) {
        log.info("Deactivating promo code: {}", id);

        return promoCodeRepository.findById(id)
            .flatMap(promoCode -> {
                promoCode.setActive(false);
                return promoCodeRepository.save(promoCode);
            });
    }

    @Override
    public Mono<PromoCode> incrementUsage(String id) {
        log.debug("Incrementing usage for promo code: {}", id);

        Query query = new Query(Criteria.where("id").is(id));
        Update update = new Update().inc("currentUses", 1);

        return mongoTemplate.findAndModify(query, update, PromoCode.class)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Promo code not found")));
    }

    @Override
    public Mono<PromoCode> findByCode(String code) {
        return promoCodeRepository.findByCodeIgnoreCase(code);
    }

    @Override
    public Flux<PromoCode> findByEventId(String eventId) {
        return promoCodeRepository.findByEventId(eventId);
    }

    @Override
    public Flux<PromoCode> findByOrganizerId(String organizerId) {
        return promoCodeRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Mono<Boolean> deletePromoCode(String id) {
        log.info("Deleting promo code: {}", id);

        return promoCodeRepository.findById(id)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Promo code not found: " + id)))
            .flatMap(promoCode -> {
                // Only allow deletion of inactive or unused promo codes
                if (promoCode.isActive() && promoCode.getCurrentUses() > 0) {
                    return Mono.error(new IllegalStateException(
                        "Cannot delete active promo code with usage. Deactivate it first."));
                }

                return promoCodeRepository.deleteById(id)
                    .thenReturn(true);
            });
    }

    @Override
    public Mono<PromoCode> findById(String id) {
        return promoCodeRepository.findById(id);
    }
}
