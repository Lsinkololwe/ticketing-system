package com.pml.booking.web.graphql.dto;

import com.pml.booking.domain.enums.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Input for creating or updating a promo code.
 */
public record CreatePromoCodeInput(
    @NotBlank(message = "Promo code is required")
    String code,

    @NotBlank(message = "Event ID is required")
    String eventId,

    @NotNull(message = "Discount type is required")
    DiscountType discountType,

    @NotNull(message = "Discount value is required")
    @Positive(message = "Discount value must be positive")
    BigDecimal discountValue,

    Integer maxUses,

    @NotNull(message = "Valid from date is required")
    LocalDateTime validFrom,

    @NotNull(message = "Valid until date is required")
    LocalDateTime validUntil,

    BigDecimal minPurchaseAmount,

    BigDecimal maxDiscountAmount,

    List<String> applicableTiers
) {}
