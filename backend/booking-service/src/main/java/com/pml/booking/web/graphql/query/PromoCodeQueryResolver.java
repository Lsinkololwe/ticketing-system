package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.PromoCode;
import com.pml.booking.service.PromoCodeService;
import com.pml.booking.web.graphql.dto.PromoCodeValidation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

/**
 * GraphQL Query Resolver for Promo Codes
 *
 * Business Intent: Handles promo code lookups and validation during checkout.
 *
 * Query Mappings (from schema):
 * - promoCode(id): Get promo code by ID (organizer/admin)
 * - promoCodeByCode(code): Get promo code by code string (organizer/admin)
 * - validatePromoCode(code, eventId, amount): Validate during checkout (mobile)
 * - eventPromoCodes(eventId): List promo codes for an event (organizer/admin)
 * - organizerPromoCodes(organizerId): List promo codes by organizer (organizer/admin)
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PromoCodeQueryResolver {

    private final PromoCodeService promoCodeService;

    /**
     * Get promo code by ID.
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<PromoCode> promoCode(@InputArgument String id) {
        log.debug("GraphQL query: promoCode({})", id);
        return promoCodeService.findByCode(id); // Will be updated if we add findById
    }

    /**
     * Get promo code by code string.
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZER')")
    public Mono<PromoCode> promoCodeByCode(@InputArgument String code) {
        log.debug("GraphQL query: promoCodeByCode({})", code);
        return promoCodeService.findByCode(code);
    }

    /**
     * Validate promo code during checkout.
     * Mobile clients use this to check if a code is valid before payment.
     */
    @DgsQuery
    public Mono<PromoCodeValidation> validatePromoCode(
            @InputArgument String code,
            @InputArgument String eventId,
            @InputArgument BigDecimal amount
    ) {
        log.debug("GraphQL query: validatePromoCode({}, {}, {})", code, eventId, amount);

        return promoCodeService.validatePromoCode(code, eventId, amount, List.of())
                .map(promoCode -> PromoCodeValidation.builder()
                        .valid(true)
                        .promoCode(promoCode)
                        .discountAmount(promoCode.calculateDiscount(amount))
                        .errorMessage(null)
                        .build())
                .onErrorResume(e -> {
                    log.warn("Promo code validation failed: {}", e.getMessage());
                    return Mono.just(PromoCodeValidation.builder()
                            .valid(false)
                            .promoCode(null)
                            .discountAmount(null)
                            .errorMessage(e.getMessage())
                            .build());
                });
    }

    /**
     * List all promo codes for an event.
     *
     * <p>OWASP A01:2021 Compliance: Uses EventSecurityService to validate
     * that the requesting user is the event organizer or has admin role.</p>
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Flux<PromoCode> eventPromoCodes(@InputArgument String eventId) {
        log.debug("GraphQL query: eventPromoCodes({})", eventId);
        return promoCodeService.findByEventId(eventId);
    }

    /**
     * List all promo codes created by an organizer.
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService to validate
     * that the requesting user is either the organizer or a team member with access.</p>
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN') or @organizationSecurityService.isOrganizerOrTeamMember(#organizerId, authentication)")
    public Flux<PromoCode> organizerPromoCodes(@InputArgument String organizerId) {
        log.debug("GraphQL query: organizerPromoCodes({})", organizerId);
        return promoCodeService.findByOrganizerId(organizerId);
    }
}
