package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.DiscountType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Promo Code Model
 *
 * Business Intent: Allows organizers to create discount codes for their events.
 * Supports percentage-based and fixed-amount discounts with usage limits,
 * validity periods, and tier restrictions.
 */
@Document(collection = "promo_codes")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PromoCode {

    @Id
    private String id;

    @NotBlank(message = "Promo code is required")
    @Indexed(unique = true)
    private String code;

    @NotBlank(message = "Event ID is required")
    @Indexed
    private String eventId;

    @NotBlank(message = "Organizer ID is required")
    @Indexed
    private String organizerId;

    /**
     * Organization ID for multi-tenant promo code management.
     * Enables organizations to manage promo codes across multiple events
     * and organizers within the same organization.
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    @Indexed
    private String organizationId;

    @NotNull(message = "Discount type is required")
    private DiscountType discountType;

    @NotNull(message = "Discount value is required")
    @Positive(message = "Discount value must be positive")
    private BigDecimal discountValue;

    private Integer maxUses;

    @PositiveOrZero(message = "Current uses cannot be negative")
    @Builder.Default
    private int currentUses = 0;

    @NotNull(message = "Valid from date is required")
    private LocalDateTime validFrom;

    @NotNull(message = "Valid until date is required")
    private LocalDateTime validUntil;

    private BigDecimal minPurchaseAmount;

    private BigDecimal maxDiscountAmount;

    private List<String> applicableTiers;

    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Check if promo code is currently valid.
     */
    public boolean isCurrentlyValid() {
        LocalDateTime now = LocalDateTime.now();
        return isActive &&
               now.isAfter(validFrom) &&
               now.isBefore(validUntil) &&
               (maxUses == null || currentUses < maxUses);
    }

    /**
     * Check if promo code has reached usage limit.
     */
    public boolean hasReachedUsageLimit() {
        return maxUses != null && currentUses >= maxUses;
    }

    /**
     * Check if promo code is valid for a specific tier.
     */
    public boolean isValidForTier(String tierId) {
        return applicableTiers == null || applicableTiers.isEmpty() || applicableTiers.contains(tierId);
    }

    /**
     * Check if purchase amount meets minimum requirement.
     */
    public boolean meetsMinimumPurchase(BigDecimal amount) {
        return minPurchaseAmount == null || amount.compareTo(minPurchaseAmount) >= 0;
    }

    /**
     * Calculate discount amount for a given total.
     */
    public BigDecimal calculateDiscount(BigDecimal total) {
        BigDecimal discount;

        if (discountType == DiscountType.PERCENTAGE) {
            discount = total.multiply(discountValue).divide(new BigDecimal("100"));
        } else {
            discount = discountValue;
        }

        // Apply maximum discount cap if set
        if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
            discount = maxDiscountAmount;
        }

        // Ensure discount doesn't exceed total
        if (discount.compareTo(total) > 0) {
            discount = total;
        }

        return discount;
    }
}
