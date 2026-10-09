package com.pml.booking.domain.model;

import com.pml.booking.persistence.BookingCollections;

import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.constants.PayoutRequestStatus;
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
@Document(collection = BookingCollections.PAYOUT_REQUESTS)
@TypeAlias("payout_requests")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequest {

    @Id
    private String id;

    @NotBlank(message = "Request ID is required")
    private String requestId;

    /**
     * Client-supplied idempotency key.
     *
     * <p>Uniqueness is enforced by {@code idx_idempotencyKey}, declared in
     * {@code BookingIndexInitializer} — not by an annotation here. The index is <b>unique and partial</b>, matching
     * only documents where this field holds a string.
     *
     * <p>Partial is load-bearing and sparse would be wrong. The key is optional,
     * so a request without one is stored as {@code idempotencyKey: null} — a
     * present field. Sparse skips a document only where the field is
     * <em>absent</em>, so it would index every stored null under the single key
     * {@code null}: the first keyless payout takes it and every later one
     * collides, leaving unrelated organizations unable to be paid. A
     * {@code $type} test excludes the absent field and the stored null together.
     *
     * <p>A retried create carrying the same key is recognised as the request already
     * recorded, never as a second payout.
     */
    private String idempotencyKey;

    @NotBlank(message = "Organizer ID is required")
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
    private String organizationId;

    private String eventId;

    private String eventTitle;

    @NotBlank(message = "Escrow account ID is required")
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

    private BigDecimal taxAmount;

    /**
     * The amount actually settled, recomputed at approval. It is the full escrow
     * balance: the platform's income is the per-ticket commission, and a payout carries no fee
     * of its own.
     */
    @NotNull(message = "Settled amount is required")
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
     */
    private com.pml.shared.constants.WorkflowSemantic statusSemantic;

    private PayoutMethod payoutMethod;

    // Request details
    @NotNull(message = "Requested at timestamp is required")
    private Instant requestedAt;

    @NotBlank(message = "Requested by is required")
    /**
     * Who requested it. Approval compares this against {@code approvedById}
     * to enforce that the approver is not the requester, so the {@code ...ById}
     * suffix is load-bearing: it is an identifier being compared, never a
     * display name.
     */
    private String requestedById;

    // Approval details
    private Instant approvedAt;
    private String approvedBy;

    // Rejection details
    private Instant rejectedAt;
    private String rejectedBy;
    private String rejectionReason;

    // Processing details
    private Instant processedAt;
    private String processedBy;
    private Instant expectedPayoutDate;
    private Instant actualPayoutDate;

    // Payment provider references
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
    private Instant stuckAt;
    private String reviewedBy;
    private Instant reviewedAt;
    private String reviewNotes;
    private String resolvedBy;
    private Instant resolvedAt;
    private String resolutionNotes;

    /** The journal entry that moved the escrow for the current attempt. */
    private String journalEntryId;

    /**
     * An operator's hold: while set, the request cannot be approved, retried or settled. It overlays
     * the status rather than replacing it, so releasing it returns the request to exactly where it was.
     */
    private boolean onHold;
    private String holdReason;
    private String heldBy;
    private Instant heldAt;
    private String releasedBy;
    private Instant releasedAt;

    /** The reversing entry, when the attempt's transfer failed. */
    private String reversalEntryId;

    /** The provider or refusal code that ended the last attempt. */
    private String failureCategory;

    // Retry tracking
    @Builder.Default
    private int retryCount = 0;
    private Instant lastRetryAt;
    private String lastError;
    private Instant nextRetryAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

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
        private Instant performedAt;
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
    public void markForReview(String issueType, String notes, Instant now) {
        this.issueType = issueType;
        this.reviewStatus = "PENDING_REVIEW";
        if (notes != null && !notes.isBlank()) {
            this.notes = (this.notes != null ? this.notes + "\n" : "") +
                    "[" + now + "] MARKED FOR REVIEW: " + notes;
        }
        this.updatedAt = now;
    }

    /**
     * Start reviewing this payout request.
     */
    public void startReview(String reviewerId, Instant now) {
        this.reviewStatus = "UNDER_REVIEW";
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.updatedAt = now;
    }

    /**
     * Mark payout request as stuck.
     */
    public void markAsStuck(String reason, Instant now) {
        this.isStuck = true;
        this.stuckReason = reason;
        this.stuckAt = now;
        this.reviewStatus = "PENDING_REVIEW";
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "[" + now + "] STUCK: " + reason;
        this.updatedAt = now;
    }

    /**
     * Resume a stuck payout request.
     */
    public void resume(Instant now) {
        this.isStuck = false;
        this.status = PayoutRequestStatus.PROCESSING;
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "[" + now + "] RESUMED";
        this.updatedAt = now;
    }

    /**
     * Resolve the payout issue.
     */
    public void resolveIssue(String resolutionType, String resolvedBy, String notes, Instant now) {
        this.resolutionType = resolutionType;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = now;
        this.resolutionNotes = notes;
        this.reviewStatus = "REVIEWED";
        this.isStuck = false;
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "[" + now + "] RESOLVED (" + resolutionType + "): " + notes;
        this.updatedAt = now;
    }

    /**
     * Escalate the payout request for higher review.
     */
    public void escalate(String reason, Instant now) {
        this.reviewStatus = "ESCALATED";
        this.notes = (this.notes != null ? this.notes + "\n" : "") +
                "[" + now + "] ESCALATED: " + reason;
        this.updatedAt = now;
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
     * Check if this payout can be retried.
     */
    public boolean canRetry() {
        return this.status == PayoutRequestStatus.FAILED && this.retryCount < 3;
    }
}
