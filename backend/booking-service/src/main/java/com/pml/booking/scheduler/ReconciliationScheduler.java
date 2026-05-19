package com.pml.booking.scheduler;

import com.pml.booking.domain.enums.ReconciliationType;
import com.pml.booking.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Reconciliation Scheduler
 *
 * <p>Automates daily reconciliation processes to ensure financial data integrity.
 * All times are in Central Africa Time (CAT, UTC+2).</p>
 *
 * <h2>Schedule</h2>
 * <table border="1">
 *   <tr><th>Job</th><th>Time (CAT)</th><th>Purpose</th></tr>
 *   <tr><td>Gateway Reconciliation</td><td>3:00 AM</td><td>After gateway settlement (2 AM)</td></tr>
 *   <tr><td>Escrow Reconciliation</td><td>4:00 AM</td><td>Verify escrow balances</td></tr>
 *   <tr><td>Alert Check</td><td>8:00 AM</td><td>Before business hours</td></tr>
 * </table>
 *
 * <h2>Alert Thresholds</h2>
 * <ul>
 *   <li>Pending > 3 days: HIGH priority</li>
 *   <li>Variance > K1,000: CRITICAL priority</li>
 *   <li>Unmatched > 10 items: HIGH priority</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;

    @Value("${reconciliation.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${reconciliation.scheduler.system-user:SYSTEM_SCHEDULER}")
    private String systemUser;

    /**
     * Gateway Reconciliation - Daily at 3:00 AM CAT (1:00 AM UTC).
     *
     * <p>Runs after PawaPay sends their daily settlement file (typically around 2 AM).
     * Compares gateway transactions with internal PaymentIntent records.</p>
     *
     * <h2>What This Job Does</h2>
     * <ol>
     *   <li>Fetches yesterday's gateway settlement data</li>
     *   <li>Starts gateway reconciliation run</li>
     *   <li>Matches transactions with internal records</li>
     *   <li>Flags discrepancies for review</li>
     * </ol>
     *
     * <p>Note: In production, the settlement file should be fetched from
     * PawaPay's SFTP server or API. For now, this triggers manual reconciliation.</p>
     */
    @Scheduled(cron = "0 0 1 * * ?", zone = "UTC") // 3:00 AM CAT = 1:00 AM UTC
    public void runGatewayReconciliation() {
        if (!schedulerEnabled) {
            log.debug("Scheduled reconciliation is disabled");
            return;
        }

        log.info("Starting scheduled gateway reconciliation for yesterday's transactions");

        LocalDate yesterday = LocalDate.now().minusDays(1);

        // TODO: In production, fetch settlement file from PawaPay SFTP/API
        // For now, log that manual upload is required
        log.info("Gateway reconciliation for {} - awaiting settlement file upload", yesterday);

        // The actual reconciliation happens when the settlement file is uploaded
        // via startGatewayReconciliationFromFile() endpoint
    }

    /**
     * Escrow Reconciliation - Daily at 4:00 AM CAT (2:00 AM UTC).
     *
     * <p>Verifies that all escrow account balances match their transaction history.
     * This catches any discrepancies from failed transactions, race conditions,
     * or data corruption.</p>
     *
     * <h2>What This Job Does</h2>
     * <ol>
     *   <li>Loads all active escrow accounts</li>
     *   <li>For each account, recalculates balance from transactions</li>
     *   <li>Compares calculated balance with recorded balance</li>
     *   <li>Creates reconciliation items for any variances</li>
     * </ol>
     */
    @Scheduled(cron = "0 0 2 * * ?", zone = "UTC") // 4:00 AM CAT = 2:00 AM UTC
    public void runEscrowReconciliation() {
        if (!schedulerEnabled) {
            log.debug("Scheduled reconciliation is disabled");
            return;
        }

        log.info("Starting scheduled escrow reconciliation");

        LocalDate today = LocalDate.now();

        reconciliationService.startEscrowReconciliation(today, systemUser)
                .doOnSuccess(run -> {
                    log.info("Escrow reconciliation completed: {} - {} matched, {} discrepancies",
                            run.getRunNumber(),
                            run.getMatchedCount(),
                            run.getUnmatchedCount());

                    if (run.getUnmatchedCount() > 0) {
                        log.warn("ALERT: Escrow reconciliation found {} discrepancies requiring review",
                                run.getUnmatchedCount());
                    }
                })
                .doOnError(error -> log.error("Failed to run scheduled escrow reconciliation", error))
                .subscribe();
    }

    /**
     * Escrow-Journal Cross-Verification - Daily at 4:30 AM CAT (2:30 AM UTC).
     *
     * <p>Verifies that EventEscrowAccount balances match the calculated balances
     * from journal entries. This is a critical integrity check that ensures
     * dual-tracking consistency between operational escrow accounts and the
     * double-entry bookkeeping ledger.</p>
     *
     * <h2>What This Job Does</h2>
     * <ol>
     *   <li>Loads all EventEscrowAccounts</li>
     *   <li>For each account, gets the journal entry balance (2010-xxx account)</li>
     *   <li>Compares escrow.currentBalance with calculated journal balance</li>
     *   <li>Reports any inconsistencies</li>
     * </ol>
     *
     * <h2>Why This Matters</h2>
     * <p>If escrow balances and journal entries diverge, it indicates a bug where
     * one system was updated without the other. Both must stay in sync for
     * accurate financial reporting.</p>
     */
    @Scheduled(cron = "0 30 2 * * ?", zone = "UTC") // 4:30 AM CAT = 2:30 AM UTC
    public void runEscrowJournalCrossVerification() {
        if (!schedulerEnabled) {
            log.debug("Scheduled reconciliation is disabled");
            return;
        }

        log.info("Starting scheduled escrow-journal cross-verification");

        LocalDate today = LocalDate.now();

        reconciliationService.startEscrowJournalReconciliation(today, systemUser)
                .doOnSuccess(run -> {
                    log.info("Escrow-journal cross-verification completed: {} - {} consistent, {} inconsistencies",
                            run.getRunNumber(),
                            run.getMatchedCount(),
                            run.getUnmatchedCount());

                    if (run.getUnmatchedCount() > 0) {
                        log.error("CRITICAL: Escrow-journal inconsistencies detected! {} accounts have balance mismatches",
                                run.getUnmatchedCount());
                        log.error("This indicates a bug where escrow was updated without corresponding journal entries (or vice versa)");
                    }
                })
                .doOnError(error -> log.error("Failed to run scheduled escrow-journal cross-verification", error))
                .subscribe();
    }

    /**
     * Reconciliation Alert Check - Daily at 8:00 AM CAT (6:00 AM UTC).
     *
     * <p>Reviews pending reconciliation items and sends alerts for:
     * - Items pending for more than 3 days
     * - High-value variances (> K1,000)
     * - Runs with more than 10 unmatched items</p>
     *
     * <p>This runs before business hours to allow finance team to address
     * issues at the start of their workday.</p>
     */
    @Scheduled(cron = "0 0 6 * * ?", zone = "UTC") // 8:00 AM CAT = 6:00 AM UTC
    public void runReconciliationAlertCheck() {
        if (!schedulerEnabled) {
            log.debug("Scheduled reconciliation is disabled");
            return;
        }

        log.info("Starting scheduled reconciliation alert check");

        reconciliationService.sendReconciliationAlerts()
                .doOnSuccess(alertCount -> {
                    if (alertCount > 0) {
                        log.warn("Sent {} reconciliation alerts", alertCount);
                    } else {
                        log.info("No reconciliation alerts needed");
                    }
                })
                .doOnError(error -> log.error("Failed to run reconciliation alert check", error))
                .subscribe();
    }

    /**
     * Weekly Full Reconciliation Summary - Every Monday at 7:00 AM CAT.
     *
     * <p>Generates a summary report of all reconciliation activity for the
     * previous week. Useful for weekly finance team reviews.</p>
     */
    @Scheduled(cron = "0 0 5 * * MON", zone = "UTC") // 7:00 AM CAT on Monday = 5:00 AM UTC
    public void runWeeklyReconciliationSummary() {
        if (!schedulerEnabled) {
            log.debug("Scheduled reconciliation is disabled");
            return;
        }

        log.info("Generating weekly reconciliation summary");

        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(6);

        // Generate summary for each reconciliation type
        for (ReconciliationType type : ReconciliationType.values()) {
            reconciliationService.generateReport(type, startDate, endDate)
                    .doOnSuccess(report -> {
                        log.info("Weekly {} Reconciliation Summary ({} to {}):",
                                type, startDate, endDate);
                        log.info("  - Total Runs: {}", report.runCount());
                        log.info("  - Total Items: {}", report.itemCount());
                        log.info("  - Matched: {} ({:.1f}%)",
                                report.matchedCount(),
                                report.itemCount() > 0 ?
                                        (report.matchedCount() * 100.0 / report.itemCount()) : 0);
                        log.info("  - Unresolved: {}", report.unresolvedItems().size());
                        log.info("  - Total Variance: K{}", report.variance());
                    })
                    .doOnError(error -> log.error("Failed to generate {} weekly summary", type, error))
                    .subscribe();
        }
    }
}
