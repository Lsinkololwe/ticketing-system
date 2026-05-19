package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.ReconciliationItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reconciliation Item - Individual Record in a Reconciliation Run
 *
 * Each reconciliation item represents a single record being compared between
 * external and internal data sources. Items are embedded within a
 * {@link ReconciliationRun} document.
 *
 * <h2>Item Status Flow</h2>
 * <pre>
 * During reconciliation:
 *   ┌────────────────────────────────────────────────────────────────────┐
 *   │ Compare external record with internal record                      │
 *   └────────────────────────────────┬───────────────────────────────────┘
 *                                    │
 *         ┌──────────────────────────┼──────────────────────────────────┐
 *         │                          │                                  │
 *         ▼                          ▼                                  ▼
 *   ┌──────────┐            ┌─────────────────┐             ┌────────────────────┐
 *   │ MATCHED  │            │ AMOUNT_MISMATCH │             │ UNMATCHED_EXTERNAL │
 *   │ (all OK) │            │ (amounts differ)│             │ (missing internal) │
 *   └──────────┘            └─────────────────┘             └────────────────────┘
 *                                                                       or
 *                                                           ┌────────────────────┐
 *                                                           │ UNMATCHED_INTERNAL │
 *                                                           │ (missing external) │
 *                                                           └────────────────────┘
 *
 * Resolution (for discrepancies):
 *   ┌────────────────────────┐
 *   │ Human investigates:    │
 *   │ - Check timing         │
 *   │ - Look for duplicates  │
 *   │ - Verify fees          │
 *   │ - Create adjustment    │
 *   └───────────┬────────────┘
 *               │ resolveItem()
 *               ▼
 *   ┌────────────────────────┐
 *   │ Item marked resolved   │
 *   │ - resolution field set │
 *   │ - resolvedBy set       │
 *   │ - resolvedAt set       │
 *   └────────────────────────┘
 * </pre>
 *
 * <h2>Example Items</h2>
 *
 * <h3>MATCHED Item</h3>
 * <pre>
 * {
 *   externalId: "PAY-001",
 *   internalId: "pi_12345",
 *   externalAmount: 100.00,
 *   internalAmount: 100.00,
 *   status: MATCHED
 * }
 * </pre>
 *
 * <h3>AMOUNT_MISMATCH Item</h3>
 * <pre>
 * {
 *   externalId: "PAY-002",
 *   internalId: "pi_12346",
 *   externalAmount: 97.50,  // Gateway deducted fee
 *   internalAmount: 100.00, // We recorded gross
 *   status: AMOUNT_MISMATCH,
 *   resolution: "Gateway fee of K2.50 not separately recorded. Adjustment entry created.",
 *   resolvedBy: "admin-123",
 *   resolvedAt: "2024-01-16T10:30:00Z"
 * }
 * </pre>
 *
 * <h3>UNMATCHED_EXTERNAL Item</h3>
 * <pre>
 * {
 *   externalId: "PAY-003",
 *   internalId: null,  // Not found internally
 *   externalAmount: 50.00,
 *   status: UNMATCHED_EXTERNAL,
 *   resolution: "Webhook failed. Manual payment record created.",
 *   resolvedBy: "admin-123",
 *   resolvedAt: "2024-01-16T11:00:00Z"
 * }
 * </pre>
 *
 * @see ReconciliationRun
 * @see ReconciliationItemStatus
 * @since 1.0.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationItem {

    /**
     * External record identifier.
     *
     * <p>This is the ID from the external source:</p>
     * <ul>
     *   <li>GATEWAY: Transaction ID from PawaPay report</li>
     *   <li>BANK: Reference number from bank statement</li>
     *   <li>ESCROW: Escrow account ID</li>
     * </ul>
     */
    private String externalId;

    /**
     * Internal record identifier.
     *
     * <p>This is the ID from our internal records:</p>
     * <ul>
     *   <li>GATEWAY: PaymentIntent or PaymentAttempt ID</li>
     *   <li>BANK: JournalEntry ID</li>
     *   <li>ESCROW: (calculated sum, not a specific ID)</li>
     * </ul>
     *
     * <p>Null if UNMATCHED_EXTERNAL (external record not found internally).</p>
     */
    private String internalId;

    /**
     * Amount from external source.
     *
     * <p>Null if UNMATCHED_INTERNAL (internal record not found externally).</p>
     */
    private BigDecimal externalAmount;

    /**
     * Amount from internal records.
     *
     * <p>Null if UNMATCHED_EXTERNAL.</p>
     */
    private BigDecimal internalAmount;

    /**
     * Currency code.
     */
    @Builder.Default
    private String currency = "ZMW";

    /**
     * Matching status of this item.
     *
     * @see ReconciliationItemStatus
     */
    private ReconciliationItemStatus status;

    /**
     * Resolution description (for discrepancies).
     *
     * <p>Explains how the discrepancy was resolved:</p>
     * <ul>
     *   <li>"Gateway fee not separately recorded - adjustment entry created"</li>
     *   <li>"Timing difference - payment settled in next batch"</li>
     *   <li>"Duplicate detected - marked for reversal"</li>
     *   <li>"Webhook missed - manual record created"</li>
     * </ul>
     */
    private String resolution;

    /**
     * User who resolved this discrepancy.
     */
    private String resolvedBy;

    /**
     * Timestamp when this discrepancy was resolved.
     */
    private Instant resolvedAt;

    /**
     * Additional notes about this item.
     */
    private String notes;

    /**
     * Reference to journal entry created as resolution.
     *
     * <p>If resolution involved creating an adjustment entry.</p>
     */
    private String adjustmentJournalEntryId;

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Checks if this item is matched successfully.
     *
     * @return true if records match and amounts match
     */
    public boolean isMatched() {
        return status == ReconciliationItemStatus.MATCHED;
    }

    /**
     * Checks if this item has a discrepancy.
     *
     * @return true if any type of mismatch
     */
    public boolean isDiscrepancy() {
        return status != ReconciliationItemStatus.MATCHED;
    }

    /**
     * Checks if this item has been resolved.
     *
     * @return true if resolution has been recorded
     */
    public boolean isResolved() {
        return resolution != null && !resolution.isBlank();
    }

    /**
     * Checks if this item needs review.
     *
     * @return true if discrepancy and not yet resolved
     */
    public boolean needsReview() {
        return isDiscrepancy() && !isResolved();
    }

    /**
     * Calculates the amount variance (if both amounts present).
     *
     * @return External - Internal, or null if either is null
     */
    public BigDecimal getVariance() {
        if (externalAmount == null || internalAmount == null) {
            return null;
        }
        return externalAmount.subtract(internalAmount);
    }

    /**
     * Gets the absolute variance.
     *
     * @return Absolute value of variance, or null
     */
    public BigDecimal getAbsoluteVariance() {
        BigDecimal variance = getVariance();
        return variance != null ? variance.abs() : null;
    }

    // ========================================================================
    // STATE TRANSITION METHODS
    // ========================================================================

    /**
     * Marks this item as resolved.
     *
     * @param resolution Description of how it was resolved
     * @param resolvedBy User who resolved it
     */
    public void resolve(String resolution, String resolvedBy) {
        this.resolution = resolution;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = Instant.now();
    }

    /**
     * Marks this item as resolved with an adjustment journal entry.
     *
     * @param resolution Description of resolution
     * @param resolvedBy User who resolved it
     * @param journalEntryId ID of adjustment journal entry
     */
    public void resolveWithAdjustment(String resolution, String resolvedBy, String journalEntryId) {
        resolve(resolution, resolvedBy);
        this.adjustmentJournalEntryId = journalEntryId;
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates a matched item.
     *
     * @param externalId External record ID
     * @param internalId Internal record ID
     * @param amount Matching amount
     * @return New matched item
     */
    public static ReconciliationItem matched(String externalId, String internalId, BigDecimal amount) {
        return ReconciliationItem.builder()
                .externalId(externalId)
                .internalId(internalId)
                .externalAmount(amount)
                .internalAmount(amount)
                .status(ReconciliationItemStatus.MATCHED)
                .build();
    }

    /**
     * Creates an amount mismatch item.
     *
     * @param externalId External record ID
     * @param internalId Internal record ID
     * @param externalAmount Amount from external source
     * @param internalAmount Amount from internal records
     * @return New mismatch item
     */
    public static ReconciliationItem amountMismatch(
            String externalId,
            String internalId,
            BigDecimal externalAmount,
            BigDecimal internalAmount
    ) {
        return ReconciliationItem.builder()
                .externalId(externalId)
                .internalId(internalId)
                .externalAmount(externalAmount)
                .internalAmount(internalAmount)
                .status(ReconciliationItemStatus.AMOUNT_MISMATCH)
                .build();
    }

    /**
     * Creates an unmatched external item (exists externally, not internally).
     *
     * @param externalId External record ID
     * @param externalAmount Amount from external source
     * @return New unmatched external item
     */
    public static ReconciliationItem unmatchedExternal(String externalId, BigDecimal externalAmount) {
        return ReconciliationItem.builder()
                .externalId(externalId)
                .externalAmount(externalAmount)
                .status(ReconciliationItemStatus.UNMATCHED_EXTERNAL)
                .build();
    }

    /**
     * Creates an unmatched internal item (exists internally, not externally).
     *
     * @param internalId Internal record ID
     * @param internalAmount Amount from internal records
     * @return New unmatched internal item
     */
    public static ReconciliationItem unmatchedInternal(String internalId, BigDecimal internalAmount) {
        return ReconciliationItem.builder()
                .internalId(internalId)
                .internalAmount(internalAmount)
                .status(ReconciliationItemStatus.UNMATCHED_INTERNAL)
                .build();
    }
}
