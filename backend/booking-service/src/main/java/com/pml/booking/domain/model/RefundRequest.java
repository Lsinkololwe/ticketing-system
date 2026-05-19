package com.pml.booking.domain.model;

import com.pml.shared.constants.RefundRequestStatus;
import com.pml.shared.constants.RefundRequestType;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Refund Request Model
 *
 * Tracks refund requests for ticket purchases.
 * Integrates with pawaPay for mobile money refunds.
 *
 * Refund Flow:
 * 1. PENDING - Request created
 * 2. APPROVED - Request approved (manual review for some cases)
 * 3. PROCESSING - Refund initiated with pawaPay
 * 4. COMPLETED - Refund successful
 * 5. FAILED - Refund failed (retryable)
 * 6. REJECTED - Request rejected (manual review)
 */
@Document(collection = "refund_requests")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequest {

    @Id
    private String id;

    @NotBlank(message = "Ticket ID is required")
    @Indexed
    private String ticketId;

    @NotBlank(message = "Ticket number is required")
    @Indexed
    private String ticketNumber;

    @NotBlank(message = "Event ID is required")
    @Indexed
    private String eventId;

    @NotBlank(message = "Organizer ID is required")
    @Indexed
    private String organizerId;

    /**
     * Organization ID for multi-tenant refund tracking.
     * Critical for:
     * - Organization-level refund reports
     * - Financial compliance by organization
     * - Consolidated refund metrics
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    @Indexed
    private String organizationId;

    @NotBlank(message = "Buyer ID is required")
    @Indexed
    private String buyerId;

    @NotBlank(message = "Request ID is required")
    @Indexed(unique = true)
    private String requestId;

    @NotNull(message = "Refund amount is required")
    @Positive(message = "Refund amount must be positive")
    private BigDecimal refundAmount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "Status is required")
    private RefundRequestStatus status;

    @NotNull(message = "Request type is required")
    private RefundRequestType requestType;

    @NotBlank(message = "Reason is required")
    private String requestReason;

    private String additionalNotes;
    private String supportingDocuments;

    // pawaPay refund integration
    @Indexed
    private String pawaPayRefundId;
    private String pawaPayDepositId;
    private String providerTransactionId;

    // Automatic refund flag (event cancellation, etc.)
    @Builder.Default
    private boolean isAutomatic = false;

    // Approval workflow
    private String requestedBy;
    private Instant requestedAt;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComments;
    private String rejectionReason;

    // Processing details
    private String paymentReference;
    private String refundTransactionId;
    private LocalDateTime processedAt;
    private String processedBy;

    // Financial details
    private BigDecimal originalTicketPrice;
    private BigDecimal processingFee;
    private BigDecimal netRefundAmount;
    private String originalPaymentMethod;
    private String originalPaymentTransactionId;

    private List<RefundRequestHistory> history;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    /**
     * Version for optimistic locking.
     *
     * <p>Prevents concurrent modifications to refund requests.
     * Critical for financial data integrity when multiple processes
     * (e.g., admin approval, gateway callback) may modify the same request.</p>
     *
     * <p>If two transactions try to modify the same RefundRequest simultaneously,
     * one will fail with OptimisticLockingFailureException.</p>
     */
    @Version
    private Long version;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundRequestHistory {
        private String action;
        private String performedBy;
        private LocalDateTime performedAt;
        private String comments;
        private String previousStatus;
        private String newStatus;
        private Map<String, Object> metadata;
    }
}
