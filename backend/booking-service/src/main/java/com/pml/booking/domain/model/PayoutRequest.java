package com.pml.booking.domain.model;

import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.constants.PayoutRequestStatus;
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
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Payout Request Model
 *
 * Tracks organizer payout requests through approval and processing workflow.
 * Integrates with pawaPay for mobile money payouts and bank transfers.
 *
 * Payout Flow:
 * 1. PENDING - Request created by organizer
 * 2. PENDING_FINANCE_APPROVAL - For large amounts (optional)
 * 3. APPROVED - Request approved by admin/finance
 * 4. PROCESSING - Payout initiated with payment provider
 * 5. COMPLETED - Payout successful
 * 6. FAILED - Payout failed (retryable)
 * 7. REJECTED - Request rejected (manual review)
 * 8. CANCELLED - Request cancelled by organizer
 */
@Document(collection = "booking_payout_requests")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "organizer_status_idx", def = "{'organizerId': 1, 'status': 1}"),
    @CompoundIndex(name = "event_status_idx", def = "{'eventId': 1, 'status': 1}")
})
public class PayoutRequest {

    @Id
    private String id;

    @NotBlank(message = "Request ID is required")
    @Indexed(unique = true)
    private String requestId;

    /**
     * Client-supplied idempotency key.
     *
     * <p>UNIQUE and SPARSE: unique so a duplicate create is rejected by the
     * database rather than by application logic that a concurrent request can
     * race past; sparse so the historical rows that predate this field — all of
     * which have a null key — do not collide with each other.
     *
     * <p>Required by specs/finance/003-payouts-and-settlement.
     */
    @Indexed(unique = true, sparse = true)
    private String idempotencyKey;

    @NotBlank(message = "Organizer ID is required")
    @Indexed
    private String organizerId;

    private String organizerName;

    /**
     * Organization ID for multi-tenant payout tracking.
     * Critical for:
     * - Organization-level payout reports
     * - Financial compliance by organization
     * - Multi-organizer organization support
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    @Indexed
    private String organizationId;

    @Indexed
    private String eventId;

    private String eventTitle;

    @NotBlank(message = "Escrow account ID is required")
    @Indexed
    private String escrowAccountId;

    @NotBlank(message = "Bank account ID is required")
    private String bankAccountId;

    // Denormalized bank account details for display
    private String bankAccountName;
    private String bankName;
    private String accountNumber;

    @NotNull(message = "Requested amount is required")
    @Positive(message = "Requested amount must be positive")
    private BigDecimal requestedAmount;

    private BigDecimal platformFee;
    private BigDecimal processingFee;
    private BigDecimal taxAmount;

    @NotNull(message = "Net payout amount is required")
    /**
     * The amount actually settled, recomputed at approval (ET-FIN-003 §4).
     *
     * <p>Named {@code settledAmount} until now. The spec's name is better than
     * a rename usually is: "net" invites the reading "gross minus fees, computed
     * at request time", and the whole point of R2 is that this figure is
     * RECOMPUTED at approval, because a credit or debit can land in between.
     */
    private BigDecimal settledAmount;

    @NotBlank(message = "Currency is required")
    @Builder.Default
    private String currency = "ZMW";

    @NotNull(message = "Status is required")
    @Builder.Default
    private PayoutRequestStatus status = PayoutRequestStatus.PENDING;

    /**
     * What {@link #status} MEANS, denormalised from reference data.
     *
     * <h2>Why the meaning is stored and not looked up</h2>
     * Statuses are administrator-configurable, so the set of codes is open and
     * a fixed {@code enum} constraint on {@code status} can no longer hold.
     * MongoDB has no foreign keys, so without this field the database would
     * have nothing at all to say about a payout's state — any string would do.
     *
     * <p>Storing the semantic alongside the code puts the integrity back where
     * it matters: {@code status} is free-form and administrator-owned, while
     * this field is enum-constrained by the collection validator. Code branches
     * on the semantic, so the part that drives behaviour is still guaranteed by
     * the database rather than by convention.
     *
     * <p>It is also the only way a query like "every payout still in flight"
     * can be written at all once the codes are open — you cannot enumerate a
     * set the administrator controls.
     *
     * <h2>The trade this accepts</h2>
     * Denormalised data can go stale: an administrator re-classifying a status
     * changes the meaning for future writes, not for rows already stored. That
     * is deliberate. A payout recorded as terminal should not silently become
     * in-flight because somebody edited a dropdown months later — the record of
     * what was true at the time is the point.
     *
     * @see specs/finance/003-payouts-and-settlement
     */
    @Indexed
    private com.pml.shared.constants.WorkflowSemantic statusSemantic;

    private PayoutMethod payoutMethod;

    // Request details
    @NotNull(message = "Requested at timestamp is required")
    private LocalDateTime requestedAt;

    @NotBlank(message = "Requested by is required")
    /**
     * Who requested it. ET-FIN-003 R3 compares this against {@code approvedById}
     * to enforce that the approver is not the requester, so the {@code ...ById}
     * suffix is load-bearing: it is an identifier being compared, never a
     * display name.
     */
    private String requestedById;

    // Approval details
    private LocalDateTime approvedAt;
    private String approvedBy;

    // Rejection details
    private LocalDateTime rejectedAt;
    private String rejectedBy;
    private String rejectionReason;

    // Processing details
    private LocalDateTime processedAt;
    private String processedBy;
    private LocalDateTime expectedPayoutDate;
    private LocalDateTime actualPayoutDate;

    // Payment provider references
    @Indexed
    private String paymentReference;
    private String transactionId;
    private String externalTransactionId;
    private String pawaPayPayoutId;

    private String notes;
    private Map<String, Object> metadata;

    private List<PayoutRequestHistory> history;

    // Recovery and review tracking
    private String issueType;          // PayoutIssueType enum value
    private String resolutionType;     // PayoutResolutionType enum value
    private String reviewStatus;       // PayoutReviewStatus enum value
    private boolean isStuck;
    private String stuckReason;
    private LocalDateTime stuckAt;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewNotes;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNotes;

    // Retry tracking
    @Builder.Default
    private int retryCount = 0;
    private LocalDateTime lastRetryAt;
    private String lastError;
    private LocalDateTime nextRetryAt;

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
     * <p>Prevents concurrent modifications to payout requests.
     * Critical for financial data integrity when multiple processes
     * (e.g., admin approval, gateway callback) may modify the same request.</p>
     *
     * <p>If two transactions try to modify the same PayoutRequest simultaneously,
     * one will fail with OptimisticLockingFailureException.</p>
     */
    @Version
    private Long version;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PayoutRequestHistory {
        private String action;
        private String performedBy;
        private LocalDateTime performedAt;
        private String comments;
        private PayoutRequestStatus previousStatus;
        private PayoutRequestStatus newStatus;
        private Map<String, Object> metadata;
    }

    // ========================================================================
    // Recovery Helper Methods
    // ========================================================================

    /**
     * Mark payout request for review with an issue type.
     */
    public void markForReview(String issueType, String notes) {
        this.issueType = issueType;
        this.reviewStatus = "PENDING_REVIEW";
        if (notes != null && !notes.isBlank()) {
            this.notes = (this.notes != null ? this.notes + "\n" : "") +
                    "[" + LocalDateTime.now() + "] MARKED FOR REVIEW: " + notes;
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Start reviewing this payout request.
     */
    public void startReview(String reviewerId) {
        this.reviewStatus = "UNDER_REVIEW";
        this.reviewedBy = reviewerId;
        this.reviewedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark payout request as stuck.
     */
    public void markAsStuck(String reason) {
        this.isStuck = true;
        this.stuckReason = reason;
        this.stuckAt = LocalDateTime.now();
        this.reviewStatus = "PENDING_REVIEW";
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "[" + LocalDateTime.now() + "] STUCK: " + reason;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Resume a stuck payout request.
     */
    public void resume() {
        this.isStuck = false;
        this.status = PayoutRequestStatus.PROCESSING;
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "[" + LocalDateTime.now() + "] RESUMED";
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Resolve the payout issue.
     */
    public void resolveIssue(String resolutionType, String resolvedBy, String notes) {
        this.resolutionType = resolutionType;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
        this.resolutionNotes = notes;
        this.reviewStatus = "REVIEWED";
        this.isStuck = false;
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "[" + LocalDateTime.now() + "] RESOLVED (" + resolutionType + "): " + notes;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Escalate the payout request for higher review.
     */
    public void escalate(String reason) {
        this.reviewStatus = "ESCALATED";
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "[" + LocalDateTime.now() + "] ESCALATED: " + reason;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Mark for retry.
     */
    public void markForRetry(String error) {
        this.status = PayoutRequestStatus.PROCESSING;
        this.retryCount++;
        this.lastRetryAt = LocalDateTime.now();
        this.lastError = error;
        this.nextRetryAt = LocalDateTime.now().plusMinutes(5);
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "[" + LocalDateTime.now() + "] RETRY #" + this.retryCount + ": " + error;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Check if this payout needs review.
     */
    public boolean needsReview() {
        return "PENDING_REVIEW".equals(this.reviewStatus) ||
               "UNDER_REVIEW".equals(this.reviewStatus) ||
               "ESCALATED".equals(this.reviewStatus);
    }

    /**
     * Check if this payout can be resumed.
     */
    public boolean canResume() {
        return this.isStuck && this.retryCount < 3;
    }

    /**
     * Check if this payout can be retried.
     */
    public boolean canRetry() {
        return this.status == PayoutRequestStatus.FAILED && this.retryCount < 3;
    }
}
