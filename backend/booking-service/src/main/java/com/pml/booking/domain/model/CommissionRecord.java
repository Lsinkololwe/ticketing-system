package com.pml.booking.domain.model;

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

/**
 * CommissionRecord Model
 *
 * Tracks platform commission for each ticket sale using the Two-Stage Commission Model:
 *
 * Stage 1 - PENDING:
 *   - Commission recorded at purchase time
 *   - NOT yet earned revenue
 *   - If refunded before event, simply CANCELLED (no money movement)
 *
 * Stage 2 - EARNED:
 *   - After event completes + 7-day hold period
 *   - Commission becomes actual platform revenue
 *   - If refunded after this point, must be CLAWED_BACK (rare)
 *
 * Benefits of Two-Stage Model:
 *   - No need to "claw back" commission on most refunds
 *   - Simpler accounting - pending is just a placeholder
 *   - Clear audit trail of when commission became revenue
 */
@Document(collection = "commission_records")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "event_status_idx", def = "{'eventId': 1, 'status': 1}"),
    @CompoundIndex(name = "organizer_status_idx", def = "{'organizerId': 1, 'status': 1}")
})
public class CommissionRecord {

    @Id
    private String id;

    @NotBlank(message = "Ticket ID is required")
    @Indexed(unique = true)
    private String ticketId;

    @NotBlank(message = "Event ID is required")
    @Indexed
    private String eventId;

    @NotBlank(message = "Organizer ID is required")
    @Indexed
    private String organizerId;

    /**
     * Organization ID for multi-tenant commission tracking.
     * Critical for:
     * - Organization-level commission reports
     * - Consolidated financial statements
     * - Multi-organizer organization support
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    @Indexed
    private String organizationId;

    // Amount Details
    @NotNull(message = "Commission amount is required")
    @Positive(message = "Commission amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Commission rate is required")
    private BigDecimal rate;

    @NotNull(message = "Ticket price is required")
    @Positive(message = "Ticket price must be positive")
    private BigDecimal ticketPrice;

    @Builder.Default
    private String currency = "ZMW";

    // Status (Two-Stage Model)
    @NotNull(message = "Status is required")
    @Indexed
    private CommissionStatus status;

    // Timestamps for each stage
    @NotNull(message = "Pending timestamp is required")
    private Instant pendingAt;

    private Instant earnedAt;
    private Instant cancelledAt;
    private Instant clawedBackAt;

    // References for refund scenarios
    private String refundRequestId;
    private String refundReason;

    // Journal entry references for double-entry bookkeeping
    private String pendingJournalEntryId;
    private String earnedJournalEntryId;
    private String cancelJournalEntryId;
    private String clawbackJournalEntryId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    /**
     * Commission status following the Two-Stage Model
     */
    public enum CommissionStatus {
        /**
         * Commission recorded but not yet earned.
         * Event has not completed + 7-day hold.
         */
        PENDING,

        /**
         * Commission is now platform revenue.
         * Event completed + 7-day hold passed.
         */
        EARNED,

        /**
         * Commission was cancelled before becoming earned.
         * Typically due to refund before event.
         * No money was ever moved - just cancelled the pending entry.
         */
        CANCELLED,

        /**
         * Commission was clawed back after being earned.
         * Rare - only for refunds/disputes after event completion.
         * Actual money movement required (debit from earned revenue).
         */
        CLAWED_BACK
    }

    // Factory methods

    public static CommissionRecord createPending(
            String ticketId,
            String eventId,
            String organizerId,
            String organizationId,
            BigDecimal ticketPrice,
            BigDecimal rate
    ) {
        BigDecimal amount = ticketPrice.multiply(rate);
        return CommissionRecord.builder()
                .ticketId(ticketId)
                .eventId(eventId)
                .organizerId(organizerId)
                .organizationId(organizationId)
                .amount(amount)
                .rate(rate)
                .ticketPrice(ticketPrice)
                .status(CommissionStatus.PENDING)
                .pendingAt(Instant.now())
                .build();
    }

    // State transitions

    /**
     * Transition commission from PENDING to EARNED.
     * Called 7 days after event completion.
     */
    public void markEarned(String journalEntryId) {
        if (this.status != CommissionStatus.PENDING) {
            throw new IllegalStateException(
                "Cannot mark as earned: current status is " + this.status
            );
        }
        this.status = CommissionStatus.EARNED;
        this.earnedAt = Instant.now();
        this.earnedJournalEntryId = journalEntryId;
    }

    /**
     * Cancel pending commission (refund before event).
     * No money movement needed - just cancel the pending entry.
     */
    public void cancel(String refundRequestId, String reason, String journalEntryId) {
        if (this.status != CommissionStatus.PENDING) {
            throw new IllegalStateException(
                "Cannot cancel: current status is " + this.status
            );
        }
        this.status = CommissionStatus.CANCELLED;
        this.cancelledAt = Instant.now();
        this.refundRequestId = refundRequestId;
        this.refundReason = reason;
        this.cancelJournalEntryId = journalEntryId;
    }

    /**
     * Claw back earned commission (rare - refund after event).
     * Actual money movement from earned revenue account.
     */
    public void clawback(String refundRequestId, String reason, String journalEntryId) {
        if (this.status != CommissionStatus.EARNED) {
            throw new IllegalStateException(
                "Cannot clawback: current status is " + this.status
            );
        }
        this.status = CommissionStatus.CLAWED_BACK;
        this.clawedBackAt = Instant.now();
        this.refundRequestId = refundRequestId;
        this.refundReason = reason;
        this.clawbackJournalEntryId = journalEntryId;
    }

    // Query helpers

    public boolean isPending() {
        return status == CommissionStatus.PENDING;
    }

    public boolean isEarned() {
        return status == CommissionStatus.EARNED;
    }

    public boolean isCancelled() {
        return status == CommissionStatus.CANCELLED;
    }

    public boolean isClawedBack() {
        return status == CommissionStatus.CLAWED_BACK;
    }

    public boolean isTerminal() {
        return status == CommissionStatus.EARNED ||
               status == CommissionStatus.CANCELLED ||
               status == CommissionStatus.CLAWED_BACK;
    }
}
