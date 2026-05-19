package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.web.graphql.dto.CreatePromoCodeInput;
import com.pml.booking.web.graphql.dto.DeleteMutationResponse;
import com.pml.booking.domain.model.PromoCode;
import com.pml.booking.service.PromoCodeService;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * GraphQL Mutation Resolver for Promo Codes
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: All actor IDs (createdBy, etc.)
 *       are extracted from JWT, never from client input</li>
 * </ul>
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class PromoCodeMutationResolver {

    private final PromoCodeService promoCodeService;

    /**
     * Create a new promo code.
     * Schema: createPromoCode(input: CreatePromoCodeInput!): PromoCode!
     *
     * <p>Security: createdBy is extracted from JWT, not from client input.</p>
     */
    @DgsMutation
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public Mono<PromoCode> createPromoCode(
        @InputArgument CreatePromoCodeInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(createdBy -> log.info("GraphQL mutation: createPromoCode for organizer: {}", createdBy))
                .flatMap(createdBy -> promoCodeService.createPromoCode(input, createdBy));
    }

    /**
     * Update an existing promo code.
     * Schema: updatePromoCode(id: ID!, input: UpdatePromoCodeInput!): PromoCode!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public Mono<PromoCode> updatePromoCode(
        @InputArgument String id,
        @InputArgument CreatePromoCodeInput input
    ) {
        log.info("GraphQL mutation: updatePromoCode({})", id);
        return promoCodeService.updatePromoCode(id, input);
    }

    /**
     * Activate a promo code.
     * Schema: activatePromoCode(id: ID!): PromoCode!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public Mono<PromoCode> activatePromoCode(@InputArgument String id) {
        log.info("GraphQL mutation: activatePromoCode({})", id);
        return promoCodeService.activatePromoCode(id);
    }

    /**
     * Deactivate a promo code.
     * Schema: deactivatePromoCode(id: ID!): PromoCode!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public Mono<PromoCode> deactivatePromoCode(@InputArgument String id) {
        log.info("GraphQL mutation: deactivatePromoCode({})", id);
        return promoCodeService.deactivatePromoCode(id);
    }

    /**
     * Delete a promo code.
     * Schema: deletePromoCode(id: ID!): DeleteMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ORGANIZER') or hasRole('ADMIN')")
    public Mono<DeleteMutationResponse> deletePromoCode(@InputArgument String id) {
        log.info("GraphQL mutation: deletePromoCode({})", id);
        return promoCodeService.findById(id)
            .flatMap(promoCode -> promoCodeService.deletePromoCode(id)
                .map(deleted -> DeleteMutationResponse.success(
                    "Promo code deleted successfully",
                    Map.of("code", promoCode.getCode(), "eventId", promoCode.getEventId())
                ))
            )
            .switchIfEmpty(Mono.just(DeleteMutationResponse.error("Promo code not found")))
            .onErrorResume(e -> {
                log.error("Delete promo code failed: {}", e.getMessage());
                return Mono.just(DeleteMutationResponse.error(e.getMessage()));
            });
    }
}
