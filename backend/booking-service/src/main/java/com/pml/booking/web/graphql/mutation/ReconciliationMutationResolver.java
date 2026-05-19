package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.ReconciliationService;
import com.pml.booking.web.graphql.dto.EscrowJournalVerificationResponse;
import com.pml.booking.web.graphql.dto.JournalEntryMutationResponse;
import com.pml.booking.web.graphql.dto.ReconciliationMutationResponse;
import com.pml.booking.web.graphql.dto.RecordGatewaySettlementInput;
import com.pml.booking.web.graphql.dto.ResolveReconciliationItemInput;
import com.pml.booking.web.graphql.dto.StartReconciliationInput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * GraphQL Mutation Resolver for Reconciliation Operations.
 *
 * <p>Provides mutations for managing reconciliation runs and recording
 * gateway settlements. Reconciliation ensures internal records match
 * external sources.</p>
 *
 * <h2>Reconciliation Types</h2>
 * <ul>
 *   <li>GATEWAY: Verify against payment gateway (PawaPay) settlements</li>
 *   <li>BANK: Verify against bank statements</li>
 *   <li>ESCROW: Verify escrow balances match transaction sums</li>
 * </ul>
 *
 * <h2>Reconciliation Workflow</h2>
 * <ol>
 *   <li>START: Initialize reconciliation run</li>
 *   <li>PROCESS: System matches records (automatic)</li>
 *   <li>REVIEW: Admin resolves discrepancies</li>
 *   <li>COMPLETE/FAIL: Finalize the run</li>
 * </ol>
 *
 * <h2>Gateway Settlement Recording</h2>
 * <p>When the payment gateway (PawaPay) settles funds to our bank,
 * we must record the accounting entry:</p>
 * <pre>
 * DR Bank Account (1011)         K9,800  [IN - Net amount received]
 * DR Gateway Fees Expense (5010)   K200  [IN - Fee cost to platform]
 *    CR Gateway Receivable (1021)       K10,000  [OUT - Receivable cleared]
 * </pre>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ReconciliationMutationResolver {

    private final ReconciliationService reconciliationService;
    private final AccountingService accountingService;

    /**
     * Start a reconciliation run.
     * Schema: startReconciliation(input: StartReconciliationInput!): ReconciliationMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReconciliationMutationResponse> startReconciliation(
            @InputArgument StartReconciliationInput input
    ) {
        log.info("GraphQL mutation: startReconciliation(type={}, date={})",
                input.type(), input.reconciliationDate());

        Mono<com.pml.booking.domain.model.ReconciliationRun> runMono;

        // TODO: Get authenticated user from security context
        String runBy = "system";

        // Convert LocalDateTime to LocalDate
        java.time.LocalDate reconciliationDate = input.reconciliationDate().toLocalDate();

        switch (input.type()) {
            case GATEWAY:
                // For gateway reconciliation, data would typically come from file upload
                // For now, use empty map (manual reconciliation mode)
                runMono = reconciliationService.startGatewayReconciliation(
                        reconciliationDate,
                        java.util.Collections.emptyMap(), // gatewayData
                        runBy
                );
                break;
            case BANK:
                // For bank reconciliation, data would typically come from file upload
                // For now, use empty list (manual reconciliation mode)
                runMono = reconciliationService.startBankReconciliation(
                        reconciliationDate,
                        java.util.Collections.emptyList(), // bankData
                        java.math.BigDecimal.ZERO,         // openingBalance
                        java.math.BigDecimal.ZERO,         // closingBalance
                        runBy
                );
                break;
            case ESCROW:
                runMono = reconciliationService.startEscrowReconciliation(
                        reconciliationDate,
                        runBy
                );
                break;
            case ESCROW_JOURNAL:
                runMono = reconciliationService.startEscrowJournalReconciliation(
                        reconciliationDate,
                        runBy,
                        input.shouldIncludeClosed()
                );
                break;
            default:
                return Mono.just(ReconciliationMutationResponse.error(
                        "Unknown reconciliation type: " + input.type()));
        }

        return runMono
                .map(run -> ReconciliationMutationResponse.success(
                        "Reconciliation run started: " + run.getId(), run))
                .onErrorResume(e -> {
                    log.error("Failed to start reconciliation: {}", e.getMessage());
                    return Mono.just(ReconciliationMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Resolve a reconciliation item.
     * Schema: resolveReconciliationItem(runId: ID!, input: ResolveReconciliationItemInput!): ReconciliationMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReconciliationMutationResponse> resolveReconciliationItem(
            @InputArgument String runId,
            @InputArgument ResolveReconciliationItemInput input
    ) {
        log.info("GraphQL mutation: resolveReconciliationItem(runId={}, externalId={})",
                runId, input.externalId());

        // TODO: Get authenticated user from security context
        String resolvedBy = "system";

        return reconciliationService.resolveItem(runId, input.externalId(), input.resolution(), resolvedBy)
                .map(run -> ReconciliationMutationResponse.success(
                        "Reconciliation item resolved", run))
                .onErrorResume(e -> {
                    log.error("Failed to resolve reconciliation item: {}", e.getMessage());
                    return Mono.just(ReconciliationMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Complete a reconciliation run.
     * Schema: completeReconciliation(runId: ID!, notes: String): ReconciliationMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReconciliationMutationResponse> completeReconciliation(
            @InputArgument String runId,
            @InputArgument(name = "notes") String notes
    ) {
        log.info("GraphQL mutation: completeReconciliation({})", runId);

        return reconciliationService.completeRun(runId, notes != null ? notes : "Completed via GraphQL")
                .map(run -> ReconciliationMutationResponse.success(
                        "Reconciliation run completed successfully", run))
                .onErrorResume(e -> {
                    log.error("Failed to complete reconciliation run {}: {}", runId, e.getMessage());
                    return Mono.just(ReconciliationMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Fail a reconciliation run.
     * Schema: failReconciliation(runId: ID!, reason: String!): ReconciliationMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReconciliationMutationResponse> failReconciliation(
            @InputArgument String runId,
            @InputArgument String reason
    ) {
        log.info("GraphQL mutation: failReconciliation(runId={}, reason={})", runId, reason);

        return reconciliationService.failRun(runId, reason)
                .map(run -> ReconciliationMutationResponse.success(
                        "Reconciliation run marked as failed: " + reason, run))
                .onErrorResume(e -> {
                    log.error("Failed to fail reconciliation run {}: {}", runId, e.getMessage());
                    return Mono.just(ReconciliationMutationResponse.error(e.getMessage()));
                });
    }

    // ========================================================================
    // ESCROW-JOURNAL CROSS-VERIFICATION
    // ========================================================================

    /**
     * Verify escrow-journal consistency for a single event.
     *
     * <p>Compares the stored balance in EventEscrowAccount with the calculated
     * balance from journal entries for the corresponding Chart of Accounts entry.</p>
     *
     * Schema: verifyEscrowJournalConsistency(eventId: ID!): EscrowJournalVerificationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EscrowJournalVerificationResponse> verifyEscrowJournalConsistency(
            @InputArgument String eventId
    ) {
        log.info("GraphQL mutation: verifyEscrowJournalConsistency(eventId={})", eventId);

        return reconciliationService.verifyEscrowJournalConsistency(eventId, "graphql")
                .map(EscrowJournalVerificationResponse::fromResult)
                .onErrorResume(e -> {
                    log.error("Failed to verify escrow-journal consistency for event {}: {}", eventId, e.getMessage());
                    return Mono.just(EscrowJournalVerificationResponse.error(eventId, e.getMessage()));
                });
    }

    // ========================================================================
    // GATEWAY SETTLEMENT RECORDING
    // ========================================================================

    /**
     * Records a gateway settlement in the accounting system.
     *
     * <p><b>Business Context:</b></p>
     * <p>When PawaPay settles funds to our bank account (typically T+1), we need
     * to record this in our accounting system to:</p>
     * <ol>
     *   <li>Clear the Gateway Receivable (money we were owed)</li>
     *   <li>Record the actual money received in bank</li>
     *   <li>Record gateway fees as expense</li>
     * </ol>
     *
     * <p><b>Accounting Flow (IN/OUT):</b></p>
     * <pre>
     * Example: Gateway settles K10,000 with K200 fees:
     *
     *   BANK ACCOUNT (1011) - ASSET
     *   ┌─────────────────────────────────────────────┐
     *   │ IN (Debit)               │ OUT (Credit)     │
     *   │ K9,800 received ✓        │                  │
     *   └─────────────────────────────────────────────┘
     *
     *   GATEWAY FEES EXPENSE (5010) - EXPENSE
     *   ┌─────────────────────────────────────────────┐
     *   │ IN (Debit)               │ OUT (Credit)     │
     *   │ K200 fee cost ✓          │                  │
     *   └─────────────────────────────────────────────┘
     *
     *   GATEWAY RECEIVABLE (1021) - ASSET
     *   ┌─────────────────────────────────────────────┐
     *   │ IN (Debit)               │ OUT (Credit)     │
     *   │                          │ K10,000 cleared ✓│
     *   └─────────────────────────────────────────────┘
     *
     * Journal Entry:
     *   DR Bank Account (1011)            K9,800  [IN - net money received]
     *   DR Gateway Fees Expense (5010)      K200  [IN - fee cost to platform]
     *      CR Gateway Receivable (1021)          K10,000  [OUT - receivable cleared]
     * </pre>
     *
     * <p><b>Trigger Points:</b></p>
     * <ul>
     *   <li>PawaPay webhook: Settlement completed</li>
     *   <li>Admin dashboard: Manual settlement recording</li>
     *   <li>Bank reconciliation: Matching bank deposit to gateway</li>
     * </ul>
     *
     * Schema: recordGatewaySettlement(input: RecordGatewaySettlementInput!): JournalEntryMutationResponse!
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<JournalEntryMutationResponse> recordGatewaySettlement(
            @InputArgument RecordGatewaySettlementInput input
    ) {
        log.info("GraphQL mutation: recordGatewaySettlement(settlementId={}, gross={}, fee={}, net={})",
                input.settlementId(), input.grossAmount(), input.feeAmount(), input.netAmount());

        return accountingService.recordGatewaySettlement(
                        input.settlementId(),
                        input.grossAmount(),
                        input.feeAmount(),
                        input.netAmount(),
                        input.settlementDate(),
                        input.bankReference(),
                        input.currency()
                )
                .map(journalEntry -> JournalEntryMutationResponse.success(
                        "Gateway settlement recorded: " + input.settlementId(),
                        journalEntry
                ))
                .doOnSuccess(response -> log.info("Gateway settlement {} recorded successfully",
                        input.settlementId()))
                .onErrorResume(e -> {
                    log.error("Failed to record gateway settlement {}: {}", input.settlementId(), e.getMessage());
                    return Mono.just(JournalEntryMutationResponse.error(e.getMessage()));
                });
    }
}
