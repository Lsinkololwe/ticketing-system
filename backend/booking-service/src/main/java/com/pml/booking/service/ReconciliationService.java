package com.pml.booking.service;

import com.pml.booking.domain.enums.ReconciliationItemStatus;
import com.pml.booking.domain.enums.ReconciliationStatus;
import com.pml.booking.domain.enums.ReconciliationType;
import com.pml.booking.domain.model.ReconciliationItem;
import com.pml.booking.domain.model.ReconciliationRun;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Reconciliation Service Interface
 *
 * <p>Manages automated reconciliation processes to ensure financial data
 * integrity across multiple systems. Reconciliation compares internal
 * records with external sources to identify discrepancies.</p>
 *
 * <h2>Reconciliation Types</h2>
 *
 * <h3>GATEWAY Reconciliation</h3>
 * <p>Compares payment gateway records with internal payment records:</p>
 * <ul>
 *   <li><b>Source</b>: Gateway settlement reports (pawaPay)</li>
 *   <li><b>Target</b>: PaymentIntent and PaymentAttempt records</li>
 *   <li><b>Frequency</b>: Daily (after settlement batch)</li>
 *   <li><b>Key fields</b>: Transaction ID, amount, status, date</li>
 * </ul>
 *
 * <h3>BANK Reconciliation</h3>
 * <p>Compares bank statements with internal ledger:</p>
 * <ul>
 *   <li><b>Source</b>: Bank statement (manual upload or API)</li>
 *   <li><b>Target</b>: Platform operating account balance</li>
 *   <li><b>Frequency</b>: Weekly or on-demand</li>
 *   <li><b>Key fields</b>: Reference, amount, date</li>
 * </ul>
 *
 * <h3>ESCROW Reconciliation</h3>
 * <p>Verifies escrow account balances match transaction sums:</p>
 * <ul>
 *   <li><b>Source</b>: EventEscrowAccount.currentBalance</li>
 *   <li><b>Target</b>: SUM(credits) - SUM(debits) from transactions</li>
 *   <li><b>Frequency</b>: Daily</li>
 *   <li><b>Key fields</b>: Account ID, expected balance, actual balance</li>
 * </ul>
 *
 * <h2>Reconciliation Process</h2>
 * <pre>
 * 1. Start Run → RUNNING
 * 2. Load external data
 * 3. Load internal data
 * 4. Match records
 * 5. Identify discrepancies
 * 6. → All matched: COMPLETED
 *    → Discrepancies found: REQUIRES_REVIEW
 *    → Error occurred: FAILED
 * </pre>
 *
 * <h2>Discrepancy Resolution</h2>
 * <ul>
 *   <li><b>Timing differences</b>: Wait for settlement</li>
 *   <li><b>Missing records</b>: Investigate and create adjustment</li>
 *   <li><b>Amount mismatch</b>: Verify fees, create adjustment if needed</li>
 *   <li><b>Duplicate records</b>: Investigate and correct</li>
 * </ul>
 *
 * @see com.pml.booking.domain.model.ReconciliationRun
 * @see com.pml.booking.domain.model.ReconciliationItem
 * @since 1.0.0
 */
public interface ReconciliationService {

    // ========================================================================
    // GATEWAY RECONCILIATION
    // ========================================================================

    /**
     * Starts a gateway reconciliation run.
     *
     * <p>Compares payment gateway settlement data with internal payment records.
     * This identifies missing payments, duplicate charges, or amount discrepancies.</p>
     *
     * @param reconciliationDate The date to reconcile
     * @param gatewayData        Settlement data from gateway (transaction ID → amount)
     * @param runBy              User/system starting the run
     * @return Created reconciliation run
     */
    Mono<ReconciliationRun> startGatewayReconciliation(
            LocalDate reconciliationDate,
            Map<String, BigDecimal> gatewayData,
            String runBy
    );

    /**
     * Starts a gateway reconciliation from a settlement file.
     *
     * <p>Parses the settlement file and initiates reconciliation.</p>
     *
     * @param reconciliationDate The date to reconcile
     * @param settlementFileContent Settlement file content (CSV or JSON)
     * @param fileFormat           File format ("csv" or "json")
     * @param runBy                User/system starting the run
     * @return Created reconciliation run
     */
    Mono<ReconciliationRun> startGatewayReconciliationFromFile(
            LocalDate reconciliationDate,
            String settlementFileContent,
            String fileFormat,
            String runBy
    );

    // ========================================================================
    // BANK RECONCILIATION
    // ========================================================================

    /**
     * Starts a bank reconciliation run.
     *
     * <p>Compares bank statement entries with internal ledger transactions.
     * The platform operating account balance should match the bank balance.</p>
     *
     * @param reconciliationDate The date to reconcile
     * @param bankData           Bank statement entries (reference → amount)
     * @param openingBalance     Bank statement opening balance
     * @param closingBalance     Bank statement closing balance
     * @param runBy              User/system starting the run
     * @return Created reconciliation run
     */
    Mono<ReconciliationRun> startBankReconciliation(
            LocalDate reconciliationDate,
            List<BankStatementEntry> bankData,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            String runBy
    );

    /**
     * Bank statement entry for reconciliation.
     */
    record BankStatementEntry(
            String reference,
            LocalDate date,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal balance
    ) {}

    // ========================================================================
    // ESCROW RECONCILIATION
    // ========================================================================

    /**
     * Starts an escrow reconciliation run.
     *
     * <p>Verifies that each escrow account's balance matches the sum of
     * its transactions. Identifies any balance discrepancies.</p>
     *
     * @param reconciliationDate The date to reconcile
     * @param runBy              User/system starting the run
     * @return Created reconciliation run
     */
    Mono<ReconciliationRun> startEscrowReconciliation(
            LocalDate reconciliationDate,
            String runBy
    );

    /**
     * Reconciles a specific escrow account.
     *
     * @param escrowAccountId The escrow account ID
     * @param runBy           User/system running the check
     * @return Reconciliation result for the account
     */
    Mono<EscrowReconciliationResult> reconcileEscrowAccount(
            String escrowAccountId,
            String runBy
    );

    /**
     * Result of escrow account reconciliation.
     */
    record EscrowReconciliationResult(
            String escrowAccountId,
            BigDecimal recordedBalance,
            BigDecimal calculatedBalance,
            BigDecimal variance,
            boolean isBalanced,
            List<String> discrepancyDetails
    ) {}

    // ========================================================================
    // ESCROW-JOURNAL CROSS-VERIFICATION
    // ========================================================================

    /**
     * Starts an escrow-journal cross-verification run for OPEN accounts only.
     *
     * <p>By default, only verifies accounts that are NOT CLOSED and NOT CANCELLED.
     * This is a performance optimization since closed accounts have zero balance
     * and are immutable.</p>
     *
     * <p>Verifies that each escrow account's balance matches the calculated balance
     * from journal entries for the corresponding Chart of Accounts entry (2010-xxx).
     * This ensures dual-tracking consistency between operational escrow accounts
     * and the double-entry bookkeeping ledger.</p>
     *
     * <h3>What is Compared</h3>
     * <ul>
     *   <li><b>Escrow Balance</b>: EventEscrowAccount.currentBalance</li>
     *   <li><b>Journal Balance</b>: AccountingService.getAccountBalance("2010-{eventId}")</li>
     * </ul>
     *
     * <h3>Accounts Verified (by default)</h3>
     * <ul>
     *   <li>CREATED - New accounts</li>
     *   <li>ACTIVE - Receiving ticket sales</li>
     *   <li>LOCKED - In hold period</li>
     *   <li>PAYOUT_ELIGIBLE - Ready for payout</li>
     *   <li>PROCESSING_PAYOUT - Payout in progress</li>
     * </ul>
     *
     * <h3>Accounts Skipped (by default)</h3>
     * <ul>
     *   <li>CLOSED - Already paid out, balance is K0</li>
     *   <li>CANCELLED - Event cancelled, balance is K0</li>
     * </ul>
     *
     * @param reconciliationDate The date to reconcile
     * @param runBy              User/system starting the run
     * @return Created reconciliation run
     */
    Mono<ReconciliationRun> startEscrowJournalReconciliation(
            LocalDate reconciliationDate,
            String runBy
    );

    /**
     * Starts an escrow-journal cross-verification run with option to include closed accounts.
     *
     * <p>Use includeClosed=true for full audit to verify ALL accounts including
     * CLOSED and CANCELLED. This is slower but provides complete verification.</p>
     *
     * @param reconciliationDate The date to reconcile
     * @param runBy              User/system starting the run
     * @param includeClosed      If true, includes CLOSED and CANCELLED accounts
     * @return Created reconciliation run
     */
    Mono<ReconciliationRun> startEscrowJournalReconciliation(
            LocalDate reconciliationDate,
            String runBy,
            boolean includeClosed
    );

    /**
     * Verifies a single escrow account against its journal entries.
     *
     * <p>Compares the stored balance in EventEscrowAccount with the calculated
     * balance from all POSTED journal entries for account code 2010-{eventId}.</p>
     *
     * @param eventId The event ID
     * @param runBy   User/system running the check
     * @return Cross-verification result
     */
    Mono<EscrowJournalVerificationResult> verifyEscrowJournalConsistency(
            String eventId,
            String runBy
    );

    /**
     * Verifies all escrow accounts against their journal entries.
     *
     * @param runBy User/system running the check
     * @return Stream of verification results
     */
    Flux<EscrowJournalVerificationResult> verifyAllEscrowJournalConsistency(String runBy);

    /**
     * Result of escrow-journal cross-verification.
     *
     * <p>Contains comparison data between EventEscrowAccount balance and
     * the calculated balance from journal entries.</p>
     */
    record EscrowJournalVerificationResult(
            /**
             * The event ID being verified
             */
            String eventId,

            /**
             * The escrow account ID (if exists)
             */
            String escrowAccountId,

            /**
             * The journal account code (e.g., "2010-abc12345")
             */
            String journalAccountCode,

            /**
             * Balance from EventEscrowAccount.currentBalance
             */
            BigDecimal escrowBalance,

            /**
             * Balance calculated from journal entries
             */
            BigDecimal journalBalance,

            /**
             * Difference: escrowBalance - journalBalance
             */
            BigDecimal variance,

            /**
             * Whether the balances match (within tolerance)
             */
            boolean isConsistent,

            /**
             * Verification status
             */
            VerificationStatus status,

            /**
             * Details about any discrepancies found
             */
            List<String> details
    ) {}

    /**
     * Status of escrow-journal verification.
     */
    enum VerificationStatus {
        /**
         * Both escrow account and journal entries exist and balances match.
         */
        CONSISTENT,

        /**
         * Both exist but balances don't match.
         */
        BALANCE_MISMATCH,

        /**
         * Escrow account exists but no journal account entry found.
         */
        MISSING_JOURNAL_ACCOUNT,

        /**
         * Journal account exists but no EventEscrowAccount found.
         */
        ORPHANED_JOURNAL_ACCOUNT,

        /**
         * Neither escrow account nor journal entries exist for this event.
         */
        NOT_FOUND
    }

    // ========================================================================
    // ITEM RESOLUTION
    // ========================================================================

    /**
     * Resolves a reconciliation discrepancy item.
     *
     * <p>Records how the discrepancy was resolved:</p>
     * <ul>
     *   <li>Timing difference - will clear in next period</li>
     *   <li>Adjustment posted - journal entry created</li>
     *   <li>Error in external data - gateway/bank corrected</li>
     *   <li>Write-off approved - loss accepted</li>
     * </ul>
     *
     * @param runId       The reconciliation run ID
     * @param externalId  The external record ID
     * @param resolution  Resolution description
     * @param resolvedBy  User resolving the item
     * @return Updated reconciliation run
     */
    Mono<ReconciliationRun> resolveItem(
            String runId,
            String externalId,
            String resolution,
            String resolvedBy
    );

    /**
     * Resolves multiple items at once.
     *
     * @param runId       The reconciliation run ID
     * @param resolutions Map of external ID to resolution
     * @param resolvedBy  User resolving the items
     * @return Updated reconciliation run
     */
    Mono<ReconciliationRun> resolveItems(
            String runId,
            Map<String, String> resolutions,
            String resolvedBy
    );

    /**
     * Creates an adjustment journal entry for a reconciliation item.
     *
     * <p>Used when the discrepancy requires a ledger correction.</p>
     *
     * @param runId           The reconciliation run ID
     * @param externalId      The external record ID
     * @param adjustmentAmount Amount to adjust
     * @param description     Adjustment description
     * @param approvedBy      Admin approving the adjustment
     * @return Created journal entry ID
     */
    Mono<String> createAdjustmentEntry(
            String runId,
            String externalId,
            BigDecimal adjustmentAmount,
            String description,
            String approvedBy
    );

    // ========================================================================
    // RUN MANAGEMENT
    // ========================================================================

    /**
     * Completes a reconciliation run.
     *
     * <p>Only possible when all items are resolved.</p>
     *
     * @param runId The run ID
     * @param notes Completion notes
     * @return Completed reconciliation run
     */
    Mono<ReconciliationRun> completeRun(String runId, String notes);

    /**
     * Fails a reconciliation run.
     *
     * <p>Used when the run cannot be completed due to errors.</p>
     *
     * @param runId  The run ID
     * @param reason Failure reason
     * @return Failed reconciliation run
     */
    Mono<ReconciliationRun> failRun(String runId, String reason);

    /**
     * Cancels a reconciliation run.
     *
     * <p>Used when the run needs to be restarted with corrected data.</p>
     *
     * @param runId  The run ID
     * @param reason Cancellation reason
     * @return Cancelled reconciliation run
     */
    Mono<ReconciliationRun> cancelRun(String runId, String reason);

    // ========================================================================
    // QUERIES
    // ========================================================================

    /**
     * Finds all reconciliation runs.
     *
     * @return All reconciliation runs
     */
    Flux<ReconciliationRun> findAll();

    /**
     * Finds a reconciliation run by ID.
     *
     * @param id The run ID
     * @return The reconciliation run, or empty if not found
     */
    Mono<ReconciliationRun> findById(String id);

    /**
     * Finds reconciliation runs by type.
     *
     * @param type The reconciliation type
     * @return All runs of the specified type
     */
    Flux<ReconciliationRun> findByType(ReconciliationType type);

    /**
     * Finds reconciliation runs by status.
     *
     * @param status The run status
     * @return All runs with the specified status
     */
    Flux<ReconciliationRun> findByStatus(ReconciliationStatus status);

    /**
     * Finds reconciliation runs within a date range.
     *
     * @param startDate Start date (inclusive)
     * @param endDate   End date (inclusive)
     * @return All runs in the date range
     */
    Flux<ReconciliationRun> findByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * Finds runs requiring review.
     *
     * <p>Returns runs with REQUIRES_REVIEW status that have unresolved items.</p>
     *
     * @return Runs needing attention
     */
    Flux<ReconciliationRun> findRequiringReview();

    /**
     * Finds unmatched items across all runs.
     *
     * @param status Item status to filter by
     * @return All unresolved items with the specified status
     */
    Flux<ReconciliationItemWithRun> findItemsByStatus(ReconciliationItemStatus status);

    /**
     * Reconciliation item with its parent run context.
     */
    record ReconciliationItemWithRun(
            String runId,
            ReconciliationType runType,
            LocalDate reconciliationDate,
            ReconciliationItem item
    ) {}

    // ========================================================================
    // STATISTICS & REPORTING
    // ========================================================================

    /**
     * Gets a summary of reconciliation status.
     *
     * @param type The reconciliation type (null for all types)
     * @return Summary statistics
     */
    Mono<ReconciliationSummary> getSummary(ReconciliationType type);

    /**
     * Reconciliation summary statistics.
     */
    record ReconciliationSummary(
            long totalRuns,
            long completedRuns,
            long pendingReviewRuns,
            long failedRuns,
            BigDecimal totalVariance,
            BigDecimal resolvedVariance,
            BigDecimal unresolvedVariance,
            LocalDate lastCompletedDate,
            LocalDate oldestPendingDate
    ) {}

    /**
     * Gets detailed reconciliation report for a date range.
     *
     * @param type      Reconciliation type
     * @param startDate Start date
     * @param endDate   End date
     * @return Detailed report
     */
    Mono<ReconciliationReport> generateReport(
            ReconciliationType type,
            LocalDate startDate,
            LocalDate endDate
    );

    /**
     * Detailed reconciliation report.
     */
    record ReconciliationReport(
            ReconciliationType type,
            LocalDate startDate,
            LocalDate endDate,
            int runCount,
            int itemCount,
            int matchedCount,
            int unmatchedCount,
            int resolvedCount,
            BigDecimal expectedTotal,
            BigDecimal actualTotal,
            BigDecimal variance,
            List<UnresolvedItem> unresolvedItems
    ) {}

    /**
     * Unresolved item for reporting.
     */
    record UnresolvedItem(
            String runId,
            LocalDate reconciliationDate,
            String externalId,
            String internalId,
            BigDecimal externalAmount,
            BigDecimal internalAmount,
            ReconciliationItemStatus status,
            int daysPending
    ) {}

    // ========================================================================
    // AUTOMATED SCHEDULING
    // ========================================================================

    /**
     * Processes scheduled reconciliations.
     *
     * <p>Called by scheduler to run daily reconciliations:</p>
     * <ul>
     *   <li>Gateway reconciliation (after settlement time)</li>
     *   <li>Escrow reconciliation (end of day)</li>
     * </ul>
     *
     * @return Number of runs started
     */
    Mono<Integer> processScheduledReconciliations();

    /**
     * Sends alerts for reconciliations requiring attention.
     *
     * <p>Notifies admins of:</p>
     * <ul>
     *   <li>Runs requiring review for more than X days</li>
     *   <li>Large unresolved variances</li>
     *   <li>Failed runs</li>
     * </ul>
     *
     * @return Number of alerts sent
     */
    Mono<Integer> sendReconciliationAlerts();
}
