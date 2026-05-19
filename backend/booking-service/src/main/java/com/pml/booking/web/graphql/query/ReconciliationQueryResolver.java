package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.enums.ReconciliationStatus;
import com.pml.booking.domain.enums.ReconciliationType;
import com.pml.booking.domain.model.ReconciliationRun;
import com.pml.booking.service.ReconciliationService;
import com.pml.booking.web.graphql.dto.EscrowJournalVerificationResponse;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaginationInfo;
import com.pml.booking.web.graphql.dto.ReconciliationFilterInput;
import com.pml.booking.web.graphql.dto.ReconciliationRunOffsetPage;
import com.pml.booking.web.graphql.dto.ReconciliationSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Reconciliation Operations.
 *
 * <p>Provides read access to reconciliation runs for financial administrators.
 * Reconciliation ensures internal records match external sources.</p>
 *
 * <h2>Reconciliation Types</h2>
 * <ul>
 *   <li>GATEWAY: Verify against payment gateway (pawaPay) settlements</li>
 *   <li>BANK: Verify against bank statements</li>
 *   <li>ESCROW: Verify escrow balances match transaction sums</li>
 * </ul>
 *
 * <h2>Run Status</h2>
 * <ul>
 *   <li>RUNNING: Reconciliation in progress</li>
 *   <li>COMPLETED: All items matched successfully</li>
 *   <li>REQUIRES_REVIEW: Discrepancies found requiring attention</li>
 *   <li>FAILED: Reconciliation could not complete</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ReconciliationQueryResolver {

    private final ReconciliationService reconciliationService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get a reconciliation run by ID.
     * Schema: reconciliationRun(id: ID!): ReconciliationRun
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReconciliationRun> reconciliationRun(@InputArgument String id) {
        log.debug("GraphQL query: reconciliationRun(id={})", id);
        Objects.requireNonNull(id, "Reconciliation run ID is required");
        return reconciliationService.findById(id);
    }

    // ========================================================================
    // LIST QUERIES
    // ========================================================================

    /**
     * Get reconciliation runs requiring review.
     * Schema: reconciliationRunsRequiringReview: [ReconciliationRun!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<ReconciliationRun>> reconciliationRunsRequiringReview() {
        log.debug("GraphQL query: reconciliationRunsRequiringReview");
        return reconciliationService.findRequiringReview().collectList();
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES
    // ========================================================================

    /**
     * Get reconciliation runs with filtering and offset pagination.
     * Schema: reconciliationRunsOffsetPagination(filter: ReconciliationFilterInput, pagination: OffsetPaginationInput): ReconciliationRunOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReconciliationRunOffsetPage> reconciliationRunsOffsetPagination(
            @InputArgument ReconciliationFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: reconciliationRunsOffsetPagination");

        Flux<ReconciliationRun> runFlux = getFilteredRuns(filter);
        return runFlux.collectList()
                .map(runs -> buildOffsetPage(runs, pagination));
    }

    /**
     * Get reconciliation runs by type with offset pagination.
     * Schema: reconciliationRunsByType(type: ReconciliationType!, pagination: OffsetPaginationInput): ReconciliationRunOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReconciliationRunOffsetPage> reconciliationRunsByType(
            @InputArgument ReconciliationType type,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: reconciliationRunsByType({})", type);
        Objects.requireNonNull(type, "Reconciliation type is required");

        return reconciliationService.findByType(type)
                .collectList()
                .map(runs -> buildOffsetPage(runs, pagination));
    }

    // ========================================================================
    // SUMMARY QUERIES
    // ========================================================================

    /**
     * Get reconciliation summary statistics.
     * Schema: reconciliationSummary(type: ReconciliationType, startDate: DateTime, endDate: DateTime): ReconciliationSummary!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ReconciliationSummary> reconciliationSummary(
            @InputArgument ReconciliationType type,
            @InputArgument LocalDateTime startDate,
            @InputArgument LocalDateTime endDate
    ) {
        log.debug("GraphQL query: reconciliationSummary(type={}, startDate={}, endDate={})",
                type, startDate, endDate);
        // Note: startDate and endDate are not currently supported by getSummary
        return reconciliationService.getSummary(type)
                .map(this::mapToDto);
    }

    /**
     * Map service ReconciliationSummary to DTO ReconciliationSummary.
     */
    private ReconciliationSummary mapToDto(ReconciliationService.ReconciliationSummary serviceSummary) {
        return new ReconciliationSummary(
                serviceSummary.totalRuns(),
                serviceSummary.completedRuns(),
                serviceSummary.failedRuns(),
                serviceSummary.pendingReviewRuns(),
                0L, // totalItemsProcessed - not tracked in service summary
                0L, // matchedItems - not tracked in service summary
                0L, // unmatchedItems - not tracked in service summary
                java.math.BigDecimal.ZERO, // totalExpectedAmount
                java.math.BigDecimal.ZERO, // totalActualAmount
                serviceSummary.totalVariance(),
                serviceSummary.lastCompletedDate() != null
                        ? serviceSummary.lastCompletedDate().atStartOfDay()
                        : null, // lastRunDate
                java.math.BigDecimal.ZERO // averageMatchRate
        );
    }

    // ========================================================================
    // ESCROW-JOURNAL CROSS-VERIFICATION QUERIES
    // ========================================================================

    /**
     * Query escrow-journal consistency for a single event.
     *
     * <p>Returns the verification result comparing EventEscrowAccount balance
     * with the calculated balance from journal entries.</p>
     *
     * Schema: escrowJournalVerification(eventId: ID!): EscrowJournalVerificationResponse!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EscrowJournalVerificationResponse> escrowJournalVerification(
            @InputArgument String eventId
    ) {
        log.debug("GraphQL query: escrowJournalVerification(eventId={})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");

        return reconciliationService.verifyEscrowJournalConsistency(eventId, "graphql-query")
                .map(EscrowJournalVerificationResponse::fromResult)
                .onErrorResume(e -> {
                    log.error("Failed to verify escrow-journal consistency for event {}: {}",
                            eventId, e.getMessage());
                    return Mono.just(EscrowJournalVerificationResponse.error(eventId, e.getMessage()));
                });
    }

    /**
     * Query escrow-journal consistency for all active escrow accounts.
     *
     * <p>Returns verification results for all EventEscrowAccounts, comparing
     * each with its corresponding journal entries.</p>
     *
     * Schema: escrowJournalVerificationAll: [EscrowJournalVerificationResponse!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<EscrowJournalVerificationResponse>> escrowJournalVerificationAll() {
        log.debug("GraphQL query: escrowJournalVerificationAll");

        return reconciliationService.verifyAllEscrowJournalConsistency("graphql-query")
                .map(EscrowJournalVerificationResponse::fromResult)
                .collectList()
                .doOnSuccess(results -> {
                    long inconsistentCount = results.stream()
                            .filter(r -> !r.isConsistent())
                            .count();
                    if (inconsistentCount > 0) {
                        log.warn("Escrow-journal verification found {} inconsistencies out of {} accounts",
                                inconsistentCount, results.size());
                    } else {
                        log.info("All {} escrow accounts are consistent with journal entries",
                                results.size());
                    }
                });
    }

    /**
     * Query escrow-journal inconsistencies only (filter out consistent accounts).
     *
     * <p>Returns only accounts with balance mismatches or other issues.
     * Useful for identifying problems without reviewing all accounts.</p>
     *
     * Schema: escrowJournalInconsistencies: [EscrowJournalVerificationResponse!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<EscrowJournalVerificationResponse>> escrowJournalInconsistencies() {
        log.debug("GraphQL query: escrowJournalInconsistencies");

        return reconciliationService.verifyAllEscrowJournalConsistency("graphql-query")
                .filter(result -> !result.isConsistent())
                .map(EscrowJournalVerificationResponse::fromResult)
                .collectList()
                .doOnSuccess(results -> {
                    if (results.isEmpty()) {
                        log.info("No escrow-journal inconsistencies found");
                    } else {
                        log.warn("Found {} escrow-journal inconsistencies requiring attention",
                                results.size());
                    }
                });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Get filtered reconciliation runs based on filter input.
     */
    private Flux<ReconciliationRun> getFilteredRuns(ReconciliationFilterInput filter) {
        if (filter == null || !filter.hasFilters()) {
            return reconciliationService.findAll();
        }

        if (filter.type() != null) {
            return reconciliationService.findByType(filter.type());
        }

        if (filter.status() != null) {
            return reconciliationService.findByStatus(filter.status());
        }

        if (filter.startDate() != null && filter.endDate() != null) {
            return reconciliationService.findByDateRange(
                    filter.startDate().toLocalDate(),
                    filter.endDate().toLocalDate()
            );
        }

        return reconciliationService.findAll();
    }

    /**
     * Build ReconciliationRunOffsetPage from a list of runs.
     */
    private ReconciliationRunOffsetPage buildOffsetPage(
            List<ReconciliationRun> allRuns,
            OffsetPaginationInput pagination
    ) {
        OffsetPaginationInput p = pagination != null ? pagination : new OffsetPaginationInput(1, 20);
        int limit = p.getLimit();
        int offset = p.getOffset();

        int totalCount = allRuns.size();
        int totalPages = (int) Math.ceil((double) totalCount / limit);
        boolean hasNextPage = (offset + limit) < totalCount;
        boolean hasPreviousPage = p.page() > 1;

        List<ReconciliationRun> paginatedData = allRuns.stream()
                .skip(offset)
                .limit(limit)
                .toList();

        PaginationInfo paginationInfo = new PaginationInfo(
                totalCount,
                limit,
                p.page(),
                totalPages,
                hasNextPage,
                hasPreviousPage
        );

        return new ReconciliationRunOffsetPage(paginatedData, paginationInfo);
    }
}
