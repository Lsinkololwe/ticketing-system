package com.pml.booking.domain.model;

import com.pml.booking.persistence.BookingCollections;

import com.pml.shared.constants.TicketCategory;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.constants.TicketPaymentStatus;
import com.pml.shared.constants.TicketRefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Ticket Model
 */
@Document(collection = BookingCollections.TICKETS)
@TypeAlias("tickets")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Ticket {

    @Id
    private String id;

    /**
     * Optimistic locking version field.
     * Prevents double-booking and concurrent modification issues.
     * Automatically incremented on each save operation.
     */
    @Version
    private Long version;

    @NotBlank(message = "Ticket number is required")
    private String ticketNumber;

    @NotBlank(message = "Event ID is required")
    private String eventId;

    /**
     * Reservation ID that created this ticket.
     * Links ticket to its original reservation for inventory tracking.
     */
    private String reservationId;

    /**
     * Ticket tier ID from catalog-service.
     * Used for inventory management (reserve/commit/restore).
     */
    private String ticketTierId;

    /** The durable booking this seat belongs to (equal to {@link #reservationId}), and its human-facing number. */
    private String bookingId;
    private String bookingNumber;

    @NotBlank(message = "Buyer ID is required")
    private String buyerId;

    /**
     * Organizer ID - denormalized from Event for efficient querying.
     * Populated when ticket is created based on the event's organizer.
     */
    private String organizerId;

    /**
     * Organization ID - denormalized from Event for multi-tenant operations.
     * Critical for:
     * - Organization-level ticket queries and reports
     * - Consistent authorization checks across services
     * - Financial audit trails by organization
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    private String organizationId;

    @NotBlank(message = "Event title is required")
    private String eventTitle;

    @NotBlank(message = "Event date is required")
    private String eventDate;

    private String eventLocationName;
    private String eventLocationAddress;
    private String eventLocationCity;

    @NotNull(message = "Ticket category is required")
    private TicketCategory ticketCategory;

    private String ticketCategoryCode;
    private String ticketCategoryName;

    @NotNull(message = "Ticket price is required")
    @Positive(message = "Ticket price must be positive")
    private BigDecimal price;

    @Builder.Default
    private String currency = "ZMW";

    @NotNull(message = "Ticket status is required")
    private TicketStatus status;

    /**
     * What {@link #status} MEANS, denormalised from reference data.
     *
     * <p>Same reasoning as {@link PayoutRequest#getStatusSemantic()}: ticket
     * statuses are administrator-configurable, so the code set is open and a
     * fixed enum constraint can no longer hold. Branches and reports read this
     * field, which the collection validator keeps enum-constrained.
     *
     * <p>It is what makes "how many tickets are actually good" answerable for
     * an event once an administrator has added statuses the platform shipped
     * without — the alternative is a hard-coded list that silently omits every
     * status added after it was written.
     *
     * <p>Deliberately not refreshed on read: a ticket refunded last month keeps
     * the meaning that was true when it was refunded.
     */
    private com.pml.shared.constants.WorkflowSemantic statusSemantic;

    private String qrCode;
    private String barcode;

    private Instant purchaseDate;
    private Instant validFrom;
    private Instant validUntil;
    private Instant validatedAt;
    private String validatedBy;  // ID of user/device that validated the ticket
    private Instant usedAt;
    private Instant cancelledAt;
    private String cancellationReason;
    private Instant refundedAt;
    private String refundReason;

    private String buyerName;
    private String buyerEmail;
    private String buyerPhone;

    // Payment processing fields
    private String correlationId;
    private String paymentReference;
    private String paymentUrl;

    private int quantity;
    private Map<String, Object> metadata;

    /**
     * The outstanding transfer holding this seat, or null. While it is set the seat is not scannable,
     * not refundable and cannot be transferred again; claiming, cancelling or expiring the transfer
     * clears it. A marker rather than a status because ticket statuses are a closed, shared vocabulary.
     */
    private String activeTransferId;

    /** How many times this seat has changed hands; bounded by {@code booking.transfer.max-chain}. */
    private int transferCount;

    // Ticket transfer fields
    private String originalBuyerId;
    private String transferredToId;
    private Instant transferredAt;
    private String transferReason;

    // Commission information
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private BigDecimal netAmount;

    private PaymentInfo paymentInfo;
    private RefundInfo refundInfo;

    /** Sum of completed refunds against this seat; the seat is {@code REFUNDED} once it reaches the price. */
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    @Builder.Default
    private boolean isActive = true;

    public static String generateTicketNumber() {
        return "TKT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public String getFormattedPrice() {
        return "K " + price.toString();
    }

    public boolean isValid(Instant now) {
        return status != null && status.isSold() &&
               (validFrom == null || now.isAfter(validFrom)) &&
               (validUntil == null || now.isBefore(validUntil));
    }

    public boolean isExpired(Instant now) {
        return validUntil != null && now.isAfter(validUntil);
    }

    public boolean isPremium() {
        return ticketCategory != null && ticketCategory.isPremium();
    }

    public boolean isPreSale() {
        return ticketCategory != null && ticketCategory.isPreSale();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfo {
        private String paymentId;
        private String paymentMethod;
        private String transactionId;
        private BigDecimal amount;
        @Builder.Default
        private String currency = "ZMW";
        private TicketPaymentStatus status;
        private Instant paymentDate;
        private String providerReference;

        public String getFormattedAmount() {
            return "K " + amount.toString();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundInfo {
        private String refundId;
        private BigDecimal refundAmount;
        private String reason;
        private TicketRefundStatus status;
        private Instant refundDate;
        private String processedBy;
        private String transactionId;

        public String getFormattedRefundAmount() {
            return "K " + refundAmount.toString();
        }
    }
}
