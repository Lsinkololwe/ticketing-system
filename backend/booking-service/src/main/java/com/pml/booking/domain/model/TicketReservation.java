package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.ReservationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
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
@Document(collection = "ticket_reservations")
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

    private LocalDateTime convertedAt;

    private String promoCode;

    @NotNull(message = "Total amount is required")
    @Positive(message = "Total amount must be positive")
    private BigDecimal totalAmount;

    private BigDecimal discountAmount;

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
        return status == ReservationStatus.ACTIVE && !isExpired();
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
