package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.StandaloneEscrowTransaction;
import com.pml.booking.service.EscrowTransactionService;
import com.pml.booking.web.graphql.dto.EscrowTransactionOffsetPage;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaginationInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * GraphQL Query Resolver for Standalone Escrow Transaction Operations.
 *
 * <p>Provides read access to individual escrow transactions, which provide
 * an independent audit trail of all escrow account movements.</p>
 *
 * <h2>Transaction Types</h2>
 * <ul>
 *   <li>CREDIT: Funds flowing INTO escrow (ticket sales)</li>
 *   <li>DEBIT: Funds flowing OUT of escrow (refunds, payouts, chargebacks)</li>
 * </ul>
 *
 * <h2>Transaction Categories</h2>
 * <ul>
 *   <li>TICKET_SALE: Credit from ticket purchase</li>
 *   <li>REFUND: Debit for customer refund</li>
 *   <li>PAYOUT: Debit for organizer payout</li>
 *   <li>CHARGEBACK: Debit for chargeback recovery</li>
 *   <li>ADJUSTMENT: Manual adjustment (credit or debit)</li>
 *   <li>REVERSAL: Reversal of a previous transaction</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EscrowTransactionQueryResolver {

    private final EscrowTransactionService escrowTransactionService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get an escrow transaction by ID.
     * Schema: escrowTransaction(id: ID!): StandaloneEscrowTransaction
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<StandaloneEscrowTransaction> escrowTransaction(@InputArgument String id) {
        log.debug("GraphQL query: escrowTransaction(id={})", id);
        Objects.requireNonNull(id, "Transaction ID is required");
        return escrowTransactionService.findById(id);
    }

    /**
     * Get escrow transactions by ticket ID.
     * Schema: escrowTransactionsByTicket(ticketId: String!): [StandaloneEscrowTransaction!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<StandaloneEscrowTransaction>> escrowTransactionsByTicket(@InputArgument String ticketId) {
        log.debug("GraphQL query: escrowTransactionsByTicket({})", ticketId);
        Objects.requireNonNull(ticketId, "Ticket ID is required");
        return escrowTransactionService.findByTicketId(ticketId).collectList();
    }

    /**
     * Get unlinked escrow transactions (not linked to a journal entry).
     * Schema: escrowTransactionsUnlinked: [StandaloneEscrowTransaction!]!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<List<StandaloneEscrowTransaction>> escrowTransactionsUnlinked() {
        log.debug("GraphQL query: escrowTransactionsUnlinked");
        return escrowTransactionService.findUnlinkedTransactions().collectList();
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES
    // ========================================================================

    /**
     * Get escrow transactions by account with offset pagination.
     * Schema: escrowTransactionsByAccount(escrowAccountId: String!, pagination: OffsetPaginationInput): EscrowTransactionOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<EscrowTransactionOffsetPage> escrowTransactionsByAccount(
            @InputArgument String escrowAccountId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: escrowTransactionsByAccount({})", escrowAccountId);
        Objects.requireNonNull(escrowAccountId, "Escrow account ID is required");

        return escrowTransactionService.findByEscrowAccountId(escrowAccountId)
                .collectList()
                .map(transactions -> buildOffsetPage(transactions, pagination));
    }

    // ========================================================================
    // BALANCE QUERIES
    // ========================================================================

    /**
     * Get current escrow balance calculated from transactions.
     * Schema: escrowBalance(escrowAccountId: String!): BigDecimal!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<BigDecimal> escrowBalance(@InputArgument String escrowAccountId) {
        log.debug("GraphQL query: escrowBalance({})", escrowAccountId);
        Objects.requireNonNull(escrowAccountId, "Escrow account ID is required");
        return escrowTransactionService.calculateBalance(escrowAccountId);
    }

    /**
     * Get escrow balance as of a specific date.
     * Schema: escrowBalanceAsOf(escrowAccountId: String!, asOf: DateTime!): BigDecimal!
     */
    @DgsQuery
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<BigDecimal> escrowBalanceAsOf(
            @InputArgument String escrowAccountId,
            @InputArgument LocalDateTime asOf
    ) {
        log.debug("GraphQL query: escrowBalanceAsOf({}, {})", escrowAccountId, asOf);
        Objects.requireNonNull(escrowAccountId, "Escrow account ID is required");
        Objects.requireNonNull(asOf, "As-of date is required");
        return escrowTransactionService.calculateBalanceAsOf(escrowAccountId, asOf);
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Build EscrowTransactionOffsetPage from a list of transactions.
     */
    private EscrowTransactionOffsetPage buildOffsetPage(
            List<StandaloneEscrowTransaction> allTransactions,
            OffsetPaginationInput pagination
    ) {
        OffsetPaginationInput p = pagination != null ? pagination : new OffsetPaginationInput(1, 20);
        int limit = p.getLimit();
        int offset = p.getOffset();

        int totalCount = allTransactions.size();
        int totalPages = (int) Math.ceil((double) totalCount / limit);
        boolean hasNextPage = (offset + limit) < totalCount;
        boolean hasPreviousPage = p.page() > 1;

        List<StandaloneEscrowTransaction> paginatedData = allTransactions.stream()
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

        return new EscrowTransactionOffsetPage(paginatedData, paginationInfo);
    }
}
