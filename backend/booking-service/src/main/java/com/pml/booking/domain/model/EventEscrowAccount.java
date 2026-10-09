package com.pml.booking.domain.model;

import com.pml.shared.constants.PlatformTime;

import com.pml.booking.persistence.BookingCollections;

import com.pml.shared.constants.EscrowStatus;
import com.pml.shared.constants.WorkflowSemantic;
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

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
@Document(collection = BookingCollections.ESCROW_ACCOUNTS)
@TypeAlias("escrow_accounts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EventEscrowAccount {

    @Id
    private String id;

    // Event & Organizer References
    /**
     * Human-readable account number, format {@code ESC-{eventId}-{year}}.
     *
     * <p>The account is keyed on {@code eventId} alone; this number is a display
     * label. It stays because it is exposed on the GraphQL
     * {@code AccountSummary} type and the organizer finance screen queries it;
     * removing it is an API break needing frontend coordination, not a field
     * deletion.
     */
    private String accountNumber;

    @NotBlank(message = "Event ID is required")
    private String eventId;

    /**
     * Denormalised event title.
     *
     * <p><b>A copy of catalog's data, kept deliberately and temporarily.</b> The
     * organizer's payout screen and the event financial report both display it.
     * Removing it leaves two choices, and both are worse: blank the labels, or
     * fabricate {@code "Event " + id} and present it as the name of a real event.
     *
     * <p>It does not really belong here — it is a copy of catalog's data that can
     * go stale and disagree with its source. Removing it
     * needs a catalog lookup on those two surfaces, which is a functional change
     * rather than a field deletion.
     */
    private String eventTitle;

    @NotBlank(message = "Organizer ID is required")
    private String organizerId;

    /** Denormalised organizer name. Kept for the same reason as eventTitle. */
    private String organizerName;

    /**
     * Commission collected from this event's sales.
     *
     * <p>The ledger carries commission on platform account {@code 2020 Pending
     * Commission}, not on the escrow. Kept here because AccountSummary
     * exposes it, and a fabricated zero on a commission field is precisely the
     * confidently-wrong number this work exists to eliminate.
     */
    @Builder.Default
    private BigDecimal totalCommissions = BigDecimal.ZERO;

    /**
     * Organization ID that owns this escrow account.
     * Critical for:
     * - Organization-level financial reporting
     * - Consolidated escrow balance views
     * - Multi-event payout batching
     *
     * OWASP A01:2021 Compliance: Used for tenant isolation in authorization.
     */
    private String organizationId;

    // Balances
    @NotNull(message = "Current balance is required")
    @DecimalMin(value = "0.0", message = "Balance cannot be negative")
    @Builder.Default
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @NotNull(message = "Total deposits is required")
    @DecimalMin(value = "0.0")
    @Builder.Default
    private BigDecimal totalCredited = BigDecimal.ZERO;

    @NotNull(message = "Total withdrawals is required")
    @DecimalMin(value = "0.0")
    @Builder.Default
    private BigDecimal totalDebited = BigDecimal.ZERO;

    @NotNull(message = "Total refunds is required")
    @DecimalMin(value = "0.0")
    @Builder.Default
    private BigDecimal totalRefunded = BigDecimal.ZERO;

    @Builder.Default
    private String currency = "ZMW";

    // Status
    @NotNull(message = "Status is required")
    private EscrowStatus status;

    /**
     * What {@link #status} MEANS, denormalised from administrator-owned
     * reference data.
     *
     * <p>Once administrators can add escrow statuses, no code may branch on the
     * code string — a query for "escrow still holding money" written as a literal
     * status list silently omits every status added after it was written. Reports
     * that under-count held funds are worse than reports that fail.
     *
     * <p>Stamped on write and backfilled by
     * {@code StatusSemanticMigrationService}. Null means the code matched
     * no reference-data row, which is findable; a guessed value would not be.
     */
    private WorkflowSemantic statusSemantic;

    /**
     * When the account became payout-eligible.
     *
     * <p>Also a denormalised copy, kept for the same reason: the organizer's
     * dashboard shows "eligible since". The status transition and its audit entry
     * already record this, but nothing reads it back out of the audit trail, and
     * dropping the field silently blanks the column.
     */
    private Instant payoutEligibleAt;

    /**
     * When the hold period ends: {@code endsAt + finance.escrow.hold-period}.
     *
     * <p>Set by {@code catalog.EventCompleted}. A date, which is what separates
     * {@link EscrowStatus#HOLD} from {@link EscrowStatus#SUSPENDED} — the latter
     * has no date because it ends when a person decides it does.
     */
    private Instant holdUntil;

    /**
     * Open chargebacks against this event's tickets.
     *
     * <p>The account cannot become {@code PAYOUT_ELIGIBLE} while this is above zero:
     * money that may still be clawed back must not be paid out. Denormalised
     * onto the account rather than counted on demand because it is read on the
     * payout path, where a scan of the chargeback collection per request would
     * put a table scan in front of every organizer checking their balance.
     */
    @Builder.Default
    private int openDisputeCount = 0;

    /**
     * When the account opened — the moment its event published.
     *
     * <p>Distinct from {@code createdAt}: that is an audit timestamp maintained
     * by the framework, this is a business fact that finance reports are
     * entitled to rely on.
     */
    private Instant openedAt;

    /**
     * Timestamp when the account was closed.
     */
    private Instant closedAt;

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

    // The lifecycle enum now lives in shared-library as
    // com.pml.shared.constants.EscrowStatus, so catalog-service can reflect over
    // it to derive the administrator-editable status list.

    // Factory method

    /**
     * Open the account for a published event, already {@code ACTIVE}.
     *
     * <p>No eventTitle or organizerName: they are denormalised copies of catalog
     * and identity data, which means they are two things that can go stale and
     * disagree with their source. The eventId resolves both when a screen needs them.
     */
    public static EventEscrowAccount create(String eventId, String organizerId, Instant eventDate, Instant now) {
        return EventEscrowAccount.builder()
                .accountNumber(String.format("ESC-%s-%d",
                        eventId.substring(0, Math.min(8, eventId.length())).toUpperCase(),
                        PlatformTime.atZone(eventDate).getYear()))
                .eventId(eventId)
                .organizerId(organizerId)
                .status(EscrowStatus.ACTIVE)
                .openedAt(now)
                .holdUntil(eventDate.plus(Duration.ofDays(7)))
                .build();
    }

    // Business methods

    /**
     * Credit funds to escrow (ticket sale).
     */
    public void credit(BigDecimal amount,
            String ticketId,
            String paymentIntentId,
            String description, Instant now) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        if (status == EscrowStatus.CLOSED) {
            throw new IllegalStateException("Cannot credit a closed escrow");
        }
        if (status == EscrowStatus.SUSPENDED) {
            throw new IllegalStateException("Cannot credit a suspended escrow");
        }

        this.currentBalance = this.currentBalance.add(amount);
        this.totalCredited = this.totalCredited.add(amount);

        // No activation step: the account opens ACTIVE when the event publishes.
        // A separate "has taken money" state would repeat what the balance already says.

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
                .timestamp(now)
                .build();
        if (this.transactions == null) {
            this.transactions = new ArrayList<>();
        }
        this.transactions.add(txn);
    }

    /**
     * Debit funds from escrow (refund).
     */
    /**
     * Whether this account already holds the refund debit for {@code refundRequestId}. A refund's
     * debit is looked up by its request id, so debiting the same refund a second time can be refused.
     */
    public boolean hasRefundDebit(String refundRequestId) {
        return refundRequestId != null && transactions != null && transactions.stream().anyMatch(txn ->
                txn.getType() == EscrowTransaction.TransactionType.DEBIT
                        && "REFUND".equals(txn.getCategory())
                        && refundRequestId.equals(txn.getRefundRequestId()));
    }

    public void debitForRefund(BigDecimal amount,
            String ticketId,
            String refundRequestId,
            String description, Instant now) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Insufficient escrow balance");
        }

        this.currentBalance = this.currentBalance.subtract(amount);
        this.totalRefunded = this.totalRefunded.add(amount);

        EscrowTransaction txn = EscrowTransaction.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(EscrowTransaction.TransactionType.DEBIT)
                .category("REFUND")
                .amount(amount)
                .balanceAfter(this.currentBalance)
                .ticketId(ticketId)
                .refundRequestId(refundRequestId)
                .description(description)
                .timestamp(now)
                .build();
        this.transactions.add(txn);
    }

    /**
     * Debit funds from escrow (payout to organizer).
     */
    public void debitForPayout(BigDecimal amount,
            String payoutRequestId,
            String description, Instant now) {
        if (status != EscrowStatus.PAYOUT_ELIGIBLE) {
            throw new IllegalStateException("Escrow is not eligible for payout");
        }
        if (!hasSufficientBalance(amount)) {
            throw new IllegalStateException("Insufficient escrow balance");
        }

        this.currentBalance = this.currentBalance.subtract(amount);
        this.totalDebited = this.totalDebited.add(amount);

        EscrowTransaction txn = EscrowTransaction.builder()
                .id(java.util.UUID.randomUUID().toString())
                .type(EscrowTransaction.TransactionType.DEBIT)
                .category("PAYOUT")
                .amount(amount)
                .balanceAfter(this.currentBalance)
                .payoutRequestId(payoutRequestId)
                .description(description)
                .timestamp(now)
                .build();
        this.transactions.add(txn);

        // Close if fully paid out
        if (this.currentBalance.compareTo(BigDecimal.ZERO) == 0) {
            this.status = EscrowStatus.CLOSED;
        }
    }

    /**
     * Restores a payout debit exactly, when the transfer it paid for failed.
     *
     * <p>The mirror of {@link #debitForPayout}: the balance and the debited total return to what they
     * were, a CREDIT row names the same payout request, and an account the debit emptied and closed
     * is eligible for payout again.
     */
    public void reverseDebitForPayout(BigDecimal amount, String payoutRequestId, Instant now) {
        this.currentBalance = this.currentBalance.add(amount);
        this.totalDebited = this.totalDebited.subtract(amount);

        this.transactions.add(EscrowTransaction.builder()
                .id(payoutRequestId + ":reversal:" + now.toEpochMilli())
                .type(EscrowTransaction.TransactionType.CREDIT)
                .category("PAYOUT_REVERSAL")
                .amount(amount)
                .balanceAfter(this.currentBalance)
                .payoutRequestId(payoutRequestId)
                .description("Payout reversed: " + payoutRequestId)
                .timestamp(now)
                .build());

        if (this.status == EscrowStatus.CLOSED) {
            this.status = EscrowStatus.PAYOUT_ELIGIBLE;
            this.closedAt = null;
        }
    }

    /**
     * Enter the post-event hold period.
     *
     * <p>Triggered by {@code catalog.EventCompleted}. This is the clock-driven
     * hold — a platform-imposed stop is {@link #suspend(String)}, which is a
     * different state precisely because it does not end on a date.
     */
    public void hold() {
        if (status != EscrowStatus.ACTIVE) {
            throw new IllegalStateException("Can only hold an active escrow");
        }
        this.status = EscrowStatus.HOLD;
    }

    /**
     * Mark as payout eligible after hold period.
     */
    public void markPayoutEligible() {
        if (status != EscrowStatus.HOLD) {
            throw new IllegalStateException("Can only make locked escrow payout eligible");
        }
        this.status = EscrowStatus.PAYOUT_ELIGIBLE;
    }

    /**
     * Cancel escrow (event cancelled, all refunded).
     *
     * @param reason The reason for cancellation
     */
    public void cancel(String reason, Instant now) {
        if (this.currentBalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Cannot cancel escrow with remaining balance");
        }
        this.status = EscrowStatus.CLOSED;
        this.closedAt = now;
    }

    /**
     * Cancel escrow (event cancelled, all refunded).
     */
    public void cancel(Instant now) {
        cancel("EVENT_CANCELLED", now);
    }

    /**
     * Close the escrow account after all funds paid out.
     *
     * @param reason The reason for closure
     */
    public void close(String reason, Instant now) {
        if (this.currentBalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Cannot close escrow with remaining balance");
        }
        this.status = EscrowStatus.CLOSED;
        this.closedAt = now;
    }

    // Query helpers

    public boolean hasSufficientBalance(BigDecimal amount) {
        return this.currentBalance.compareTo(amount) >= 0;
    }

    public boolean isPayoutEligible() {
        return status == EscrowStatus.PAYOUT_ELIGIBLE;
    }

    /** In the post-event hold period — a clock running down, not a person deciding. */
    public boolean isOnHold() {
        return status == EscrowStatus.HOLD;
    }

    /** Held by the platform. Only a person lifts this one. */
    public boolean isSuspended() {
        return status == EscrowStatus.SUSPENDED;
    }

    public boolean isActive() {
        return status == EscrowStatus.ACTIVE;
    }

    public boolean isClosed() {
        return status == EscrowStatus.CLOSED;
    }

    public boolean isHoldPeriodPassed(Instant now) {
        return holdUntil != null && now.isAfter(holdUntil);
    }

    public BigDecimal getAvailableForPayout() {
        if (!isPayoutEligible()) {
            return BigDecimal.ZERO;
        }
        return currentBalance;
    }
}
