package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.AlertPriority;
import com.pml.booking.domain.enums.JournalEntryType;
import com.pml.booking.domain.enums.NotificationType;
import com.pml.booking.domain.enums.ReconciliationItemStatus;
import com.pml.booking.domain.enums.ReconciliationStatus;
import com.pml.booking.domain.enums.ReconciliationType;
import com.pml.booking.domain.model.JournalLine;
import com.pml.booking.domain.model.PaymentIntent;
import com.pml.booking.domain.model.ReconciliationItem;
import com.pml.booking.domain.model.ReconciliationRun;
import com.pml.booking.exception.ReconciliationDiscrepancyException;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.EventEscrowAccount.EscrowStatus;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.PaymentIntentRepository;
import com.pml.booking.repository.ReconciliationRunRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.EscrowTransactionService;
import com.pml.booking.service.JournalService;
import com.pml.booking.service.NotificationService;
import com.pml.booking.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reconciliation Service Implementation
 *
 * <p>Manages automated reconciliation processes to ensure financial data
 * integrity across multiple systems. Supports gateway, bank, and escrow
 * reconciliation types.</p>
 *
 * <h2>Reconciliation Types</h2>
 * <ul>
 *   <li><b>GATEWAY</b>: Compare gateway settlements with internal records</li>
 *   <li><b>BANK</b>: Compare bank statements with ledger</li>
 *   <li><b>ESCROW</b>: Verify escrow balances match transactions</li>
 * </ul>
 *
 * <h2>Item Matching Logic</h2>
 * <ul>
 *   <li>Match by transaction/reference ID</li>
 *   <li>Verify amounts match within tolerance</li>
 *   <li>Flag discrepancies for manual review</li>
 * </ul>
 *
 * @see ReconciliationService
 * @see ReconciliationRun
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final ReconciliationRunRepository reconciliationRepository;
    private final EventEscrowAccountRepository escrowAccountRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final EscrowTransactionService escrowTransactionService;
    private final JournalService journalService;
    private final AccountingService accountingService;
    private final NotificationService notificationService;
    private final ReactiveMongoTemplate mongoTemplate;

    // Tolerance for amount matching (handles rounding differences)
    private static final BigDecimal AMOUNT_TOLERANCE = new BigDecimal("0.01");

    // ========================================================================
    // GATEWAY RECONCILIATION
    // ========================================================================

    @Override
    @Transactional
    public Mono<ReconciliationRun> startGatewayReconciliation(
            LocalDate reconciliationDate,
            Map<String, BigDecimal> gatewayData,
            String runBy
    ) {
        log.info("Starting gateway reconciliation for date: {}", reconciliationDate);

        return createReconciliationRun(ReconciliationType.GATEWAY, reconciliationDate, runBy)
                .flatMap(run -> {
                    // TODO: Load internal payment records for the date
                    // For now, create items from gateway data
                    List<ReconciliationItem> items = new ArrayList<>();
                    BigDecimal expectedTotal = BigDecimal.ZERO;

                    for (Map.Entry<String, BigDecimal> entry : gatewayData.entrySet()) {
                        // Create item for each gateway transaction (external, awaiting internal match)
                        ReconciliationItem item = ReconciliationItem.unmatchedExternal(
                                entry.getKey(),
                                entry.getValue()
                        );
                        items.add(item);
                        expectedTotal = expectedTotal.add(entry.getValue());
                    }

                    run.setItems(items);
                    run.setExpectedTotal(expectedTotal);

                    // Process matching
                    return processMatching(run)
                            .flatMap(processedRun -> reconciliationRepository.save(processedRun))
                            .doOnSuccess(saved -> log.info(
                                    "Gateway reconciliation started: {} items, {} matched",
                                    saved.getItems().size(), saved.getMatchedCount()));
                });
    }

    @Override
    @Transactional
    public Mono<ReconciliationRun> startGatewayReconciliationFromFile(
            LocalDate reconciliationDate,
            String settlementFileContent,
            String fileFormat,
            String runBy
    ) {
        log.info("Starting gateway reconciliation from file for date: {}", reconciliationDate);

        // Parse file based on format
        Map<String, BigDecimal> gatewayData = parseSettlementFile(settlementFileContent, fileFormat);

        return startGatewayReconciliation(reconciliationDate, gatewayData, runBy);
    }

    /**
     * Parses settlement file content based on format.
     *
     * <h2>Supported Formats</h2>
     * <ul>
     *   <li><b>CSV</b>: Header row + transaction_id,amount,status columns</li>
     *   <li><b>JSON</b>: Array of objects with transactionId, amount, status fields</li>
     * </ul>
     *
     * <h2>CSV Format Example</h2>
     * <pre>
     * transaction_id,amount,status
     * TXN-001,500.00,SUCCEEDED
     * TXN-002,250.00,SUCCEEDED
     * </pre>
     *
     * <h2>JSON Format Example</h2>
     * <pre>
     * {
     *   "settlementDate": "2024-03-15",
     *   "transactions": [
     *     {"transactionId": "TXN-001", "amount": 500.00, "status": "SUCCEEDED"},
     *     {"transactionId": "TXN-002", "amount": 250.00, "status": "SUCCEEDED"}
     *   ]
     * }
     * </pre>
     *
     * @param content File content as string
     * @param format  File format: "csv" or "json"
     * @return Map of transaction ID to amount
     */
    private Map<String, BigDecimal> parseSettlementFile(String content, String format) {
        Map<String, BigDecimal> data = new HashMap<>();

        if ("csv".equalsIgnoreCase(format)) {
            parseCsvSettlement(content, data);
        } else if ("json".equalsIgnoreCase(format)) {
            parseJsonSettlement(content, data);
        } else {
            log.warn("Unsupported settlement file format: {}. Supported formats: csv, json", format);
        }

        log.info("Parsed {} transactions from {} format settlement file", data.size(), format);
        return data;
    }

    /**
     * Parses CSV format settlement file.
     *
     * <p>Expected format: transaction_id,amount[,status] with header row.</p>
     */
    private void parseCsvSettlement(String content, Map<String, BigDecimal> data) {
        String[] lines = content.split("\n");

        // Find column indices from header
        int txIdCol = 0;
        int amountCol = 1;
        int statusCol = -1;

        if (lines.length > 0) {
            String[] headers = lines[0].toLowerCase().split(",");
            for (int i = 0; i < headers.length; i++) {
                String header = headers[i].trim();
                if (header.contains("transaction") && header.contains("id") || header.equals("transactionid")) {
                    txIdCol = i;
                } else if (header.contains("amount") || header.equals("value")) {
                    amountCol = i;
                } else if (header.contains("status")) {
                    statusCol = i;
                }
            }
        }

        for (int i = 1; i < lines.length; i++) { // Skip header
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split(",");
            if (parts.length >= 2) {
                try {
                    String txId = parts[txIdCol].trim().replace("\"", "");
                    String amountStr = parts[amountCol].trim().replace("\"", "");
                    BigDecimal amount = new BigDecimal(amountStr);

                    // Only include SUCCEEDED transactions if status column exists
                    if (statusCol >= 0 && parts.length > statusCol) {
                        String status = parts[statusCol].trim().toUpperCase().replace("\"", "");
                        if (!"SUCCEEDED".equals(status) && !"SUCCESS".equals(status) && !"COMPLETED".equals(status)) {
                            continue;
                        }
                    }

                    data.put(txId, amount);
                } catch (NumberFormatException e) {
                    log.warn("Skipping CSV line {} - invalid amount format: {}", i + 1, line);
                }
            }
        }
    }

    /**
     * Parses JSON format settlement file.
     *
     * <p>Supports two formats:</p>
     * <ul>
     *   <li>Object with "transactions" array: {"transactions": [...]}</li>
     *   <li>Direct array of transactions: [...]</li>
     * </ul>
     */
    private void parseJsonSettlement(String content, Map<String, BigDecimal> data) {
        try {
            content = content.trim();

            // Check if it's an array or object
            if (content.startsWith("[")) {
                // Direct array format
                parseJsonArray(content, data);
            } else if (content.startsWith("{")) {
                // Object format - look for transactions array
                int txArrayStart = content.indexOf("\"transactions\"");
                if (txArrayStart > 0) {
                    int arrayStart = content.indexOf("[", txArrayStart);
                    int arrayEnd = findMatchingBracket(content, arrayStart, '[', ']');
                    if (arrayStart > 0 && arrayEnd > arrayStart) {
                        String arrayContent = content.substring(arrayStart, arrayEnd + 1);
                        parseJsonArray(arrayContent, data);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse JSON settlement file", e);
        }
    }

    /**
     * Parses a JSON array of transaction objects.
     */
    private void parseJsonArray(String arrayContent, Map<String, BigDecimal> data) {
        // Simple JSON array parsing without external dependencies
        // Looks for objects with transactionId/transaction_id and amount fields
        int pos = 0;
        while (pos < arrayContent.length()) {
            int objStart = arrayContent.indexOf("{", pos);
            if (objStart < 0) break;

            int objEnd = findMatchingBracket(arrayContent, objStart, '{', '}');
            if (objEnd < 0) break;

            String obj = arrayContent.substring(objStart, objEnd + 1);

            // Extract transaction ID
            String txId = extractJsonStringField(obj, "transactionId");
            if (txId == null) txId = extractJsonStringField(obj, "transaction_id");
            if (txId == null) txId = extractJsonStringField(obj, "id");

            // Extract amount
            BigDecimal amount = extractJsonNumberField(obj, "amount");
            if (amount == null) amount = extractJsonNumberField(obj, "value");

            // Extract status (optional)
            String status = extractJsonStringField(obj, "status");

            // Add to map if valid and succeeded
            if (txId != null && amount != null) {
                if (status == null || "SUCCEEDED".equalsIgnoreCase(status) ||
                        "SUCCESS".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status)) {
                    data.put(txId, amount);
                }
            }

            pos = objEnd + 1;
        }
    }

    private int findMatchingBracket(String s, int start, char open, char close) {
        if (start < 0 || start >= s.length() || s.charAt(start) != open) return -1;

        int depth = 1;
        boolean inString = false;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private String extractJsonStringField(String json, String field) {
        String pattern1 = "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern1);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        return null;
    }

    private BigDecimal extractJsonNumberField(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*([\\d.]+)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return new BigDecimal(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    // ========================================================================
    // BANK RECONCILIATION
    // ========================================================================

    @Override
    @Transactional
    public Mono<ReconciliationRun> startBankReconciliation(
            LocalDate reconciliationDate,
            List<BankStatementEntry> bankData,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            String runBy
    ) {
        log.info("Starting bank reconciliation for date: {}", reconciliationDate);

        return createReconciliationRun(ReconciliationType.BANK, reconciliationDate, runBy)
                .flatMap(run -> {
                    List<ReconciliationItem> items = new ArrayList<>();
                    BigDecimal calculatedBalance = openingBalance;

                    for (BankStatementEntry entry : bankData) {
                        BigDecimal netAmount = entry.credit().subtract(entry.debit());
                        calculatedBalance = calculatedBalance.add(netAmount);

                        ReconciliationItem item = ReconciliationItem.unmatchedExternal(
                                entry.reference(),
                                netAmount.abs()
                        );
                        items.add(item);
                    }

                    run.setItems(items);
                    run.setExpectedTotal(closingBalance);
                    run.setActualTotal(calculatedBalance);
                    run.setVariance(closingBalance.subtract(calculatedBalance));

                    return processMatching(run)
                            .flatMap(processedRun -> reconciliationRepository.save(processedRun))
                            .doOnSuccess(saved -> log.info(
                                    "Bank reconciliation started: variance {}",
                                    saved.getVariance()));
                });
    }

    // ========================================================================
    // ESCROW RECONCILIATION
    // ========================================================================

    @Override
    @Transactional
    public Mono<ReconciliationRun> startEscrowReconciliation(
            LocalDate reconciliationDate,
            String runBy
    ) {
        log.info("Starting escrow reconciliation for date: {}", reconciliationDate);

        return createReconciliationRun(ReconciliationType.ESCROW, reconciliationDate, runBy)
                .flatMap(run -> escrowAccountRepository.findAll()
                        .flatMap(escrow -> reconcileEscrowAccount(escrow.getId(), runBy)
                                .map(result -> {
                                    if (result.isBalanced()) {
                                        return ReconciliationItem.matched(
                                                escrow.getId(),
                                                escrow.getId(),
                                                result.recordedBalance()
                                        );
                                    } else {
                                        return ReconciliationItem.amountMismatch(
                                                escrow.getId(),
                                                escrow.getId(),
                                                result.recordedBalance(),
                                                result.calculatedBalance()
                                        );
                                    }
                                }))
                        .collectList()
                        .flatMap(items -> {
                            run.setItems(items);

                            BigDecimal totalVariance = items.stream()
                                    .map(item -> item.getExternalAmount().subtract(item.getInternalAmount()).abs())
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                            run.setVariance(totalVariance);

                            long matchedCount = items.stream()
                                    .filter(i -> i.getStatus() == ReconciliationItemStatus.MATCHED)
                                    .count();
                            run.setMatchedCount((int) matchedCount);
                            run.setUnmatchedCount(items.size() - (int) matchedCount);

                            if (run.getUnmatchedCount() > 0) {
                                run.requiresReview();
                            } else {
                                run.complete("All escrow accounts balanced");
                            }

                            return reconciliationRepository.save(run);
                        }));
    }

    @Override
    public Mono<EscrowReconciliationResult> reconcileEscrowAccount(
            String escrowAccountId,
            String runBy
    ) {
        return escrowAccountRepository.findById(escrowAccountId)
                .flatMap(escrow -> escrowTransactionService.calculateBalance(escrowAccountId)
                        .map(calculatedBalance -> {
                            BigDecimal recordedBalance = escrow.getCurrentBalance();
                            BigDecimal variance = recordedBalance.subtract(calculatedBalance);
                            boolean isBalanced = variance.abs().compareTo(AMOUNT_TOLERANCE) <= 0;

                            List<String> discrepancies = new ArrayList<>();
                            if (!isBalanced) {
                                discrepancies.add(String.format(
                                        "Balance mismatch: recorded=%s, calculated=%s, variance=%s",
                                        recordedBalance, calculatedBalance, variance
                                ));
                            }

                            return new EscrowReconciliationResult(
                                    escrowAccountId,
                                    recordedBalance,
                                    calculatedBalance,
                                    variance,
                                    isBalanced,
                                    discrepancies
                            );
                        }))
                .defaultIfEmpty(new EscrowReconciliationResult(
                        escrowAccountId,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        true,
                        List.of()
                ));
    }

    // ========================================================================
    // ESCROW-JOURNAL CROSS-VERIFICATION
    // ========================================================================

    @Override
    @Transactional
    public Mono<ReconciliationRun> startEscrowJournalReconciliation(
            LocalDate reconciliationDate,
            String runBy
    ) {
        // Delegate to overloaded method with includeClosed=false (default behavior)
        return startEscrowJournalReconciliation(reconciliationDate, runBy, false);
    }

    @Override
    @Transactional
    public Mono<ReconciliationRun> startEscrowJournalReconciliation(
            LocalDate reconciliationDate,
            String runBy,
            boolean includeClosed
    ) {
        log.info("Starting escrow-journal cross-verification for date: {} (includeClosed={})",
                reconciliationDate, includeClosed);

        return createReconciliationRun(ReconciliationType.ESCROW_JOURNAL, reconciliationDate, runBy)
                .flatMap(run -> verifyAllEscrowJournalConsistency(runBy, includeClosed)
                        .map(result -> {
                            if (result.isConsistent()) {
                                return ReconciliationItem.matched(
                                        result.escrowAccountId() != null ? result.escrowAccountId() : result.eventId(),
                                        result.journalAccountCode(),
                                        result.escrowBalance()
                                );
                            } else {
                                ReconciliationItem item = ReconciliationItem.amountMismatch(
                                        result.escrowAccountId() != null ? result.escrowAccountId() : result.eventId(),
                                        result.journalAccountCode(),
                                        result.escrowBalance(),
                                        result.journalBalance()
                                );
                                item.setNotes(String.format("Status: %s. %s",
                                        result.status(),
                                        String.join("; ", result.details())));
                                return item;
                            }
                        })
                        .collectList()
                        .flatMap(items -> {
                            run.setItems(items);

                            BigDecimal totalVariance = items.stream()
                                    .map(item -> {
                                        BigDecimal external = item.getExternalAmount() != null ? item.getExternalAmount() : BigDecimal.ZERO;
                                        BigDecimal internal = item.getInternalAmount() != null ? item.getInternalAmount() : BigDecimal.ZERO;
                                        return external.subtract(internal).abs();
                                    })
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                            run.setVariance(totalVariance);

                            long matchedCount = items.stream()
                                    .filter(i -> i.getStatus() == ReconciliationItemStatus.MATCHED)
                                    .count();
                            run.setMatchedCount((int) matchedCount);
                            run.setUnmatchedCount(items.size() - (int) matchedCount);

                            if (run.getUnmatchedCount() > 0) {
                                run.requiresReview();
                                log.warn("Escrow-Journal cross-verification found {} inconsistencies with total variance: K{}",
                                        run.getUnmatchedCount(), totalVariance);
                            } else {
                                run.complete("All escrow accounts consistent with journal entries");
                                log.info("Escrow-Journal cross-verification passed for all {} accounts", items.size());
                            }

                            return reconciliationRepository.save(run);
                        }));
    }

    @Override
    public Mono<EscrowJournalVerificationResult> verifyEscrowJournalConsistency(
            String eventId,
            String runBy
    ) {
        log.debug("Verifying escrow-journal consistency for event: {}", eventId);

        String journalAccountCode = getEscrowAccountCode(eventId);

        // Get both balances in parallel
        Mono<BigDecimal> journalBalanceMono = accountingService.getAccountBalance(journalAccountCode)
                .defaultIfEmpty(BigDecimal.ZERO);

        Mono<com.pml.booking.domain.model.EventEscrowAccount> escrowAccountMono =
                escrowAccountRepository.findByEventId(eventId);

        return Mono.zip(journalBalanceMono, escrowAccountMono.map(java.util.Optional::of).defaultIfEmpty(java.util.Optional.empty()))
                .map(tuple -> {
                    BigDecimal journalBalance = tuple.getT1();
                    java.util.Optional<com.pml.booking.domain.model.EventEscrowAccount> escrowOpt = tuple.getT2();

                    List<String> details = new ArrayList<>();

                    // Case 1: No escrow account exists
                    if (escrowOpt.isEmpty()) {
                        if (journalBalance.compareTo(BigDecimal.ZERO) != 0) {
                            details.add(String.format("Journal account %s has balance K%s but no EventEscrowAccount exists",
                                    journalAccountCode, journalBalance));
                            return new EscrowJournalVerificationResult(
                                    eventId,
                                    null,
                                    journalAccountCode,
                                    BigDecimal.ZERO,
                                    journalBalance,
                                    journalBalance.negate(),
                                    false,
                                    VerificationStatus.ORPHANED_JOURNAL_ACCOUNT,
                                    details
                            );
                        } else {
                            details.add("Neither escrow account nor journal entries exist for this event");
                            return new EscrowJournalVerificationResult(
                                    eventId,
                                    null,
                                    journalAccountCode,
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO,
                                    BigDecimal.ZERO,
                                    true,
                                    VerificationStatus.NOT_FOUND,
                                    details
                            );
                        }
                    }

                    // Case 2: Escrow account exists
                    com.pml.booking.domain.model.EventEscrowAccount escrow = escrowOpt.get();
                    BigDecimal escrowBalance = escrow.getCurrentBalance();
                    BigDecimal variance = escrowBalance.subtract(journalBalance);
                    boolean isConsistent = variance.abs().compareTo(AMOUNT_TOLERANCE) <= 0;

                    // Case 2a: Journal balance is zero but escrow has balance
                    if (journalBalance.compareTo(BigDecimal.ZERO) == 0 && escrowBalance.compareTo(BigDecimal.ZERO) != 0) {
                        details.add(String.format("Escrow account %s has balance K%s but no journal entries found for %s",
                                escrow.getId(), escrowBalance, journalAccountCode));
                        return new EscrowJournalVerificationResult(
                                eventId,
                                escrow.getId(),
                                journalAccountCode,
                                escrowBalance,
                                journalBalance,
                                variance,
                                false,
                                VerificationStatus.MISSING_JOURNAL_ACCOUNT,
                                details
                        );
                    }

                    // Case 2b: Both have balances - check if they match
                    if (!isConsistent) {
                        details.add(String.format("Balance mismatch: Escrow=%s, Journal=%s, Variance=%s",
                                escrowBalance, journalBalance, variance));
                        details.add("Possible causes: Transaction recorded in one system but not the other");
                    } else {
                        details.add(String.format("Balances match: K%s (variance within tolerance)", escrowBalance));
                    }

                    return new EscrowJournalVerificationResult(
                            eventId,
                            escrow.getId(),
                            journalAccountCode,
                            escrowBalance,
                            journalBalance,
                            variance,
                            isConsistent,
                            isConsistent ? VerificationStatus.CONSISTENT : VerificationStatus.BALANCE_MISMATCH,
                            details
                    );
                })
                .doOnSuccess(result -> {
                    if (!result.isConsistent()) {
                        log.warn("Escrow-Journal inconsistency detected for event {}: {} - variance K{}",
                                eventId, result.status(), result.variance());
                    }
                });
    }

    @Override
    public Flux<EscrowJournalVerificationResult> verifyAllEscrowJournalConsistency(String runBy) {
        log.info("Starting escrow-journal consistency verification (excluding CLOSED/CANCELLED accounts)");

        // By default, exclude CLOSED and CANCELLED accounts for performance
        // These accounts have zero balance and are immutable
        List<EscrowStatus> excludedStatuses = List.of(EscrowStatus.CLOSED, EscrowStatus.CANCELLED);

        return escrowAccountRepository.findByStatusNotIn(excludedStatuses)
                .flatMap(escrow -> verifyEscrowJournalConsistency(escrow.getEventId(), runBy))
                .doOnComplete(() -> log.info("Completed escrow-journal consistency verification"));
    }

    /**
     * Verifies all escrow accounts against their journal entries with option to include closed.
     *
     * @param runBy User/system running the check
     * @param includeClosed If true, includes CLOSED and CANCELLED accounts
     * @return Stream of verification results
     */
    private Flux<EscrowJournalVerificationResult> verifyAllEscrowJournalConsistency(String runBy, boolean includeClosed) {
        if (includeClosed) {
            log.info("Starting FULL escrow-journal consistency verification (including CLOSED/CANCELLED accounts)");
            return escrowAccountRepository.findAll()
                    .flatMap(escrow -> verifyEscrowJournalConsistency(escrow.getEventId(), runBy))
                    .doOnComplete(() -> log.info("Completed full escrow-journal consistency verification"));
        } else {
            return verifyAllEscrowJournalConsistency(runBy);
        }
    }

    /**
     * Gets the escrow account code for an event.
     * Format: 2010-{eventId first 8 chars}
     */
    private String getEscrowAccountCode(String eventId) {
        String shortEventId = eventId.length() > 8 ? eventId.substring(0, 8) : eventId;
        return "2010-" + shortEventId;
    }

    // ========================================================================
    // ITEM RESOLUTION
    // ========================================================================

    @Override
    @Transactional
    public Mono<ReconciliationRun> resolveItem(
            String runId,
            String externalId,
            String resolution,
            String resolvedBy
    ) {
        log.info("Resolving reconciliation item: run={}, item={}", runId, externalId);

        return reconciliationRepository.findById(runId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Run not found: " + runId)))
                .flatMap(run -> {
                    run.resolveItem(externalId, resolution, resolvedBy);
                    return reconciliationRepository.save(run);
                })
                .doOnSuccess(updated -> log.info("Item resolved: {}", externalId));
    }

    @Override
    @Transactional
    public Mono<ReconciliationRun> resolveItems(
            String runId,
            Map<String, String> resolutions,
            String resolvedBy
    ) {
        return reconciliationRepository.findById(runId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Run not found: " + runId)))
                .flatMap(run -> {
                    for (Map.Entry<String, String> entry : resolutions.entrySet()) {
                        run.resolveItem(entry.getKey(), entry.getValue(), resolvedBy);
                    }
                    return reconciliationRepository.save(run);
                });
    }

    /**
     * Creates an adjustment journal entry to correct a reconciliation discrepancy.
     *
     * <h2>Purpose</h2>
     * <p>When reconciliation finds a variance that cannot be explained by timing
     * or other expected differences, an adjustment entry may be needed to
     * bring the books into alignment with reality.</p>
     *
     * <h2>Typical Adjustment Scenarios</h2>
     * <ul>
     *   <li><b>Gateway Fee Variance</b>: Gateway charged a fee we didn't record</li>
     *   <li><b>Missing Internal Record</b>: Webhook failed, need to record payment</li>
     *   <li><b>Bank Fee</b>: Bank charged fee not in our records</li>
     *   <li><b>Rounding Difference</b>: Small currency conversion rounding</li>
     * </ul>
     *
     * <h2>Journal Entry Structure</h2>
     * <pre>
     * For positive variance (we have less than gateway):
     *   DR 5030 Bank Fees / Reconciliation Variance   K50.00
     *   CR 1021 Gateway Settlement Receivable         K50.00
     *
     * For negative variance (we have more than gateway):
     *   DR 1021 Gateway Settlement Receivable         K50.00
     *   CR 4090 Other Income / Reconciliation Gain    K50.00
     * </pre>
     *
     * @param runId The reconciliation run ID
     * @param externalId The external transaction ID being adjusted
     * @param adjustmentAmount The adjustment amount (positive = expense, negative = income)
     * @param description Human-readable description of the adjustment
     * @param approvedBy User who approved the adjustment
     * @return The created journal entry ID
     */
    @Override
    @Transactional
    public Mono<String> createAdjustmentEntry(
            String runId,
            String externalId,
            BigDecimal adjustmentAmount,
            String description,
            String approvedBy
    ) {
        log.info("Creating adjustment entry for reconciliation: run={}, item={}, amount={}",
                runId, externalId, adjustmentAmount);

        return reconciliationRepository.findById(runId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Run not found: " + runId)))
                .flatMap(run -> {
                    // Build journal entry lines based on adjustment direction
                    List<JournalLine> lines = new ArrayList<>();

                    // Account codes (from Chart of Accounts)
                    String gatewayReceivableAccount = "1021"; // Gateway Settlement Receivable
                    String reconciliationExpenseAccount = "5099"; // Reconciliation Variance Expense
                    String reconciliationIncomeAccount = "4099"; // Reconciliation Variance Income

                    String fullDescription = String.format(
                            "Reconciliation Adjustment - %s %s: %s (Run: %s, External ID: %s)",
                            run.getType(),
                            run.getReconciliationDate(),
                            description,
                            run.getRunNumber(),
                            externalId
                    );

                    if (adjustmentAmount.compareTo(BigDecimal.ZERO) > 0) {
                        // Positive adjustment: We need to record an expense/loss
                        // Gateway has more than we recorded, so we owe the difference
                        lines.add(JournalLine.debitWithReference(
                                reconciliationExpenseAccount,
                                adjustmentAmount,
                                "Reconciliation variance - " + description,
                                "RECONCILIATION",
                                runId
                        ));
                        lines.add(JournalLine.creditWithReference(
                                gatewayReceivableAccount,
                                adjustmentAmount,
                                "Adjust gateway receivable for variance",
                                "RECONCILIATION",
                                runId
                        ));
                    } else {
                        // Negative adjustment: We record income/gain
                        // We have more than gateway shows, adjust to match
                        BigDecimal absAmount = adjustmentAmount.abs();
                        lines.add(JournalLine.debitWithReference(
                                gatewayReceivableAccount,
                                absAmount,
                                "Adjust gateway receivable for variance",
                                "RECONCILIATION",
                                runId
                        ));
                        lines.add(JournalLine.creditWithReference(
                                reconciliationIncomeAccount,
                                absAmount,
                                "Reconciliation variance gain - " + description,
                                "RECONCILIATION",
                                runId
                        ));
                    }

                    // Create the journal entry
                    return journalService.createAndPostEntry(
                            "RECON-" + runId + "-" + externalId,
                            LocalDateTime.now(),
                            fullDescription,
                            JournalEntryType.ADJUSTMENT,
                            lines,
                            approvedBy,
                            Map.of(
                                    "reconciliationRunId", runId,
                                    "reconciliationRunNumber", run.getRunNumber(),
                                    "reconciliationType", run.getType().name(),
                                    "externalId", externalId,
                                    "adjustmentAmount", adjustmentAmount.toString()
                            )
                    ).flatMap(entry -> {
                        // Update the reconciliation item with the journal entry reference
                        run.getItems().stream()
                                .filter(item -> externalId.equals(item.getExternalId()) ||
                                        externalId.equals(item.getInternalId()))
                                .findFirst()
                                .ifPresent(item -> {
                                    item.resolveWithAdjustment(
                                            "Adjustment entry created: " + description,
                                            approvedBy,
                                            entry.getId()
                                    );
                                });

                        return reconciliationRepository.save(run)
                                .thenReturn(entry.getId());
                    });
                })
                .doOnSuccess(entryId -> log.info("Created adjustment entry {} for reconciliation {}",
                        entryId, runId))
                .doOnError(error -> log.error("Failed to create adjustment entry for reconciliation {}",
                        runId, error));
    }

    // ========================================================================
    // RUN MANAGEMENT
    // ========================================================================

    @Override
    @Transactional
    public Mono<ReconciliationRun> completeRun(String runId, String notes) {
        log.info("Completing reconciliation run: {}", runId);

        return reconciliationRepository.findById(runId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Run not found: " + runId)))
                .flatMap(run -> {
                    // Check all items are resolved
                    long unresolvedCount = run.getItems().stream()
                            .filter(item -> item.getStatus() != ReconciliationItemStatus.MATCHED &&
                                    item.getResolvedAt() == null)
                            .count();

                    if (unresolvedCount > 0) {
                        return Mono.error(new ReconciliationDiscrepancyException(
                                String.format("Cannot complete: %d unresolved items remain", unresolvedCount)
                        ));
                    }

                    run.complete(notes);
                    return reconciliationRepository.save(run);
                })
                .doOnSuccess(completed -> log.info("Reconciliation run completed: {}", runId));
    }

    @Override
    @Transactional
    public Mono<ReconciliationRun> failRun(String runId, String reason) {
        log.info("Failing reconciliation run: {}", runId);

        return reconciliationRepository.findById(runId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Run not found: " + runId)))
                .flatMap(run -> {
                    run.fail(reason);
                    return reconciliationRepository.save(run);
                });
    }

    @Override
    @Transactional
    public Mono<ReconciliationRun> cancelRun(String runId, String reason) {
        log.info("Cancelling reconciliation run: {}", runId);

        return reconciliationRepository.findById(runId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Run not found: " + runId)))
                .flatMap(run -> {
                    run.fail("Cancelled: " + reason);
                    return reconciliationRepository.save(run);
                });
    }

    // ========================================================================
    // QUERIES
    // ========================================================================

    @Override
    public Flux<ReconciliationRun> findAll() {
        return reconciliationRepository.findAll();
    }

    @Override
    public Mono<ReconciliationRun> findById(String id) {
        return reconciliationRepository.findById(id);
    }

    @Override
    public Flux<ReconciliationRun> findByType(ReconciliationType type) {
        return reconciliationRepository.findByType(type);
    }

    @Override
    public Flux<ReconciliationRun> findByStatus(ReconciliationStatus status) {
        return reconciliationRepository.findByStatus(status);
    }

    @Override
    public Flux<ReconciliationRun> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return reconciliationRepository.findByReconciliationDateBetween(startDate, endDate);
    }

    @Override
    public Flux<ReconciliationRun> findRequiringReview() {
        return reconciliationRepository.findByStatus(ReconciliationStatus.REQUIRES_REVIEW);
    }

    @Override
    public Flux<ReconciliationItemWithRun> findItemsByStatus(ReconciliationItemStatus status) {
        return reconciliationRepository.findAll()
                .flatMapIterable(run -> run.getItems().stream()
                        .filter(item -> item.getStatus() == status)
                        .map(item -> new ReconciliationItemWithRun(
                                run.getId(),
                                run.getType(),
                                run.getReconciliationDate(),
                                item
                        ))
                        .toList());
    }

    // ========================================================================
    // STATISTICS & REPORTING
    // ========================================================================

    @Override
    public Mono<ReconciliationSummary> getSummary(ReconciliationType type) {
        Flux<ReconciliationRun> runs = type != null
                ? reconciliationRepository.findByType(type)
                : reconciliationRepository.findAll();

        return runs.collectList()
                .map(runList -> {
                    long totalRuns = runList.size();
                    long completedRuns = runList.stream()
                            .filter(r -> r.getStatus() == ReconciliationStatus.COMPLETED)
                            .count();
                    long pendingReviewRuns = runList.stream()
                            .filter(r -> r.getStatus() == ReconciliationStatus.REQUIRES_REVIEW)
                            .count();
                    long failedRuns = runList.stream()
                            .filter(r -> r.getStatus() == ReconciliationStatus.FAILED)
                            .count();

                    BigDecimal totalVariance = runList.stream()
                            .map(r -> r.getVariance() != null ? r.getVariance().abs() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal resolvedVariance = runList.stream()
                            .filter(r -> r.getStatus() == ReconciliationStatus.COMPLETED)
                            .map(r -> r.getVariance() != null ? r.getVariance().abs() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    LocalDate lastCompleted = runList.stream()
                            .filter(r -> r.getStatus() == ReconciliationStatus.COMPLETED)
                            .map(ReconciliationRun::getReconciliationDate)
                            .max(LocalDate::compareTo)
                            .orElse(null);

                    LocalDate oldestPending = runList.stream()
                            .filter(r -> r.getStatus() == ReconciliationStatus.REQUIRES_REVIEW)
                            .map(ReconciliationRun::getReconciliationDate)
                            .min(LocalDate::compareTo)
                            .orElse(null);

                    return new ReconciliationSummary(
                            totalRuns,
                            completedRuns,
                            pendingReviewRuns,
                            failedRuns,
                            totalVariance,
                            resolvedVariance,
                            totalVariance.subtract(resolvedVariance),
                            lastCompleted,
                            oldestPending
                    );
                });
    }

    @Override
    public Mono<ReconciliationReport> generateReport(
            ReconciliationType type,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return reconciliationRepository.findByTypeAndReconciliationDateBetween(type, startDate, endDate)
                .collectList()
                .map(runs -> {
                    int runCount = runs.size();
                    int itemCount = runs.stream()
                            .mapToInt(r -> r.getItems().size())
                            .sum();
                    int matchedCount = runs.stream()
                            .mapToInt(ReconciliationRun::getMatchedCount)
                            .sum();
                    int unmatchedCount = runs.stream()
                            .mapToInt(ReconciliationRun::getUnmatchedCount)
                            .sum();

                    // Count resolved items
                    int resolvedCount = (int) runs.stream()
                            .flatMap(r -> r.getItems().stream())
                            .filter(item -> item.getResolvedAt() != null)
                            .count();

                    BigDecimal expectedTotal = runs.stream()
                            .map(r -> r.getExpectedTotal() != null ? r.getExpectedTotal() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal actualTotal = runs.stream()
                            .map(r -> r.getActualTotal() != null ? r.getActualTotal() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal variance = expectedTotal.subtract(actualTotal);

                    // Build unresolved items list
                    List<UnresolvedItem> unresolvedItems = runs.stream()
                            .flatMap(run -> run.getItems().stream()
                                    .filter(item -> item.getStatus() != ReconciliationItemStatus.MATCHED &&
                                            item.getResolvedAt() == null)
                                    .map(item -> new UnresolvedItem(
                                            run.getId(),
                                            run.getReconciliationDate(),
                                            item.getExternalId(),
                                            item.getInternalId(),
                                            item.getExternalAmount(),
                                            item.getInternalAmount(),
                                            item.getStatus(),
                                            (int) ChronoUnit.DAYS.between(run.getReconciliationDate(), LocalDate.now())
                                    )))
                            .toList();

                    return new ReconciliationReport(
                            type,
                            startDate,
                            endDate,
                            runCount,
                            itemCount,
                            matchedCount,
                            unmatchedCount,
                            resolvedCount,
                            expectedTotal,
                            actualTotal,
                            variance,
                            unresolvedItems
                    );
                });
    }

    // ========================================================================
    // AUTOMATED SCHEDULING
    // ========================================================================

    @Override
    public Mono<Integer> processScheduledReconciliations() {
        log.info("Processing scheduled reconciliations");
        // TODO: Implement scheduled reconciliation logic
        // - Check for pending gateway settlements
        // - Run escrow reconciliation
        return Mono.just(0);
    }

    /**
     * Sends reconciliation alerts for items requiring attention.
     *
     * <h2>Alert Triggers</h2>
     * <table border="1">
     *   <tr><th>Condition</th><th>Priority</th><th>Action</th></tr>
     *   <tr><td>Pending > 3 days</td><td>HIGH</td><td>Email + Slack</td></tr>
     *   <tr><td>Variance > K1,000</td><td>CRITICAL</td><td>Email + Slack + SMS</td></tr>
     *   <tr><td>Unmatched > 10 items</td><td>HIGH</td><td>Email + Slack</td></tr>
     * </table>
     *
     * @return Count of alerts sent
     */
    @Override
    public Mono<Integer> sendReconciliationAlerts() {
        log.info("Checking for reconciliation alerts");

        LocalDate threeDaysAgo = LocalDate.now().minusDays(3);
        BigDecimal highVarianceThreshold = new BigDecimal("1000.00");
        int highItemCountThreshold = 10;

        return reconciliationRepository.findByStatus(ReconciliationStatus.REQUIRES_REVIEW)
                .collectList()
                .flatMap(pendingRuns -> {
                    List<Mono<Void>> notificationMonos = new ArrayList<>();
                    List<ReconciliationRun> runsRequiringAlert = new ArrayList<>();

                    for (ReconciliationRun run : pendingRuns) {
                        List<String> alerts = new ArrayList<>();
                        AlertPriority highestPriority = AlertPriority.LOW;

                        // Check 1: Pending for more than 3 days
                        if (run.getReconciliationDate().isBefore(threeDaysAgo)) {
                            long daysPending = ChronoUnit.DAYS.between(run.getReconciliationDate(), LocalDate.now());
                            alerts.add(String.format("OVERDUE: Pending for %d days (threshold: 3 days)", daysPending));
                            log.warn("ALERT [HIGH]: Reconciliation {} pending for {} days",
                                    run.getRunNumber(), daysPending);
                            highestPriority = AlertPriority.HIGH;
                        }

                        // Check 2: High variance (CRITICAL)
                        if (run.getVariance() != null &&
                                run.getVariance().abs().compareTo(highVarianceThreshold) > 0) {
                            alerts.add(String.format("HIGH VARIANCE: K%.2f (threshold: K%.2f)",
                                    run.getVariance().abs(), highVarianceThreshold));
                            log.error("ALERT [CRITICAL]: Reconciliation {} has variance of K{}",
                                    run.getRunNumber(), run.getVariance().abs());
                            highestPriority = AlertPriority.CRITICAL;
                        }

                        // Check 3: Too many unmatched items
                        if (run.getUnmatchedCount() > highItemCountThreshold) {
                            alerts.add(String.format("MANY DISCREPANCIES: %d items (threshold: %d)",
                                    run.getUnmatchedCount(), highItemCountThreshold));
                            log.warn("ALERT [HIGH]: Reconciliation {} has {} unmatched items",
                                    run.getRunNumber(), run.getUnmatchedCount());
                            if (highestPriority.ordinal() < AlertPriority.HIGH.ordinal()) {
                                highestPriority = AlertPriority.HIGH;
                            }
                        }

                        if (!alerts.isEmpty()) {
                            // Log consolidated alert
                            log.warn("Reconciliation Alert Summary for {}:", run.getRunNumber());
                            log.warn("  Type: {}", run.getType());
                            log.warn("  Date: {}", run.getReconciliationDate());
                            log.warn("  Status: {}", run.getStatus());
                            for (String alert : alerts) {
                                log.warn("  - {}", alert);
                            }

                            runsRequiringAlert.add(run);

                            // Send notification via NotificationService
                            Mono<Void> notification = notificationService.sendReconciliationAlert(
                                    run.getRunNumber(),
                                    alerts,
                                    highestPriority
                            ).doOnSuccess(v -> log.debug("Alert sent for reconciliation: {}", run.getRunNumber()))
                                    .doOnError(e -> log.error("Failed to send alert for reconciliation: {}", run.getRunNumber(), e))
                                    .onErrorResume(e -> Mono.empty()); // Don't fail the whole batch if one alert fails

                            notificationMonos.add(notification);
                        }
                    }

                    int alertCount = runsRequiringAlert.size();

                    // Summary log
                    if (alertCount > 0) {
                        log.warn("Reconciliation Alert Check Complete: {} runs require attention out of {} pending",
                                alertCount, pendingRuns.size());
                    } else {
                        log.info("Reconciliation Alert Check Complete: All {} pending runs within acceptable thresholds",
                                pendingRuns.size());
                    }

                    // Send all notifications in parallel and return the count
                    if (notificationMonos.isEmpty()) {
                        return Mono.just(0);
                    }

                    return Flux.merge(notificationMonos)
                            .then(Mono.just(alertCount));
                });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private Mono<ReconciliationRun> createReconciliationRun(
            ReconciliationType type,
            LocalDate reconciliationDate,
            String runBy
    ) {
        // Generate run number by counting existing runs for this type and date
        return reconciliationRepository.countByReconciliationDateAndType(reconciliationDate, type)
                .defaultIfEmpty(0L)
                .map(count -> {
                    String runNumber = ReconciliationRun.generateRunNumber(type, reconciliationDate, count.intValue() + 1);
                    return ReconciliationRun.create(
                            runNumber,
                            reconciliationDate,
                            type,
                            type.name() + " Source",
                            runBy
                    );
                });
    }

    /**
     * Processes matching for reconciliation runs based on type.
     *
     * <p>Routes to type-specific matching logic:</p>
     * <ul>
     *   <li>GATEWAY - Matches against PaymentIntent records</li>
     *   <li>BANK - Matches against JournalEntry records for bank accounts</li>
     *   <li>ESCROW - Already processed during creation</li>
     * </ul>
     *
     * @param run The reconciliation run to process
     * @return The processed run with matching results
     */
    private Mono<ReconciliationRun> processMatching(ReconciliationRun run) {
        return switch (run.getType()) {
            case GATEWAY -> processGatewayMatching(run);
            case BANK -> processBankMatching(run);
            case ESCROW -> Mono.just(run); // Already processed in startEscrowReconciliation
            case ESCROW_JOURNAL -> Mono.just(run); // Already processed in startEscrowJournalReconciliation
        };
    }

    /**
     * Matches external gateway transactions with internal PaymentIntent records.
     *
     * <h2>Matching Algorithm</h2>
     * <ol>
     *   <li>For each external transaction, find internal by provider transaction ID</li>
     *   <li>Compare amounts within K0.01 tolerance</li>
     *   <li>Mark status: MATCHED, MISSING_INTERNAL, AMOUNT_MISMATCH</li>
     *   <li>Check for internal records not in gateway (UNMATCHED_INTERNAL)</li>
     * </ol>
     *
     * <h2>Discrepancy Types</h2>
     * <table border="1">
     *   <tr><th>Status</th><th>Meaning</th><th>Likely Cause</th></tr>
     *   <tr><td>MATCHED</td><td>Both sides agree</td><td>Normal</td></tr>
     *   <tr><td>MISSING_INTERNAL</td><td>Gateway has it, we don't</td><td>Webhook failure</td></tr>
     *   <tr><td>UNMATCHED_INTERNAL</td><td>We have it, gateway doesn't</td><td>Our record is wrong</td></tr>
     *   <tr><td>AMOUNT_MISMATCH</td><td>Same transaction, different amounts</td><td>Fee handling error</td></tr>
     * </table>
     *
     * @param run The gateway reconciliation run
     * @return The processed run with matching results
     */
    private Mono<ReconciliationRun> processGatewayMatching(ReconciliationRun run) {
        LocalDate reconciliationDate = run.getReconciliationDate();

        // Convert LocalDate to Instant range for the day (using system default timezone)
        Instant startOfDay = reconciliationDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant endOfDay = reconciliationDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();

        // Find all SUCCEEDED payments for the reconciliation date
        Query query = Query.query(Criteria.where("status").is(PaymentIntent.PaymentStatus.SUCCEEDED)
                .and("processedAt").gte(startOfDay).lt(endOfDay));

        return mongoTemplate.find(query, PaymentIntent.class)
                .collectList()
                .map(internalPayments -> {
                    // Build map of internal payments by provider transaction ID
                    Map<String, PaymentIntent> internalMap = internalPayments.stream()
                            .filter(pi -> pi.getProviderTransactionId() != null)
                            .collect(Collectors.toMap(
                                    PaymentIntent::getProviderTransactionId,
                                    pi -> pi,
                                    (a, b) -> a // In case of duplicates, keep first
                            ));

                    int matched = 0;
                    int unmatched = 0;
                    BigDecimal actualTotal = BigDecimal.ZERO;

                    // Process each external gateway item
                    for (ReconciliationItem item : run.getItems()) {
                        PaymentIntent internal = internalMap.remove(item.getExternalId());

                        if (internal == null) {
                            // Gateway has transaction we don't have - possible webhook failure
                            item.setStatus(ReconciliationItemStatus.UNMATCHED_EXTERNAL);
                            item.setNotes("No internal PaymentIntent found - possible webhook failure. " +
                                    "Verify with payment provider and consider manual record creation.");
                            unmatched++;
                        } else {
                            item.setInternalId(internal.getId());
                            item.setInternalAmount(internal.getAmount());
                            actualTotal = actualTotal.add(internal.getAmount());

                            BigDecimal variance = item.getExternalAmount()
                                    .subtract(internal.getAmount()).abs();

                            if (variance.compareTo(AMOUNT_TOLERANCE) <= 0) {
                                item.setStatus(ReconciliationItemStatus.MATCHED);
                                matched++;
                            } else {
                                item.setStatus(ReconciliationItemStatus.AMOUNT_MISMATCH);
                                item.setNotes(String.format(
                                        "Amount variance: K%.2f (External: K%.2f, Internal: K%.2f). " +
                                                "Check for fee discrepancies or currency conversion issues.",
                                        variance, item.getExternalAmount(), internal.getAmount()));
                                unmatched++;
                            }
                        }
                    }

                    // Check for internal records not in gateway settlement file
                    for (PaymentIntent orphan : internalMap.values()) {
                        ReconciliationItem unmatchedItem = ReconciliationItem.unmatchedInternal(
                                orphan.getId(),
                                orphan.getAmount()
                        );
                        unmatchedItem.setNotes(String.format(
                                "Internal payment (ref: %s) not found in gateway settlement. " +
                                        "Transaction may have settled on different day or gateway ID mismatch. " +
                                        "Provider ID: %s",
                                orphan.getTransactionRef(),
                                orphan.getProviderTransactionId()));
                        run.getItems().add(unmatchedItem);
                        unmatched++;
                    }

                    run.setMatchedCount(matched);
                    run.setUnmatchedCount(unmatched);
                    run.setActualTotal(actualTotal);
                    run.setVariance(run.getExpectedTotal().subtract(actualTotal));

                    if (unmatched > 0) {
                        run.requiresReview();
                        log.warn("Gateway reconciliation {}: {} matched, {} discrepancies found, variance: K{}",
                                run.getRunNumber(), matched, unmatched, run.getVariance());
                    } else {
                        run.complete("All " + matched + " transactions matched automatically");
                        log.info("Gateway reconciliation {} completed: all {} items matched",
                                run.getRunNumber(), matched);
                    }

                    return run;
                });
    }

    /**
     * Matches bank statement entries with JournalEntry records affecting bank accounts.
     *
     * <p>Bank reconciliation compares bank statement entries against journal entries
     * that affect the bank cash account (typically account code 1010).</p>
     *
     * <h2>Matching Criteria</h2>
     * <ul>
     *   <li>Match by reference/correlation ID</li>
     *   <li>Match by amount within tolerance</li>
     *   <li>Consider timing differences (T+1 settlements)</li>
     * </ul>
     *
     * @param run The bank reconciliation run
     * @return The processed run with matching results
     */
    private Mono<ReconciliationRun> processBankMatching(ReconciliationRun run) {
        // Bank account code for cash/bank (from Chart of Accounts)
        String bankAccountCode = "1010";

        return journalService.findByAccountCodeAndDateRange(
                        bankAccountCode,
                        run.getReconciliationDate().minusDays(3), // Allow 3 days for timing differences
                        run.getReconciliationDate().plusDays(1)
                )
                .collectList()
                .map(journalEntries -> {
                    // Build map of journal entries by reference
                    Map<String, BigDecimal> journalByRef = new HashMap<>();
                    for (var entry : journalEntries) {
                        if (entry.getCorrelationId() != null) {
                            journalByRef.merge(entry.getCorrelationId(),
                                    entry.getTotalDebits().subtract(entry.getTotalCredits()),
                                    BigDecimal::add);
                        }
                    }

                    int matched = 0;
                    int unmatched = 0;

                    for (ReconciliationItem item : run.getItems()) {
                        BigDecimal journalAmount = journalByRef.remove(item.getExternalId());

                        if (journalAmount == null) {
                            item.setStatus(ReconciliationItemStatus.UNMATCHED_EXTERNAL);
                            item.setNotes("Bank transaction not found in journal entries. " +
                                    "May be timing difference or missing record.");
                            unmatched++;
                        } else {
                            item.setInternalAmount(journalAmount.abs());

                            BigDecimal variance = item.getExternalAmount()
                                    .subtract(journalAmount.abs()).abs();

                            if (variance.compareTo(AMOUNT_TOLERANCE) <= 0) {
                                item.setStatus(ReconciliationItemStatus.MATCHED);
                                matched++;
                            } else {
                                item.setStatus(ReconciliationItemStatus.AMOUNT_MISMATCH);
                                item.setNotes(String.format(
                                        "Amount variance: K%.2f. Check for bank fees or partial settlements.",
                                        variance));
                                unmatched++;
                            }
                        }
                    }

                    // Add unmatched journal entries
                    for (Map.Entry<String, BigDecimal> entry : journalByRef.entrySet()) {
                        ReconciliationItem unmatchedItem = ReconciliationItem.unmatchedInternal(
                                entry.getKey(),
                                entry.getValue().abs()
                        );
                        unmatchedItem.setNotes("Journal entry not found in bank statement. " +
                                "May settle in future statement.");
                        run.getItems().add(unmatchedItem);
                        unmatched++;
                    }

                    run.setMatchedCount(matched);
                    run.setUnmatchedCount(unmatched);

                    if (unmatched > 0) {
                        run.requiresReview();
                    } else {
                        run.complete("All bank transactions matched");
                    }

                    return run;
                });
    }
}
