package com.pml.catalog.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ticket Tier
 *
 * Represents a pricing tier for event tickets (e.g., VIP, General Admission, Early Bird).
 *
 * Business Intent: Support sophisticated pricing strategies with multiple ticket types,
 * early bird pricing, hidden tiers with access codes, and purchase limits.
 *
 * <h2>Inventory Model</h2>
 * <pre>
 * quantity = availableQuantity + reservedQuantity + soldQuantity
 *
 * AVAILABLE → RESERVED (on reservation) → SOLD (on payment success)
 *                 ↓ (on expiration/cancel)
 *               RESTORED → AVAILABLE
 * </pre>
 */
@Document(collection = "ticket_tiers")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "event_code_idx", def = "{'eventId': 1, 'code': 1}", unique = true)
@CompoundIndex(name = "event_active_sort_idx", def = "{'eventId': 1, 'isActive': 1, 'sortOrder': 1}")
public class TicketTier {

    @Id
    private String id;

    /**
     * Optimistic locking version field.
     * Prevents race conditions during concurrent inventory updates.
     * Automatically incremented on each save operation.
     */
    @Version
    private Long version;

    /**
     * Event ID this tier belongs to
     */
    @Indexed
    private String eventId;

    /**
     * Organization ID for multi-tenant inventory management.
     * Denormalized from Event for efficient querying of organization's inventory.
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    @Indexed
    private String organizationId;

    /**
     * Unique code for this tier (e.g., "VIP", "GA", "EARLY_BIRD")
     */
    @NotBlank(message = "Tier code is required")
    private String code;

    /**
     * Display name (e.g., "VIP Package", "General Admission")
     */
    @NotBlank(message = "Tier name is required")
    private String name;

    /**
     * Description of what this tier includes
     */
    private String description;

    /**
     * Current price
     */
    @NotNull(message = "Price is required")
    @Positive(message = "Price must be positive")
    private BigDecimal price;

    /**
     * Original price (before discounts) - used to show savings
     */
    private BigDecimal originalPrice;

    /**
     * Total quantity available
     */
    @Positive(message = "Quantity must be positive")
    private int quantity;

    /**
     * Currently available quantity (not reserved or sold)
     */
    private int availableQuantity;

    /**
     * Number of tickets currently reserved (held pending payment).
     * Reservations expire after TTL and inventory is released.
     */
    @Builder.Default
    private int reservedQuantity = 0;

    /**
     * Number of tickets sold (payment completed)
     */
    @Builder.Default
    private int soldQuantity = 0;

    /**
     * Maximum tickets per order (null = unlimited)
     */
    private Integer maxPerOrder;

    /**
     * Minimum tickets per order (null = 1)
     */
    private Integer minPerOrder;

    /**
     * List of benefits included in this tier
     */
    private List<String> benefits;

    /**
     * Display order (lower values appear first)
     */
    @Builder.Default
    private int sortOrder = 0;

    /**
     * Whether this tier is currently active
     */
    @Builder.Default
    private boolean isActive = true;

    /**
     * When sales start for this tier (null = immediately)
     */
    private LocalDateTime salesStartAt;

    /**
     * When sales end for this tier (null = until event)
     */
    private LocalDateTime salesEndAt;

    /**
     * Early bird price (special pricing before earlyBirdEndsAt)
     */
    private BigDecimal earlyBirdPrice;

    /**
     * When early bird pricing ends
     */
    private LocalDateTime earlyBirdEndsAt;

    /**
     * Whether this tier is hidden from public listings
     */
    @Builder.Default
    private boolean isHidden = false;

    /**
     * Access code required to purchase this tier (null = no code required)
     */
    private String accessCode;

    /**
     * When this tier was created
     */
    private LocalDateTime createdAt;

    /**
     * When this tier was last updated
     */
    private LocalDateTime updatedAt;

    /**
     * Get the current applicable price (early bird if active, otherwise regular price)
     *
     * @return Current price
     */
    public BigDecimal getCurrentPrice() {
        if (earlyBirdPrice != null && earlyBirdEndsAt != null
                && LocalDateTime.now().isBefore(earlyBirdEndsAt)) {
            return earlyBirdPrice;
        }
        return price;
    }

    /**
     * Check if early bird pricing is currently active
     *
     * @return true if early bird pricing applies
     */
    public boolean isEarlyBirdActive() {
        return earlyBirdPrice != null
                && earlyBirdEndsAt != null
                && LocalDateTime.now().isBefore(earlyBirdEndsAt);
    }

    /**
     * Check if this tier is currently on sale
     *
     * @return true if tier is available for purchase
     */
    public boolean isOnSale() {
        if (!isActive || availableQuantity <= 0) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        boolean afterStart = salesStartAt == null || now.isAfter(salesStartAt);
        boolean beforeEnd = salesEndAt == null || now.isBefore(salesEndAt);

        return afterStart && beforeEnd;
    }

    /**
     * Check if tickets are available
     *
     * @return true if tickets can be purchased
     */
    public boolean hasAvailableTickets() {
        return isActive && availableQuantity > 0;
    }

    /**
     * Get true available quantity (available minus reserved).
     * This is the quantity that can be reserved by new buyers.
     *
     * @return Quantity available for new reservations
     */
    public int getTrueAvailableQuantity() {
        return Math.max(0, availableQuantity - reservedQuantity);
    }

    /**
     * Check if requested quantity is truly available for reservation.
     * Accounts for both active status and reserved inventory.
     *
     * @param requestedQuantity Number of tickets requested
     * @return true if quantity can be reserved
     */
    public boolean hasTrueAvailableQuantity(int requestedQuantity) {
        return isActive && getTrueAvailableQuantity() >= requestedQuantity;
    }

    /**
     * Validate that inventory invariant is maintained.
     * quantity = availableQuantity + soldQuantity
     * availableQuantity >= reservedQuantity
     *
     * @return true if inventory is consistent
     */
    public boolean isInventoryConsistent() {
        return availableQuantity >= reservedQuantity
                && availableQuantity >= 0
                && reservedQuantity >= 0
                && soldQuantity >= 0;
    }

    /**
     * Calculate savings compared to original price
     *
     * @return Savings amount (null if no original price set)
     */
    public BigDecimal getSavings() {
        if (originalPrice == null) {
            return null;
        }
        return originalPrice.subtract(getCurrentPrice());
    }

    /**
     * Calculate discount percentage
     *
     * @return Discount percentage (null if no original price set)
     */
    public Integer getDiscountPercentage() {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal savings = getSavings();
        if (savings == null) {
            return null;
        }
        return savings.multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, BigDecimal.ROUND_HALF_UP)
                .intValue();
    }
}
