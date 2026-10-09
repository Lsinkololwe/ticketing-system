package com.pml.booking.domain.model;

import com.pml.shared.constants.Money;

import com.pml.booking.persistence.BookingCollections;

import com.pml.booking.domain.enums.DiscountType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Promo Code Model
 *
 * Business Intent: Allows organizers to create discount codes for their events.
 * Supports percentage-based and fixed-amount discounts with usage limits,
 * validity periods, and tier restrictions.
 */
@Document(collection = BookingCollections.PROMO_CODES)
@TypeAlias("promo_codes")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PromoCode {

    @Id
    private String id;

    /**
     * Every monetary field carries a currency sibling. Launch is ZMW-only and the field still
     * exists: adding a second currency later becomes a data
     * migration rather than an audit of which amounts meant what.
     */
    @Builder.Default
    private String currency = Money.DEFAULT_CURRENCY;

    /**
     * {@code booking_promo_codes} is versioned, and {@code currentUses} is why.
     *
     * <p>Redemption is read-check-increment against {@code maxUses}. Two checkouts that read the
     * same {@code currentUses} both see room under the limit, both write back the same
     * incremented value, and one redemption disappears — so a code capped at 100 settles more
     * than 100 discounts, and the overspend is discovered in the commission reconciliation
     * rather than at the point of sale. {@code @Version} turns the second write into an
     * {@code OptimisticLockingFailureException} the caller can retry.</p>
     */
    @Version
    private Long version;

    @NotBlank(message = "Promo code is required")
    private String code;

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "Organizer ID is required")
    private String organizerId;

    /**
     * Organization ID for multi-tenant promo code management.
     * Enables organizations to manage promo codes across multiple events
     * and organizers within the same organization.
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
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
    private Instant validFrom;

    @NotNull(message = "Valid until date is required")
    private Instant validUntil;

    private BigDecimal minPurchaseAmount;

    private BigDecimal maxDiscountAmount;

    private List<String> applicableTiers;

    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * Check if promo code is currently valid.
     */
    public boolean isCurrentlyValid(Instant now) {
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
     *
     * <p>Rounded to the platform's scale <b>once</b>, at the end, after the cap and the total
     * have been applied. A percentage of an arbitrary total does not land on a
     * ngwee — 15% of K33.33 is K4.9995 — and an unrounded figure returned from here is stored
     * as {@code Decimal128} at whatever scale it happens to have, then summed into a commission
     * and a ledger line that no longer agree to the cent.</p>
     */
    public BigDecimal calculateDiscount(BigDecimal total) {
        BigDecimal discount;

        if (discountType == DiscountType.PERCENTAGE) {
            discount = Money.percentageOf(total, discountValue);
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

        return Money.round(discount);
    }
}
