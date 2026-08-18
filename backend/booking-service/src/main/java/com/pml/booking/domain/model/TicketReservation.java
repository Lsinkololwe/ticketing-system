package com.pml.booking.domain.model;

import com.pml.shared.constants.ReservationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Ticket Reservation Model
 *
 * Business Intent: Holds a temporary reservation of tickets for a user.
 * Reservations expire after a configured TTL (default 10 minutes), releasing
 * inventory back to the pool. This prevents cart abandonment from blocking
 * other buyers.
 */
@Document(collection = "booking_reservations")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TicketReservation {

    @Id
    private String id;

    @NotBlank(message = "Event ID is required")
    @Indexed
    private String eventId;

    @NotBlank(message = "User ID is required")
    @Indexed
    private String userId;

    /**
     * Organizer ID - denormalized from Event for efficient querying.
     * Populated when reservation is created based on the event's organizer.
     */
    @Indexed
    private String organizerId;

    /**
     * Organization ID for multi-tenant reservation tracking.
     * Critical for:
     * - Organization-level reservation analytics
     * - Conversion rate tracking by organization
     * - Inventory hold monitoring
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    @Indexed
    private String organizationId;

    @NotNull(message = "Reservation items are required")
    private List<ReservationItem> items;

    @NotNull(message = "Reservation status is required")
    @Indexed
    private ReservationStatus status;

    @NotNull(message = "Expiration time is required")
    @Indexed
    private LocalDateTime expiresAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * The payment intent this reservation is waiting on.
     *
     * <p>Null until an intent exists, and that null is meaningful: ET-TKT-001 R8's
     * recovery sweep reads exactly this pair — the reservation's own status and the
     * intent's — to decide what happened to a purchase the process died in the
     * middle of. A {@code HELD} reservation with no intent past its expiry was
     * never charged and is safe to release; one with a {@code SUCCEEDED} intent
     * took the buyer's money and must be confirmed.
     */
    @Indexed
    private String paymentIntentId;

    /**
     * The client's key for this purchase (ET-TKT-001 R6).
     *
     * <p>On the purchase rather than on the payment intent because the retry that
     * needs it is the one where the client never saw a response — it does not know
     * an intent id to deduplicate against, only the key it generated.
     */
    @Indexed
    private String idempotencyKey;

    private String promoCode;

    /** Set when the promo code's redemption was consumed, so it can be given back. */
    private String promoCodeId;

    @NotNull(message = "Total amount is required")
    @Positive(message = "Total amount must be positive")
    private BigDecimal totalAmount;

    private BigDecimal subtotal;

    private BigDecimal discountAmount;

    @Builder.Default
    private String currency = "ZMW";

    // ------------------------------------------------------------------
    // Terminal timestamps.
    //
    // One per terminal state rather than a single `resolvedAt` plus the status,
    // because the reports differ: "nobody paid in time" and "the payment failed"
    // are different problems, and a platform that cannot separate them cannot
    // tell a payments outage from a slow checkout.
    // ------------------------------------------------------------------

    private LocalDateTime confirmedAt;

    private LocalDateTime releasedAt;

    private LocalDateTime failedAt;

    private String failureReason;

    /**
     * Optimistic lock. Present because two writers genuinely race here — the
     * expiry sweep and an arriving payment callback contend for the same
     * reservation by design.
     */
    @Version
    private Long version;

    /**
     * Check if reservation is expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Check if reservation is still active.
     */
    public boolean isActive() {
        return status == ReservationStatus.HELD && !isExpired();
    }

    /**
     * Calculate net amount after discount.
     */
    public BigDecimal getNetAmount() {
        if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) == 0) {
            return totalAmount;
        }
        return totalAmount.subtract(discountAmount);
    }

    /**
     * Reservation Item
     *
     * Represents a single tier selection within a reservation.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationItem {
        @NotBlank(message = "Ticket tier ID is required")
        private String ticketTierId;

        @NotBlank(message = "Tier name is required")
        private String tierName;

        @Positive(message = "Quantity must be positive")
        private int quantity;

        @NotNull(message = "Unit price is required")
        @Positive(message = "Unit price must be positive")
        private BigDecimal unitPrice;

        @NotNull(message = "Subtotal is required")
        @Positive(message = "Subtotal must be positive")
        private BigDecimal subtotal;
    }
}
