package com.pml.booking.domain.model;

import com.pml.shared.constants.EscrowStatus;
import com.pml.shared.constants.WorkflowSemantic;
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
@Document(collection = "booking_escrow_accounts")
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
     * <p>Another documented delta from ET-FIN-001, which keys the account on
     * {@code eventId} alone. It stays because it is exposed on the GraphQL
     * {@code AccountSummary} type and the organizer finance screen queries it;
     * removing it is an API break needing frontend coordination, not a field
     * deletion.
     */
    @Indexed(unique = true)
    private String accountNumber;

    @NotBlank(message = "Event ID is required")
    @Indexed(unique = true)
    private String eventId;

    /**
     * Denormalised event title.
     *
     * <p><b>Not in ET-FIN-001's document, and kept anyway — deliberately, and
     * temporarily.</b> The organizer's payout screen and the event financial
     * report both display it. Removing it leaves two choices, and both are
     * worse than a documented delta: blank the labels, or fabricate
     * {@code "Event " + id} and present it as the name of a real event.
     *
     * <p>The spec is right that this does not belong here — it is a copy of
     * catalog's data that can go stale and disagree with its source. Removing it
     * needs a catalog lookup on those two surfaces, which is a functional change
     * rather than a field deletion.
     */
    private String eventTitle;

    @NotBlank(message = "Organizer ID is required")
    @Indexed
    private String organizerId;

    /** Denormalised organizer name. Same delta and same reason as eventTitle. */
    private String organizerName;

    /**
     * Commission collected from this event's sales.
     *
     * <p>ET-FIN-001 puts commission on platform account {@code 2020 Pending
     * Commission}, not on the escrow. Kept for now because AccountSummary
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
    @Indexed
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
    @Indexed
    private WorkflowSemantic statusSemantic;

    /**
     * When the account became payout-eligible.
     *
     * <p>Also not in ET-FIN-001's document, and kept for the same reason: the
     * organizer's dashboard shows "eligible since". The spec's position is
     * defensible — the status transition and its audit entry already record
     * this — but reading it back out of the audit trail is work this increment
     * does not do, and dropping the field silently blanks the column.
     */
    private Instant payoutEligibleAt;

    /**
     * When the hold period ends: {@code endsAt + finance.escrow.hold-period}.
     *
     * <p>Set by {@code catalog.EventCompleted}. A date, which is what separates
     * {@link EscrowStatus#HOLD} from {@link EscrowStatus#SUSPENDED} — the latter
     * has no date because it ends when a person decides it does.
     */
    private LocalDateTime holdUntil;

    /**
     * Open chargebacks against this event's tickets.
     *
     * <p>ET-FIN-001 R4 blocks {@code PAYOUT_ELIGIBLE} while this is above zero:
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
     * by the framework, this is a business fact ET-FIN-001 names and reports
     * are entitled to rely on.
     */
    private Instant openedAt;

    /**
     * Timestamp when the account was closed.
     */
    private LocalDateTime closedAt;

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
     * Open the account for a published event (ET-FIN-001 R4).
     *
     * <p>No accountNumber, eventTitle or organizerName: the spec's document has
     * none of them. They were denormalised copies of catalog and identity data,
     * which means they were also three things that could go stale and disagree
     * with their source. The eventId resolves all three when a screen needs them.
     */
    public static EventEscrowAccount create(String eventId, String organizerId, LocalDateTime eventDate) {
        return EventEscrowAccount.builder()
                .accountNumber(String.format("ESC-%s-%d",
                        eventId.substring(0, Math.min(8, eventId.length())).toUpperCase(),
                        eventDate.getYear()))
                .eventId(eventId)
                .organizerId(organizerId)
                .status(EscrowStatus.ACTIVE)
                .openedAt(Instant.now())
                .holdUntil(eventDate.plusDays(7))
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
        if (status == EscrowStatus.CLOSED) {
            throw new IllegalStateException("Cannot credit a closed escrow");
        }
        if (status == EscrowStatus.SUSPENDED) {
            throw new IllegalStateException("Cannot credit a suspended escrow");
        }

        this.currentBalance = this.currentBalance.add(amount);
        this.totalCredited = this.totalCredited.add(amount);

        // No activation step: ET-FIN-001 R4 opens the account ACTIVE when the
        // event publishes. The old CREATED -> ACTIVE hop existed only to record
        // "has taken money", which the balance already says.

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
                .timestamp(Instant.now())
                .build();
        this.transactions.add(txn);

        // Close if fully paid out
        if (this.currentBalance.compareTo(BigDecimal.ZERO) == 0) {
            this.status = EscrowStatus.CLOSED;
        }
    }

    /**
     * Enter the post-event hold period (ET-FIN-001 R4).
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
     * Stop the account by decision — fraud review, a dispute, a compliance hold.
     *
     * <p>Reachable from any live state, unlike {@link #hold(String)}: fraud is
     * found whenever it is found, not only while an event is still selling.
     */
    public void suspend() {
        if (status == EscrowStatus.CLOSED) {
            throw new IllegalStateException("Cannot suspend a closed escrow");
        }
        this.status = EscrowStatus.SUSPENDED;
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
    public void cancel(String reason) {
        if (this.currentBalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Cannot cancel escrow with remaining balance");
        }
        this.status = EscrowStatus.CLOSED;
        this.closedAt = LocalDateTime.now();
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

    public boolean isHoldPeriodPassed() {
        return holdUntil != null && LocalDateTime.now().isAfter(holdUntil);
    }

    public BigDecimal getAvailableForPayout() {
        if (!isPayoutEligible()) {
            return BigDecimal.ZERO;
        }
        return currentBalance;
    }
}
