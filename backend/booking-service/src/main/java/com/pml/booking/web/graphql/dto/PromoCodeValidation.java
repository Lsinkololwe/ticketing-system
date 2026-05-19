package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.model.PromoCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Promo Code Validation Result DTO
 *
 * Business Intent: Returns the result of validating a promo code during checkout.
 * Mobile clients use this to display discount information or error messages.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoCodeValidation {

    /** Whether the promo code is valid */
    private boolean valid;

    /** The promo code details (null if invalid) */
    private PromoCode promoCode;

    /** Calculated discount amount (null if invalid) */
    private BigDecimal discountAmount;

    /** Error message explaining why validation failed (null if valid) */
    private String errorMessage;

    /**
     * Factory method for a valid promo code.
     */
    public static PromoCodeValidation valid(PromoCode promoCode, BigDecimal discountAmount) {
        return PromoCodeValidation.builder()
                .valid(true)
                .promoCode(promoCode)
                .discountAmount(discountAmount)
                .build();
    }

    /**
     * Factory method for an invalid promo code.
     */
    public static PromoCodeValidation invalid(String errorMessage) {
        return PromoCodeValidation.builder()
                .valid(false)
                .errorMessage(errorMessage)
                .build();
    }
}
