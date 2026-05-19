package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.enums.AccountType;
import com.pml.booking.domain.model.ChartOfAccountsEntry;
import com.pml.booking.service.ChartOfAccountsService;
import com.pml.booking.web.graphql.dto.ChartOfAccountsOffsetPage;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaginationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Chart of Accounts Operations.
 *
 * <p>Provides read access to the Chart of Accounts for financial administrators.
 * The Chart of Accounts is the foundation of the double-entry bookkeeping system.</p>
 *
 * <h2>Account Structure</h2>
 * <ul>
 *   <li>1000-1999: ASSETS</li>
 *   <li>2000-2999: LIABILITIES</li>
 *   <li>3000-3999: EQUITY</li>
 *   <li>4000-4999: REVENUE</li>
 *   <li>5000-5999: EXPENSES</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class ChartOfAccountsQueryResolver {

    private final ChartOfAccountsService chartOfAccountsService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get all chart of accounts entries.
     * Schema: chartOfAccounts: [ChartOfAccountsEntry!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<ChartOfAccountsEntry>> chartOfAccounts() {
        log.debug("GraphQL query: chartOfAccounts");
        return chartOfAccountsService.findAllActive().collectList();
    }

    /**
     * Get a chart of accounts entry by ID.
     * Schema: chartOfAccountsEntry(id: ID!): ChartOfAccountsEntry
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChartOfAccountsEntry> chartOfAccountsEntry(@InputArgument String id) {
        log.debug("GraphQL query: chartOfAccountsEntry(id={})", id);
        Objects.requireNonNull(id, "Account ID is required");
        return chartOfAccountsService.findByAccountCode(id);
    }

    /**
     * Get a chart of accounts entry by account code.
     * Schema: chartOfAccountsByCode(accountCode: String!): ChartOfAccountsEntry
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChartOfAccountsEntry> chartOfAccountsByCode(@InputArgument String accountCode) {
        log.debug("GraphQL query: chartOfAccountsByCode({})", accountCode);
        Objects.requireNonNull(accountCode, "Account code is required");
        return chartOfAccountsService.findByAccountCode(accountCode);
    }

    /**
     * Get chart of accounts entries by type.
     * Schema: chartOfAccountsByType(accountType: AccountType!): [ChartOfAccountsEntry!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<ChartOfAccountsEntry>> chartOfAccountsByType(@InputArgument AccountType accountType) {
        log.debug("GraphQL query: chartOfAccountsByType({})", accountType);
        Objects.requireNonNull(accountType, "Account type is required");
        return chartOfAccountsService.findByAccountType(accountType).collectList();
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES
    // ========================================================================

    /**
     * Get chart of accounts entries with offset pagination.
     * Schema: chartOfAccountsOffsetPagination(pagination: OffsetPaginationInput): ChartOfAccountsOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ChartOfAccountsOffsetPage> chartOfAccountsOffsetPagination(
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: chartOfAccountsOffsetPagination");

        return chartOfAccountsService.findAllActive()
                .collectList()
                .map(allAccounts -> buildOffsetPage(allAccounts, pagination));
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Build ChartOfAccountsOffsetPage from a list of accounts.
     */
    private ChartOfAccountsOffsetPage buildOffsetPage(
            List<ChartOfAccountsEntry> allAccounts,
            OffsetPaginationInput pagination
    ) {
        OffsetPaginationInput p = pagination != null ? pagination : new OffsetPaginationInput(1, 20);
        int limit = p.getLimit();
        int offset = p.getOffset();

        int totalCount = allAccounts.size();
        int totalPages = (int) Math.ceil((double) totalCount / limit);
        boolean hasNextPage = (offset + limit) < totalCount;
        boolean hasPreviousPage = p.page() > 1;

        List<ChartOfAccountsEntry> paginatedData = allAccounts.stream()
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

        return new ChartOfAccountsOffsetPage(paginatedData, paginationInfo);
    }
}
