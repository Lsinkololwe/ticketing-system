package com.pml.catalog.domain.model;

import com.pml.shared.constants.Money;

import com.pml.catalog.persistence.CatalogCollections;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Version;

import java.math.BigDecimal;
import java.time.Instant;
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
@Document(collection = CatalogCollections.TICKET_TIERS)
@TypeAlias("ticket_tiers")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TicketTier {

    @Id
    private String id;

    /**
     * Every monetary field carries a currency sibling. Launch is ZMW-only
     * and the field still exists: adding a second currency later becomes a data
     * migration rather than an audit of which amounts meant what.
     */
    @Builder.Default
    private String currency = Money.DEFAULT_CURRENCY;

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
    private String eventId;

    /**
     * Organization ID for multi-tenant inventory management.
     * Denormalized from Event for efficient querying of organization's inventory.
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
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
     * The holds and sales applied to this tier, one entry per reservation.
     *
     * <p>Kept in the tier document so that every movement is one conditional atomic update on one
     * document: the entry and the counters change together without a multi-document transaction. A
     * transaction per hold on a shared tier document aborts on write conflicts under on-sale
     * contention. An entry is removed when its hold is released; a committed entry stays, so a
     * replayed commit and a reversal after a commit can both be recognised.</p>
     *
     * <p>Bounded by the number of reservations that hold or bought this tier. At roughly 70 bytes an
     * entry that stays well inside MongoDB's 16 MB document limit for any venue this platform sells.</p>
     */
    @Builder.Default
    private List<InventoryMovement> movements = new java.util.ArrayList<>();

    /** One reservation's seats on this tier. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InventoryMovement {
        /** The reservation holding or having bought these seats. */
        private String reservationId;
        /** Seats held or sold for that reservation. */
        private int quantity;
        /** {@code HELD} or {@code COMMITTED}. */
        private String state;
        /** What booking reported for the sale, so reversing the sale reverses the same money. */
        private BigDecimal grossAmount;
        private BigDecimal commissionAmount;

        public InventoryMovement(String reservationId, int quantity, String state) {
            this(reservationId, quantity, state, null, null);
        }
    }

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
    private Instant salesStartAt;

    /**
     * When sales end for this tier (null = until event)
     */
    private Instant salesEndAt;

    /**
     * Early bird price (special pricing before earlyBirdEndsAt)
     */
    private BigDecimal earlyBirdPrice;

    /**
     * When early bird pricing ends
     */
    private Instant earlyBirdEndsAt;

    /**
     * Whether this tier is hidden from public listings
     */
    @Builder.Default
    private boolean isHidden = false;

    /**
     * Access code required to purchase this tier (null = no code required)
     */
    private String accessCode;

    /** What kind of ticket this is. A tier written before categories existed reads as GENERAL. */
    private com.pml.shared.constants.TicketCategory category;

    /**
     * When this tier was created
     */
    private Instant createdAt;

    /**
     * When this tier was last updated
     */
    private Instant updatedAt;

    /**
     * Get the current applicable price (early bird if active, otherwise regular price)
     *
     * @return Current price
     */
    public BigDecimal getCurrentPrice(Instant now) {
        if (earlyBirdPrice != null && earlyBirdEndsAt != null
                && now.isBefore(earlyBirdEndsAt)) {
            return earlyBirdPrice;
        }
        return price;
    }

    /**
     * Check if early bird pricing is currently active
     *
     * @return true if early bird pricing applies
     */
    public boolean isEarlyBirdActive(Instant now) {
        return earlyBirdPrice != null
                && earlyBirdEndsAt != null
                && now.isBefore(earlyBirdEndsAt);
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
    public BigDecimal getSavings(Instant now) {
        if (originalPrice == null) {
            return null;
        }
        return originalPrice.subtract(getCurrentPrice(now));
    }

    /**
     * Calculate discount percentage
     *
     * @return Discount percentage (null if no original price set)
     */
    public Integer getDiscountPercentage(Instant now) {
        if (originalPrice == null || originalPrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal savings = getSavings(now);
        if (savings == null) {
            return null;
        }
        return savings.multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, java.math.RoundingMode.HALF_UP)
                .intValue();
    }

    /** The tier's category; {@code GENERAL} when none was chosen. */
    public com.pml.shared.constants.TicketCategory getCategory() {
        return category == null ? com.pml.shared.constants.TicketCategory.GENERAL : category;
    }
}
