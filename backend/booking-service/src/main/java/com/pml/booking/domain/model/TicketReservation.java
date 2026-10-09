package com.pml.booking.domain.model;

import com.pml.booking.persistence.BookingCollections;

import com.pml.shared.constants.ReservationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ticket Reservation Model
 *
 * Business Intent: Holds a temporary reservation of tickets for a user.
 * Reservations expire after a configured TTL (default 10 minutes), releasing
 * inventory back to the pool. This prevents cart abandonment from blocking
 * other buyers.
 */
@Document(collection = BookingCollections.RESERVATIONS)
@TypeAlias("reservations")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class TicketReservation {

    @Id
    private String id;

    @NotBlank(message = "Event ID is required")
    private String eventId;

    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Organizer ID - denormalized from Event for efficient querying.
     * Populated when reservation is created based on the event's organizer.
     */
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
    private String organizationId;

    @NotNull(message = "Reservation items are required")
    private List<ReservationItem> items;

    @NotNull(message = "Reservation status is required")
    private ReservationStatus status;

    @NotNull(message = "Expiration time is required")
    private Instant expiresAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    /**
     * The payment intent this reservation is waiting on.
     *
     * <p>Null until an intent exists, and that null is meaningful: the
     * purchase workflow reads exactly this pair — the reservation's own status and the
     * intent's — to decide what happened to a purchase the process died in the
     * middle of. A {@code HELD} reservation with no intent past its expiry was
     * never charged and is safe to release; one with a {@code SUCCEEDED} intent
     * took the buyer's money and must be confirmed.
     */
    private String paymentIntentId;

    /**
     * The client's key for this purchase, so a retry produces one charge rather than two.
     *
     * <p>On the purchase rather than on the payment intent because the retry that
     * needs it is the one where the client never saw a response — it does not know
     * an intent id to deduplicate against, only the key it generated.
     */
    private String idempotencyKey;

    private String promoCode;

    /** {@code BK-2026-00001234}; set when the booking is opened, so tickets issued later can carry it. */
    private String bookingNumber;

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

    private Instant confirmedAt;

    private Instant releasedAt;

    private Instant failedAt;

    private String failureReason;

    /**
     * Optimistic lock. Present because two writers genuinely race here — the
     * expiry timer and an arriving payment callback contend for the same
     * reservation by design.
     */
    @Version
    private Long version;

    /**
     * Check if reservation is expired.
     */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /**
     * Check if reservation is still active.
     */
    public boolean isActive(Instant now) {
        return status == ReservationStatus.HELD && !isExpired(now);
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
