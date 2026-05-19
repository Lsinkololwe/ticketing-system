package com.pml.booking.domain.model;

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

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * EventEscrowAccount Model
 *
 * Per-event escrow account that holds organizer funds until payout.
 * This is the money that belongs to the organizer (after platform commission).
 *
 * Escrow Lifecycle:
 * 1. CREATED - Account created when event is published
 * 2. ACTIVE - Receiving funds from ticket sales
 * 3. LOCKED - Event happened, in 7-day hold period
 * 4. PAYOUT_ELIGIBLE - Hold period passed, organizer can request payout
 * 5. PROCESSING_PAYOUT - Payout in progress
 * 6. CLOSED - All funds paid out, account closed
 * 7. CANCELLED - Event cancelled, all refunds processed
 *
 * Key Concept: This is an ESCROW account (holding others' money).
 * - The platform is a LIABILITY holder - we OWE this money to the organizer
 * - We MUST pay this out eventually (unless refunded)
 * - This is NOT platform revenue (that's the commission)
 */
@Document(collection = "event_escrow_accounts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EventEscrowAccount {

    @Id
    private String id;

    /**
     * Human-readable account number.
     * Format: ESC-{eventId}-{year}
     */
    @NotBlank(message = "Account number is required")
    @Indexed(unique = true)
    private String accountNumber;

    // Event & Organizer References
    @NotBlank(message = "Event ID is required")
    @Indexed(unique = true)
    private String eventId;

    private String eventTitle;

    @NotBlank(message = "Organizer ID is required")
    @Indexed
    private String organizerId;

    private String organizerName;

    /**
     * Organization ID that owns this escrow account.
     * Critical for:
     * - Organization-level financial reporting
     * - Consolidated escrow balance views
     * - Multi-event payout batching
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    @Indexed
    private String organizationId;

    // Balances
    @NotNull(message = "Current balance is required")
    @DecimalMin(value = "0.0", message = "Balance cannot be negative")
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @NotNull(message = "Total deposits is required")
    @DecimalMin(value = "0.0")
    @Builder.Default
    private BigDecimal totalDeposits = BigDecimal.ZERO;

    @NotNull(message = "Total withdrawals is required")
    @DecimalMin(value = "0.0")
    @Builder.Default
    private BigDecimal totalWithdrawals = BigDecimal.ZERO;

    @NotNull(message = "Total refunds is required")
    @DecimalMin(value = "0.0")
    @Builder.Default
    private BigDecimal totalRefunds = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal pendingWithdrawals = BigDecimal.ZERO;

    /**
     * Total platform commissions collected from this event's ticket sales.
     * This is NOT part of escrow balance - it belongs to the platform.
     */
    @DecimalMin(value = "0.0")
    @Builder.Default
    private BigDecimal totalCommissions = BigDecimal.ZERO;

    @Builder.Default
    private String currency = "ZMW";

    // Status
    @NotNull(message = "Status is required")
    @Indexed
    private EscrowStatus status;

    /**
     * Date until which the escrow is locked.
     * Typically: eventDate + 7 days
     */
    private LocalDateTime lockUntil;

    /**
     * Date when payout becomes eligible.
     * Set when status changes to PAYOUT_ELIGIBLE.
     */
    private Instant payoutEligibleAt;

    /**
     * Reason for locking the escrow account.
     * Examples: "HOLD_PERIOD", "FRAUD_REVIEW", "DISPUTE"
     */
    private String lockReason;

    /**
     * Timestamp when the account was closed.
     */
    private LocalDateTime closedAt;

    /**
     * Reason for closing the account.
     * Examples: "FULLY_PAID", "EVENT_CANCELLED", "ZERO_BALANCE"
     */
    private String closedReason;

    // Transaction Ledger (embedded for quick access)
    @Builder.Default
    private List<EscrowTransaction> transactions = new ArrayList<>();

    // Audit
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    /**
     * Escrow account status lifecycle
     */
    public enum EscrowStatus {
        CREATED,           // Account created, no funds yet
        ACTIVE,            // Receiving funds from ticket sales
        LOCKED,            // Event happened, in hold period
        PAYOUT_ELIGIBLE,   // Can request payout
        PROCESSING_PAYOUT, // Payout in progress
        CLOSED,            // All funds paid out
        CANCELLED          // Event cancelled
    }


    // Factory method

    public static EventEscrowAccount create(
            String eventId,
            String eventTitle,
            String organizerId,
            String organizerName,
            LocalDateTime eventDate
    ) {
        String accountNumber = String.format("ESC-%s-%d",
                eventId.substring(0, Math.min(8, eventId.length())).toUpperCase(),
                eventDate.getYear());

        return EventEscrowAccount.builder()
                .accountNumber(accountNumber)
                .eventId(eventId)
                .eventTitle(eventTitle)
                .organizerId(organizerId)
                .organizerName(organizerName)
                .status(EscrowStatus.CREATED)
                .lockUntil(eventDate.plusDays(7))
                .build();
    }

    // Business methods

    /**
     * Credit funds to escrow (ticket sale).
     */
    public void credit(
            BigDecimal amount,
            String ticketId,
            String paymentIntentId,
            String description
    ) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        if (status == EscrowStatus.CLOSED || status == EscrowStatus.CANCELLED) {
            throw new IllegalStateException("Cannot credit closed/cancelled escrow");
        }

        this.currentBalance = this.currentBalance.add(amount);
        this.totalDeposits = this.totalDeposits.add(amount);

        // Activate if first deposit
        if (this.status == EscrowStatus.CREATED) {
            this.status = EscrowStatus.ACTIVE;
        }

        // Record transaction
        EscrowTransaction txn = EscrowTransaction.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(EscrowTransaction.TransactionType.CREDIT)
                .category("TICKET_SALE")
                .amount(amount)
                .balanceAfter(this.currentBalance)
                .ticketId(ticketId)
                .paymentIntentId(paymentIntentId)
                .description(description)
                .timestamp(Instant.now())
                .build();
        if (this.transactions == null) {
            this.transactions = new ArrayList<>();
        }
        this.transactions.add(txn);
    }

    /**
     * Debit funds from escrow (refund).
     */
    public void debitForRefund(
            BigDecimal amount,
            String ticketId,
            String refundRequestId,
            String description
    ) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Insufficient escrow balance");
        }

        this.currentBalance = this.currentBalance.subtract(amount);
        this.totalRefunds = this.totalRefunds.add(amount);

        EscrowTransaction txn = EscrowTransaction.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(EscrowTransaction.TransactionType.DEBIT)
                .category("REFUND")
                .amount(amount)
                .balanceAfter(this.currentBalance)
                .ticketId(ticketId)
                .refundRequestId(refundRequestId)
                .description(description)
                .timestamp(Instant.now())
                .build();
        this.transactions.add(txn);
    }

    /**
     * Debit funds from escrow (payout to organizer).
     */
    public void debitForPayout(
            BigDecimal amount,
            String payoutRequestId,
            String description
    ) {
        if (status != EscrowStatus.PAYOUT_ELIGIBLE && status != EscrowStatus.PROCESSING_PAYOUT) {
            throw new IllegalStateException("Escrow is not eligible for payout");
        }
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Insufficient escrow balance");
        }

        this.currentBalance = this.currentBalance.subtract(amount);
        this.totalWithdrawals = this.totalWithdrawals.add(amount);

        EscrowTransaction txn = EscrowTransaction.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(EscrowTransaction.TransactionType.DEBIT)
                .category("PAYOUT")
                .amount(amount)
                .balanceAfter(this.currentBalance)
                .payoutRequestId(payoutRequestId)
                .description(description)
                .timestamp(Instant.now())
                .build();
        this.transactions.add(txn);

        // Close if fully paid out
        if (this.currentBalance.compareTo(BigDecimal.ZERO) == 0) {
            this.status = EscrowStatus.CLOSED;
        }
    }

    /**
     * Lock escrow after event completion.
     *
     * @param reason The reason for locking (e.g., "HOLD_PERIOD", "FRAUD_REVIEW")
     */
    public void lock(String reason) {
        if (status != EscrowStatus.ACTIVE) {
            throw new IllegalStateException("Can only lock active escrow");
        }
        this.status = EscrowStatus.LOCKED;
        this.lockReason = reason != null ? reason : "HOLD_PERIOD";
    }

    /**
     * Lock escrow after event completion (default reason).
     */
    public void lock() {
        lock("HOLD_PERIOD");
    }

    /**
     * Mark as payout eligible after hold period.
     */
    public void markPayoutEligible() {
        if (status != EscrowStatus.LOCKED) {
            throw new IllegalStateException("Can only make locked escrow payout eligible");
        }
        this.status = EscrowStatus.PAYOUT_ELIGIBLE;
        this.payoutEligibleAt = Instant.now();
    }

    /**
     * Cancel escrow (event cancelled, all refunded).
     *
     * @param reason The reason for cancellation
     */
    public void cancel(String reason) {
        if (this.currentBalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Cannot cancel escrow with remaining balance");
        }
        this.status = EscrowStatus.CANCELLED;
        this.closedAt = LocalDateTime.now();
        this.closedReason = reason != null ? reason : "EVENT_CANCELLED";
    }

    /**
     * Cancel escrow (event cancelled, all refunded).
     */
    public void cancel() {
        cancel("EVENT_CANCELLED");
    }

    /**
     * Close the escrow account after all funds paid out.
     *
     * @param reason The reason for closure
     */
    public void close(String reason) {
        if (this.currentBalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Cannot close escrow with remaining balance");
        }
        this.status = EscrowStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
        this.closedReason = reason != null ? reason : "FULLY_PAID";
    }

    /**
     * Record commission collected from a ticket sale.
     *
     * @param commissionAmount The commission amount to record
     */
    public void recordCommission(BigDecimal commissionAmount) {
        if (commissionAmount == null || commissionAmount.compareTo(BigDecimal.ZERO) < 0) {
            return;
        }
        if (this.totalCommissions == null) {
            this.totalCommissions = BigDecimal.ZERO;
        }
        this.totalCommissions = this.totalCommissions.add(commissionAmount);
    }

    // Query helpers

    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.currentBalance.compareTo(amount) >= 0;
    }

    public boolean isPayoutEligible() {
        return status == EscrowStatus.PAYOUT_ELIGIBLE;
    }

    public boolean isLocked() {
        return status == EscrowStatus.LOCKED;
    }

    public boolean isActive() {
        return status == EscrowStatus.ACTIVE;
    }

    public boolean isClosed() {
        return status == EscrowStatus.CLOSED || status == EscrowStatus.CANCELLED;
    }

    public boolean isHoldPeriodPassed() {
        return lockUntil != null && LocalDateTime.now().isAfter(lockUntil);
    }

    public BigDecimal getAvailableForPayout() {
        if (!isPayoutEligible()) {
            return BigDecimal.ZERO;
        }
        return currentBalance.subtract(pendingWithdrawals);
    }
}
