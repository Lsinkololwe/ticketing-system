package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.ChargebackFundSource;
import com.pml.booking.domain.enums.ChargebackReason;
import com.pml.booking.domain.enums.ChargebackStatus;
import com.pml.booking.domain.enums.RecoveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Chargeback Record - Tracking Involuntary Payment Reversals
 *
 * A chargeback occurs when a customer disputes a charge through their
 * bank or payment provider, resulting in a forced refund. Unlike voluntary
 * refunds, chargebacks are initiated externally and typically include fees.
 *
 * <h2>Chargeback vs. Refund</h2>
 * <table border="1">
 *   <tr><th>Aspect</th><th>Refund</th><th>Chargeback</th></tr>
 *   <tr><td>Initiated by</td><td>Platform (us)</td><td>Customer via bank</td></tr>
 *   <tr><td>Control</td><td>Voluntary</td><td>Involuntary</td></tr>
 *   <tr><td>Fees</td><td>Usually none</td><td>K250-500 per case</td></tr>
 *   <tr><td>Timeline</td><td>Immediate</td><td>45-90 days to resolve</td></tr>
 *   <tr><td>Disputable</td><td>N/A</td><td>Yes, with evidence</td></tr>
 *   <tr><td>Impact</td><td>Minimal</td><td>Affects merchant standing</td></tr>
 * </table>
 *
 * <h2>Chargeback Lifecycle</h2>
 * <pre>
 *               ┌──────────────┐
 *               │   RECEIVED   │  Gateway notifies us of chargeback
 *               └──────┬───────┘
 *                      │ Freeze organizer escrow
 *                      ▼
 *               ┌──────────────┐
 *               │ UNDER_REVIEW │  Gathering evidence, assessing case
 *               └──────┬───────┘
 *                      │
 *         ┌────────────┴────────────┐
 *         │                         │
 *         ▼                         ▼
 *   ┌──────────┐              ┌───────────┐
 *   │ ACCEPTED │              │ DISPUTED  │  Evidence submitted
 *   │(no fight)│              │           │
 *   └────┬─────┘              └─────┬─────┘
 *        │                          │
 *        │               ┌──────────┴──────────┐
 *        │               │                     │
 *        │               ▼                     ▼
 *        │         ┌───────────┐         ┌───────────┐
 *        │         │    WON    │         │   LOST    │
 *        │         │(funds back)│         │(confirmed │
 *        │         │           │         │   loss)   │
 *        │         └───────────┘         └─────┬─────┘
 *        │                                     │
 *        └─────────────────┬───────────────────┘
 *                          │ Start recovery
 *                          ▼
 *                  ┌───────────────────┐
 *                  │ RECOVERY PROCESS  │
 *                  │ 1. Organizer escrow│
 *                  │ 2. Future payouts  │
 *                  │ 3. Platform reserve│
 *                  │ 4. Write-off       │
 *                  └───────────────────┘
 * </pre>
 *
 * <h2>Financial Impact</h2>
 * <p>A chargeback creates multiple financial impacts:</p>
 * <ul>
 *   <li><b>Chargeback Amount</b>: The original payment amount reversed</li>
 *   <li><b>Chargeback Fee</b>: Gateway penalty (K250-500 typically)</li>
 *   <li><b>Commission Clawback</b>: If commission was already earned</li>
 *   <li><b>Escrow Reduction</b>: Debit from organizer's escrow</li>
 * </ul>
 *
 * @see ChargebackReason
 * @see ChargebackStatus
 * @see RecoveryStatus
 * @see ChargebackFundSource
 * @since 1.0.0
 */
@Document(collection = "chargebacks")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "status_received_idx", def = "{'status': 1, 'receivedAt': -1}"),
    @CompoundIndex(name = "organizer_status_idx", def = "{'organizerId': 1, 'status': 1}"),
    @CompoundIndex(name = "event_idx", def = "{'eventId': 1}"),
    @CompoundIndex(name = "recovery_status_idx", def = "{'recoveryStatus': 1}")
})
public class ChargebackRecord {

    /**
     * MongoDB document ID.
     */
    @Id
    private String id;

    /**
     * External chargeback ID from payment provider.
     *
     * <p>This is the ID assigned by PawaPay or the gateway when they
     * notify us of the chargeback.</p>
     */
    @NotBlank(message = "External chargeback ID is required")
    @Indexed(unique = true)
    private String chargebackId;

    // ========================================================================
    // ORIGINAL TRANSACTION REFERENCES
    // ========================================================================

    /**
     * Reference to the original payment transaction.
     *
     * <p>Links to PaymentIntent or PaymentAttempt that was charged back.</p>
     */
    @NotBlank(message = "Original transaction ID is required")
    @Indexed
    private String originalTransactionId;

    /**
     * Reference to the ticket that was purchased.
     */
    @Indexed
    private String ticketId;

    /**
     * Reference to the event.
     */
    @NotBlank(message = "Event ID is required")
    @Indexed
    private String eventId;

    /**
     * Reference to the event organizer.
     *
     * <p>Important for recovery - we recover funds from the organizer.</p>
     */
    @NotBlank(message = "Organizer ID is required")
    @Indexed
    private String organizerId;

    /**
     * Organization ID for multi-tenant chargeback tracking.
     * Critical for:
     * - Organization-level chargeback reports
     * - Aggregate recovery tracking by organization
     * - Risk assessment across organization's events
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    @Indexed
    private String organizationId;

    /**
     * Reference to the customer who initiated the chargeback.
     */
    @NotBlank(message = "Customer ID is required")
    @Indexed
    private String customerId;

    // ========================================================================
    // FINANCIAL AMOUNTS
    // ========================================================================

    /**
     * Original transaction amount.
     *
     * <p>The full amount of the original payment.</p>
     */
    @NotNull(message = "Original amount is required")
    @Positive(message = "Original amount must be positive")
    private BigDecimal originalAmount;

    /**
     * Chargeback amount (may differ from original if partial).
     *
     * <p>Usually equals originalAmount, but can be partial.</p>
     */
    @NotNull(message = "Chargeback amount is required")
    @Positive(message = "Chargeback amount must be positive")
    private BigDecimal chargebackAmount;

    /**
     * Fee charged by the payment provider for this chargeback.
     *
     * <p>Typically K250-500 per chargeback. This is a direct cost
     * regardless of whether we win or lose the dispute.</p>
     */
    @NotNull(message = "Chargeback fee is required")
    private BigDecimal chargebackFee;

    /**
     * Currency code.
     */
    @Builder.Default
    private String currency = "ZMW";

    // ========================================================================
    // STATUS & REASON
    // ========================================================================

    /**
     * Reason for the chargeback as reported by the customer/bank.
     */
    @NotNull(message = "Reason is required")
    @Indexed
    private ChargebackReason reason;

    /**
     * Additional reason details or description.
     */
    private String reasonDetails;

    /**
     * Current status of the chargeback case.
     */
    @NotNull(message = "Status is required")
    @Indexed
    @Builder.Default
    private ChargebackStatus status = ChargebackStatus.RECEIVED;

    // ========================================================================
    // TIMELINE
    // ========================================================================

    /**
     * When the chargeback was received/created.
     */
    @NotNull(message = "Received date is required")
    @Indexed
    private Instant receivedAt;

    /**
     * Deadline to respond with evidence.
     *
     * <p>Typically 7-14 days from receipt. Missing this deadline
     * usually means automatic loss.</p>
     */
    @NotNull(message = "Response deadline is required")
    private LocalDate responseDeadline;

    /**
     * When the case was resolved (ACCEPTED, WON, or LOST).
     */
    private Instant resolvedAt;

    // ========================================================================
    // DISPUTE EVIDENCE
    // ========================================================================

    /**
     * Whether evidence has been submitted for dispute.
     */
    @Builder.Default
    private Boolean evidenceSubmitted = false;

    /**
     * When evidence was submitted.
     */
    private Instant evidenceSubmittedAt;

    /**
     * List of evidence document references/URLs.
     */
    @Builder.Default
    private List<String> evidenceDocuments = new ArrayList<>();

    /**
     * Notes about the dispute/evidence.
     */
    private String disputeNotes;

    // ========================================================================
    // RECOVERY TRACKING
    // ========================================================================

    /**
     * Status of fund recovery from organizer.
     */
    @NotNull(message = "Recovery status is required")
    @Indexed
    @Builder.Default
    private RecoveryStatus recoveryStatus = RecoveryStatus.NOT_STARTED;

    /**
     * Amount successfully recovered.
     *
     * <p>May be partial if multiple sources are used.</p>
     */
    @Builder.Default
    private BigDecimal recoveredAmount = BigDecimal.ZERO;

    /**
     * Source(s) from which funds were recovered.
     */
    @Builder.Default
    private List<ChargebackFundSource> fundSources = new ArrayList<>();

    /**
     * Primary fund source for the majority of recovery.
     */
    private ChargebackFundSource primaryFundSource;

    /**
     * Amount written off as bad debt (if any).
     */
    @Builder.Default
    private BigDecimal writtenOffAmount = BigDecimal.ZERO;

    // ========================================================================
    // COMMISSION CLAWBACK
    // ========================================================================

    /**
     * Reference to the commission record that was clawed back.
     *
     * <p>If commission was already earned, it must be reversed.</p>
     */
    private String commissionClawbackId;

    /**
     * Amount of commission clawed back.
     */
    private BigDecimal commissionClawbackAmount;

    // ========================================================================
    // ACCOUNTING REFERENCE
    // ========================================================================

    /**
     * Reference to the journal entry recording this chargeback.
     */
    @Indexed
    private String journalEntryId;

    /**
     * Reference to the journal entry for recovery.
     */
    private String recoveryJournalEntryId;

    // ========================================================================
    // AUDIT FIELDS
    // ========================================================================

    /**
     * User who processed the chargeback.
     */
    private String processedBy;

    /**
     * User who submitted dispute evidence.
     */
    private String disputedBy;

    /**
     * User who performed recovery.
     */
    private String recoveredBy;

    /**
     * Internal notes for tracking.
     */
    private String internalNotes;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    // ========================================================================
    // STATE TRANSITION METHODS
    // ========================================================================

    /**
     * Starts the review process.
     *
     * @param reviewerId User starting the review
     */
    public void startReview(String reviewerId) {
        if (status != ChargebackStatus.RECEIVED) {
            throw new IllegalStateException(
                    "Cannot start review from status " + status
            );
        }
        this.status = ChargebackStatus.UNDER_REVIEW;
        this.processedBy = reviewerId;
    }

    /**
     * Accepts the chargeback without disputing.
     *
     * @param acceptedBy User accepting the chargeback
     * @param reason Reason for accepting
     */
    public void accept(String acceptedBy, String reason) {
        if (status != ChargebackStatus.RECEIVED && status != ChargebackStatus.UNDER_REVIEW) {
            throw new IllegalStateException(
                    "Cannot accept chargeback from status " + status
            );
        }
        this.status = ChargebackStatus.ACCEPTED;
        this.resolvedAt = Instant.now();
        this.processedBy = acceptedBy;
        this.internalNotes = appendNote("Accepted: " + reason);
    }

    /**
     * Submits dispute evidence.
     *
     * @param disputedBy User submitting the dispute
     * @param evidenceDocs List of evidence document references
     * @param notes Notes about the dispute
     */
    public void submitDispute(String disputedBy, List<String> evidenceDocs, String notes) {
        if (status != ChargebackStatus.RECEIVED && status != ChargebackStatus.UNDER_REVIEW) {
            throw new IllegalStateException(
                    "Cannot dispute chargeback from status " + status
            );
        }
        this.status = ChargebackStatus.DISPUTED;
        this.evidenceSubmitted = true;
        this.evidenceSubmittedAt = Instant.now();
        this.disputedBy = disputedBy;
        if (evidenceDocs != null) {
            this.evidenceDocuments.addAll(evidenceDocs);
        }
        this.disputeNotes = notes;
    }

    /**
     * Records dispute outcome as won.
     *
     * @param processedBy User recording the outcome
     */
    public void recordWin(String processedBy) {
        if (status != ChargebackStatus.DISPUTED) {
            throw new IllegalStateException(
                    "Cannot record win from status " + status
            );
        }
        this.status = ChargebackStatus.WON;
        this.resolvedAt = Instant.now();
        this.processedBy = processedBy;
        this.recoveryStatus = RecoveryStatus.RECOVERED; // Funds returned by bank
        this.recoveredAmount = this.chargebackAmount;
    }

    /**
     * Records dispute outcome as lost.
     *
     * @param processedBy User recording the outcome
     */
    public void recordLoss(String processedBy) {
        if (status != ChargebackStatus.DISPUTED) {
            throw new IllegalStateException(
                    "Cannot record loss from status " + status
            );
        }
        this.status = ChargebackStatus.LOST;
        this.resolvedAt = Instant.now();
        this.processedBy = processedBy;
    }

    /**
     * Starts the recovery process.
     */
    public void startRecovery() {
        if (!isLoss()) {
            throw new IllegalStateException(
                    "Cannot start recovery for non-loss chargeback"
            );
        }
        if (recoveryStatus != RecoveryStatus.NOT_STARTED) {
            throw new IllegalStateException(
                    "Recovery already started/completed"
            );
        }
        this.recoveryStatus = RecoveryStatus.IN_PROGRESS;
    }

    /**
     * Records successful recovery.
     *
     * @param amount Amount recovered
     * @param source Source of recovery
     * @param recoveredBy User performing recovery
     */
    public void recordRecovery(BigDecimal amount, ChargebackFundSource source, String recoveredBy) {
        this.recoveredAmount = this.recoveredAmount.add(amount);
        if (!this.fundSources.contains(source)) {
            this.fundSources.add(source);
        }
        if (this.primaryFundSource == null) {
            this.primaryFundSource = source;
        }
        this.recoveredBy = recoveredBy;

        // Check if fully recovered
        if (this.recoveredAmount.compareTo(this.chargebackAmount) >= 0) {
            this.recoveryStatus = RecoveryStatus.RECOVERED;
        }
    }

    /**
     * Records amount written off as bad debt.
     *
     * @param amount Amount to write off
     */
    public void writeOff(BigDecimal amount) {
        this.writtenOffAmount = this.writtenOffAmount.add(amount);
        this.fundSources.add(ChargebackFundSource.WRITE_OFF);
        this.recoveryStatus = RecoveryStatus.WRITTEN_OFF;
    }

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Checks if this chargeback is still in progress.
     *
     * @return true if not yet resolved
     */
    public boolean isInProgress() {
        return status.isInProgress();
    }

    /**
     * Checks if this chargeback resulted in a loss.
     *
     * @return true if ACCEPTED or LOST
     */
    public boolean isLoss() {
        return status.isLoss();
    }

    /**
     * Checks if recovery is needed.
     *
     * @return true if loss and recovery not yet started
     */
    public boolean needsRecovery() {
        return isLoss() && recoveryStatus == RecoveryStatus.NOT_STARTED;
    }

    /**
     * Gets the total financial impact (chargeback + fee).
     *
     * @return Total amount
     */
    public BigDecimal getTotalImpact() {
        return chargebackAmount.add(chargebackFee != null ? chargebackFee : BigDecimal.ZERO);
    }

    /**
     * Gets the unrecovered amount.
     *
     * @return Chargeback amount - recovered amount
     */
    public BigDecimal getUnrecoveredAmount() {
        return chargebackAmount.subtract(recoveredAmount != null ? recoveredAmount : BigDecimal.ZERO);
    }

    /**
     * Checks if response deadline has passed.
     *
     * @return true if past deadline
     */
    public boolean isPastDeadline() {
        return responseDeadline != null && LocalDate.now().isAfter(responseDeadline);
    }

    /**
     * Gets days until response deadline.
     *
     * @return Days remaining (negative if past)
     */
    public long getDaysUntilDeadline() {
        if (responseDeadline == null) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), responseDeadline);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private String appendNote(String note) {
        if (internalNotes == null || internalNotes.isBlank()) {
            return note;
        }
        return internalNotes + "\n" + note;
    }

    // ========================================================================
    // FACTORY METHOD
    // ========================================================================

    /**
     * Creates a new chargeback record.
     *
     * @param chargebackId External chargeback ID
     * @param originalTransactionId Original payment transaction ID
     * @param ticketId Ticket ID
     * @param eventId Event ID
     * @param organizerId Organizer ID
     * @param customerId Customer ID
     * @param originalAmount Original payment amount
     * @param chargebackAmount Chargeback amount
     * @param chargebackFee Gateway chargeback fee
     * @param reason Chargeback reason
     * @param responseDeadline Deadline to respond
     * @return New ChargebackRecord instance
     */
    public static ChargebackRecord create(
            String chargebackId,
            String originalTransactionId,
            String ticketId,
            String eventId,
            String organizerId,
            String organizationId,
            String customerId,
            BigDecimal originalAmount,
            BigDecimal chargebackAmount,
            BigDecimal chargebackFee,
            ChargebackReason reason,
            LocalDate responseDeadline
    ) {
        return ChargebackRecord.builder()
                .chargebackId(chargebackId)
                .originalTransactionId(originalTransactionId)
                .ticketId(ticketId)
                .eventId(eventId)
                .organizerId(organizerId)
                .organizationId(organizationId)
                .customerId(customerId)
                .originalAmount(originalAmount)
                .chargebackAmount(chargebackAmount)
                .chargebackFee(chargebackFee)
                .reason(reason)
                .status(ChargebackStatus.RECEIVED)
                .recoveryStatus(RecoveryStatus.NOT_STARTED)
                .receivedAt(Instant.now())
                .responseDeadline(responseDeadline)
                .build();
    }
}
