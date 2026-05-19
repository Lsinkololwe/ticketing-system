package com.pml.booking.domain.model;

import com.pml.shared.constants.TicketCategory;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.constants.TicketPaymentStatus;
import com.pml.shared.constants.TicketRefundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Ticket Model
 */
@Document(collection = "tickets")
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
    @Indexed(unique = true)
    private String ticketNumber;

    @NotBlank(message = "Event ID is required")
    @Indexed
    private String eventId;

    /**
     * Reservation ID that created this ticket.
     * Links ticket to its original reservation for inventory tracking.
     */
    @Indexed
    private String reservationId;

    /**
     * Ticket tier ID from catalog-service.
     * Used for inventory management (reserve/commit/restore).
     */
    @Indexed
    private String ticketTierId;

    @NotBlank(message = "Buyer ID is required")
    @Indexed
    private String buyerId;

    /**
     * Organizer ID - denormalized from Event for efficient querying.
     * Populated when ticket is created based on the event's organizer.
     */
    @Indexed
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
    @Indexed
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

    private String qrCode;
    private String barcode;

    private LocalDateTime purchaseDate;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private LocalDateTime validatedAt;
    private String validatedBy;  // ID of user/device that validated the ticket
    private LocalDateTime usedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private LocalDateTime refundedAt;
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

    // Ticket transfer fields
    private String originalBuyerId;
    private String transferredToId;
    private LocalDateTime transferredAt;
    private String transferReason;

    // Commission information
    private BigDecimal commissionRate;
    private BigDecimal commissionAmount;
    private BigDecimal netAmount;

    private PaymentInfo paymentInfo;
    private RefundInfo refundInfo;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

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

    public boolean isValid() {
        LocalDateTime now = LocalDateTime.now();
        return (status == TicketStatus.PURCHASED || status == TicketStatus.VALIDATED) &&
               (validFrom == null || now.isAfter(validFrom)) &&
               (validUntil == null || now.isBefore(validUntil));
    }

    public boolean isExpired() {
        return validUntil != null && LocalDateTime.now().isAfter(validUntil);
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
        private LocalDateTime paymentDate;
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
        private LocalDateTime refundDate;
        private String processedBy;
        private String transactionId;

        public String getFormattedRefundAmount() {
            return "K " + refundAmount.toString();
        }
    }
}
