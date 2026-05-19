package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.ReconciliationItemStatus;
import com.pml.booking.domain.enums.ReconciliationStatus;
import com.pml.booking.domain.enums.ReconciliationType;
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

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconciliation Run - A Single Reconciliation Execution
 *
 * A reconciliation run represents a single execution of the reconciliation
 * process, comparing external records (from gateways, banks) against internal
 * records to identify discrepancies.
 *
 * <h2>Reconciliation Types</h2>
 * <ul>
 *   <li><b>GATEWAY</b>: Compare PawaPay settlement report with PaymentIntent/PaymentAttempt records</li>
 *   <li><b>BANK</b>: Compare bank statement with JournalEntry records affecting bank accounts</li>
 *   <li><b>ESCROW</b>: Verify escrow balances match transaction sums</li>
 * </ul>
 *
 * <h2>Reconciliation Process</h2>
 * <pre>
 *     ┌─────────────────────────────────────────────────────────────────────┐
 *     │                    1. START RECONCILIATION                          │
 *     │  - Create ReconciliationRun with status = RUNNING                   │
 *     │  - Fetch external data (gateway report, bank statement)             │
 *     │  - Query internal records for the same period                       │
 *     └───────────────────────────────┬─────────────────────────────────────┘
 *                                     │
 *                                     ▼
 *     ┌─────────────────────────────────────────────────────────────────────┐
 *     │                    2. MATCHING ALGORITHM                            │
 *     │  For each external record:                                          │
 *     │    - Look for matching internal record (by ID)                      │
 *     │    - If found, compare amounts                                      │
 *     │    - Create ReconciliationItem with appropriate status              │
 *     │  For remaining internal records (not matched):                      │
 *     │    - Create UNMATCHED_INTERNAL items                                │
 *     └───────────────────────────────┬─────────────────────────────────────┘
 *                                     │
 *                                     ▼
 *     ┌─────────────────────────────────────────────────────────────────────┐
 *     │                    3. DETERMINE OUTCOME                             │
 *     │  If all items MATCHED:                                              │
 *     │    → Status = COMPLETED (success)                                   │
 *     │  If any items have discrepancies:                                   │
 *     │    → Status = REQUIRES_REVIEW                                       │
 *     │  If error during processing:                                        │
 *     │    → Status = FAILED                                                │
 *     └───────────────────────────────┬─────────────────────────────────────┘
 *                                     │
 *                                     ▼
 *     ┌─────────────────────────────────────────────────────────────────────┐
 *     │                    4. RESOLUTION (if needed)                        │
 *     │  Human reviews each discrepancy:                                    │
 *     │    - Investigate root cause                                         │
 *     │    - Create adjustment entries if needed                            │
 *     │    - Mark items as resolved                                         │
 *     └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Typical Schedule</h2>
 * <ul>
 *   <li>GATEWAY: Daily, after gateway settlement (usually T+1)</li>
 *   <li>BANK: Daily or weekly, after bank statement is available</li>
 *   <li>ESCROW: Real-time (after each transaction) + daily batch</li>
 * </ul>
 *
 * @see ReconciliationType
 * @see ReconciliationStatus
 * @see ReconciliationItem
 * @since 1.0.0
 */
@Document(collection = "reconciliation_runs")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    @CompoundIndex(name = "type_date_idx", def = "{'type': 1, 'reconciliationDate': -1}"),
    @CompoundIndex(name = "status_date_idx", def = "{'status': 1, 'reconciliationDate': -1}")
})
public class ReconciliationRun {

    /**
     * MongoDB document ID.
     */
    @Id
    private String id;

    /**
     * Run number for human reference.
     *
     * <p>Format: REC-{TYPE}-{YYYYMMDD}-{NNN}</p>
     * <p>Example: REC-GATEWAY-20240115-001</p>
     */
    @Indexed(unique = true)
    private String runNumber;

    /**
     * The business date being reconciled.
     *
     * <p>This is the date of the transactions being compared,
     * not the date the reconciliation was executed.</p>
     */
    @NotNull(message = "Reconciliation date is required")
    @Indexed
    private LocalDate reconciliationDate;

    /**
     * Type of reconciliation being performed.
     *
     * @see ReconciliationType
     */
    @NotNull(message = "Reconciliation type is required")
    @Indexed
    private ReconciliationType type;

    /**
     * Current status of this reconciliation run.
     *
     * @see ReconciliationStatus
     */
    @NotNull(message = "Status is required")
    @Indexed
    @Builder.Default
    private ReconciliationStatus status = ReconciliationStatus.RUNNING;

    /**
     * Description of the data source.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"PawaPay Settlement Report 2024-01-15"</li>
     *   <li>"Zanaco Bank Statement Jan 2024"</li>
     *   <li>"Escrow Balance Verification"</li>
     * </ul>
     */
    private String dataSource;

    // ========================================================================
    // TOTALS
    // ========================================================================

    /**
     * Total amount from external source.
     */
    @Builder.Default
    private BigDecimal expectedTotal = BigDecimal.ZERO;

    /**
     * Total amount from internal records.
     */
    @Builder.Default
    private BigDecimal actualTotal = BigDecimal.ZERO;

    /**
     * Variance between expected and actual totals.
     *
     * <p>Calculated: expectedTotal - actualTotal</p>
     */
    @Builder.Default
    private BigDecimal variance = BigDecimal.ZERO;

    /**
     * Currency code.
     */
    @Builder.Default
    private String currency = "ZMW";

    // ========================================================================
    // COUNTS
    // ========================================================================

    /**
     * Total number of records processed.
     */
    @Builder.Default
    private Integer totalRecords = 0;

    /**
     * Number of records that matched successfully.
     */
    @Builder.Default
    private Integer matchedCount = 0;

    /**
     * Number of records with discrepancies.
     */
    @Builder.Default
    private Integer unmatchedCount = 0;

    /**
     * Number of discrepancies that have been resolved.
     */
    @Builder.Default
    private Integer resolvedCount = 0;

    // ========================================================================
    // ITEMS (Embedded)
    // ========================================================================

    /**
     * List of individual reconciliation items.
     *
     * <p>Each item represents one record being compared. Items are
     * embedded within this document for atomic updates.</p>
     *
     * <p>For large reconciliations (>1000 items), consider using a
     * separate collection with reference to this run.</p>
     */
    @Builder.Default
    private List<ReconciliationItem> items = new ArrayList<>();

    // ========================================================================
    // AUDIT FIELDS
    // ========================================================================

    /**
     * User who initiated this reconciliation run.
     */
    private String runBy;

    /**
     * When the run started.
     */
    @NotNull(message = "Started at is required")
    private Instant startedAt;

    /**
     * When the run completed (or failed).
     */
    private Instant completedAt;

    /**
     * Notes about the reconciliation or its results.
     */
    private String notes;

    /**
     * Error message if the run failed.
     */
    private String errorMessage;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Version
    private Long version;

    // ========================================================================
    // BUSINESS METHODS
    // ========================================================================

    /**
     * Adds a reconciliation item to this run.
     *
     * @param item The item to add
     */
    public void addItem(ReconciliationItem item) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(item);
        updateCounts();
    }

    /**
     * Updates counts based on current items.
     *
     * <p>Call after modifying items list.</p>
     */
    public void updateCounts() {
        if (items == null) {
            totalRecords = 0;
            matchedCount = 0;
            unmatchedCount = 0;
            resolvedCount = 0;
            return;
        }

        totalRecords = items.size();
        matchedCount = (int) items.stream()
                .filter(ReconciliationItem::isMatched)
                .count();
        unmatchedCount = (int) items.stream()
                .filter(ReconciliationItem::isDiscrepancy)
                .count();
        resolvedCount = (int) items.stream()
                .filter(ReconciliationItem::isResolved)
                .count();
    }

    /**
     * Calculates variance based on totals.
     */
    public void calculateVariance() {
        if (expectedTotal != null && actualTotal != null) {
            variance = expectedTotal.subtract(actualTotal);
        }
    }

    /**
     * Marks the run as completed successfully.
     */
    public void complete() {
        if (status != ReconciliationStatus.RUNNING && status != ReconciliationStatus.REQUIRES_REVIEW) {
            throw new IllegalStateException(
                    "Cannot complete run in status " + status
            );
        }
        updateCounts();
        calculateVariance();

        this.status = ReconciliationStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * Marks the run as completed with notes.
     *
     * @param completionNotes Notes about the completion
     */
    public void complete(String completionNotes) {
        complete();
        this.notes = completionNotes;
    }

    /**
     * Marks the run as requiring review due to discrepancies.
     */
    public void requiresReview() {
        this.status = ReconciliationStatus.REQUIRES_REVIEW;
        updateCounts();
        calculateVariance();
    }

    /**
     * Marks the run as failed with an error message.
     *
     * @param errorMessage Description of the failure
     */
    public void fail(String errorMessage) {
        this.status = ReconciliationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
    }

    /**
     * Resolves an item in this run.
     *
     * @param externalId External ID of the item to resolve
     * @param resolution Resolution description
     * @param resolvedBy User resolving the item
     * @return true if item was found and resolved
     */
    public boolean resolveItem(String externalId, String resolution, String resolvedBy) {
        if (items == null) return false;

        for (ReconciliationItem item : items) {
            if (externalId.equals(item.getExternalId())) {
                item.resolve(resolution, resolvedBy);
                updateCounts();

                // If all discrepancies resolved, complete the run
                if (resolvedCount >= unmatchedCount) {
                    this.status = ReconciliationStatus.COMPLETED;
                }
                return true;
            }
        }
        return false;
    }

    // ========================================================================
    // QUERY METHODS
    // ========================================================================

    /**
     * Checks if the run is still in progress.
     *
     * @return true if status is RUNNING
     */
    public boolean isRunning() {
        return status == ReconciliationStatus.RUNNING;
    }

    /**
     * Checks if the run completed successfully.
     *
     * @return true if status is COMPLETED
     */
    public boolean isCompleted() {
        return status == ReconciliationStatus.COMPLETED;
    }

    /**
     * Checks if the run requires manual review.
     *
     * @return true if status is REQUIRES_REVIEW
     */
    public boolean needsReview() {
        return status == ReconciliationStatus.REQUIRES_REVIEW;
    }

    /**
     * Checks if the run failed.
     *
     * @return true if status is FAILED
     */
    public boolean isFailed() {
        return status == ReconciliationStatus.FAILED;
    }

    /**
     * Checks if there are unresolved discrepancies.
     *
     * @return true if unmatched > resolved
     */
    public boolean hasUnresolvedDiscrepancies() {
        return unmatchedCount > resolvedCount;
    }

    /**
     * Gets items that need review.
     *
     * @return List of unresolved discrepancy items
     */
    public List<ReconciliationItem> getItemsNeedingReview() {
        if (items == null) return List.of();
        return items.stream()
                .filter(ReconciliationItem::needsReview)
                .toList();
    }

    /**
     * Gets items by status.
     *
     * @param status The item status to filter by
     * @return List of items with that status
     */
    public List<ReconciliationItem> getItemsByStatus(ReconciliationItemStatus status) {
        if (items == null) return List.of();
        return items.stream()
                .filter(item -> item.getStatus() == status)
                .toList();
    }

    /**
     * Gets the duration of the run in milliseconds.
     *
     * @return Duration in ms, or -1 if not completed
     */
    public long getDurationMs() {
        if (startedAt == null || completedAt == null) {
            return -1;
        }
        return completedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    /**
     * Gets the match rate as a percentage.
     *
     * @return Match rate (0-100), or -1 if no records
     */
    public double getMatchRatePercent() {
        if (totalRecords == null || totalRecords == 0) {
            return -1;
        }
        return (matchedCount * 100.0) / totalRecords;
    }

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates a new reconciliation run.
     *
     * @param runNumber Human-readable run number
     * @param reconciliationDate Date being reconciled
     * @param type Type of reconciliation
     * @param dataSource Description of data source
     * @param runBy User initiating the run
     * @return New ReconciliationRun in RUNNING status
     */
    public static ReconciliationRun create(
            String runNumber,
            LocalDate reconciliationDate,
            ReconciliationType type,
            String dataSource,
            String runBy
    ) {
        return ReconciliationRun.builder()
                .runNumber(runNumber)
                .reconciliationDate(reconciliationDate)
                .type(type)
                .dataSource(dataSource)
                .status(ReconciliationStatus.RUNNING)
                .runBy(runBy)
                .startedAt(Instant.now())
                .build();
    }

    /**
     * Generates a run number.
     *
     * @param type Reconciliation type
     * @param date Reconciliation date
     * @param sequence Sequential number for that day
     * @return Formatted run number
     */
    public static String generateRunNumber(
            ReconciliationType type,
            LocalDate date,
            int sequence
    ) {
        return String.format("REC-%s-%s-%03d",
                type.name(),
                date.toString().replace("-", ""),
                sequence
        );
    }
}
