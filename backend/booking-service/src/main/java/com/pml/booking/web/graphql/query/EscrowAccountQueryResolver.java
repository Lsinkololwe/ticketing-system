package com.pml.booking.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.service.EscrowService;
import com.pml.booking.service.PlatformSummaryService;
import com.pml.booking.web.graphql.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import com.pml.booking.security.CallerScope;
import com.pml.shared.error.ErrorCode;

/**
 * GraphQL Query Resolver for Escrow Account Operations.
 *
 * Provides visibility into event escrow accounts for organizers and finance team.
 * Escrow accounts hold ticket revenue until the event completes and hold period passes.
 * Supports both offset and cursor-based pagination.
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class EscrowAccountQueryResolver {

    private final EscrowService escrowService;
    private final PlatformSummaryService platformSummaryService;

    // ========================================================================
    // SINGLE ENTITY QUERIES
    // ========================================================================

    /**
     * Get an escrow account by ID.
     * Schema: escrowAccount(id: ID!): EventEscrowAccount
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<EventEscrowAccount> escrowAccount(@InputArgument String id) {
        log.debug("GraphQL query: escrowAccount(id={})", id);
        Objects.requireNonNull(id, "Escrow account ID is required");
        return escrowService.findById(id);
    }

    /**
     * Get an escrow account by account number.
     * Schema: escrowAccountByNumber(accountNumber: String!): EventEscrowAccount
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEscrowOwner(#accountNumber, authentication)")
    public Mono<EventEscrowAccount> escrowAccountByNumber(@InputArgument String accountNumber) {
        log.debug("GraphQL query: escrowAccountByNumber({})", accountNumber);
        Objects.requireNonNull(accountNumber, "Account number is required");
        return escrowService.findByAccountNumber(accountNumber);
    }

    /**
     * Get an escrow account by event ID.
     * Schema: escrowAccountByEvent(eventId: String!): EventEscrowAccount
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEventOrganizer(#eventId, authentication)")
    public Mono<EventEscrowAccount> escrowAccountByEvent(@InputArgument String eventId) {
        log.debug("GraphQL query: escrowAccountByEvent({})", eventId);
        Objects.requireNonNull(eventId, "Event ID is required");
        return escrowService.findByEventId(eventId);
    }

    // ========================================================================
    // OFFSET PAGINATION QUERIES (Admin Tables)
    // ========================================================================

    /**
     * Get escrow accounts with offset pagination.
     * Schema: escrowAccounts(filter: EscrowAccountFilterInput, pagination: OffsetPaginationInput): EscrowAccountOffsetPage!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<EscrowAccountOffsetPage> escrowAccounts(
            @InputArgument EscrowAccountFilterInput filter,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: escrowAccounts");

        Flux<EventEscrowAccount> accountFlux = applyFilters(escrowService.findAll(), filter);
        return buildOffsetPage(accountFlux, pagination);
    }

    /**
     * Get escrow accounts by organizer with offset pagination.
     * Schema: escrowAccountsByOrganizer(organizerId: String!, pagination: OffsetPaginationInput): EscrowAccountOffsetPage!
     *
     * <p>OWASP A01:2021 Compliance: Uses OrganizationSecurityService for multi-tenant isolation.</p>
     */
    @DgsQuery
    @PreAuthorize("@organizationSecurityService.rolesOrFinancialView(authentication, 'ADMIN,FINANCE', #organizerId)")
    public Mono<EscrowAccountOffsetPage> escrowAccountsByOrganizer(
            @InputArgument String organizerId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: escrowAccountsByOrganizer(organizerId={})", organizerId);
        Objects.requireNonNull(organizerId, "Organizer ID is required");

        return buildOffsetPage(escrowService.findByOrganizerId(organizerId), pagination);
    }

    // ========================================================================
    // BALANCE AND SUMMARY QUERIES
    // ========================================================================

    /**
     * Get escrow account balance.
     * Schema: escrowAccountBalance(accountId: String!): BigDecimal
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEscrowOwner(#accountId, authentication)")
    public Mono<BigDecimal> escrowAccountBalance(@InputArgument String accountId) {
        log.debug("GraphQL query: escrowAccountBalance({})", accountId);
        Objects.requireNonNull(accountId, "Account ID is required");
        return escrowService.findByAccountNumber(accountId)
                .map(EventEscrowAccount::getCurrentBalance)
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    /**
     * Get summary for a single escrow account.
     * Schema: accountSummary(accountId: String!): AccountSummary
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE') or @eventSecurityService.isEscrowOwner(#accountId, authentication)")
    public Mono<AccountSummary> accountSummary(@InputArgument String accountId) {
        log.debug("GraphQL query: accountSummary({})", accountId);
        Objects.requireNonNull(accountId, "Account ID is required");

        return escrowService.findByAccountNumber(accountId)
                .switchIfEmpty(escrowService.findByEventId(accountId))
                .map(this::mapToAccountSummary);
    }

    /**
     * Get platform-wide financial summary.
     * Schema: platformSummary: PlatformSummary!
     */
    @DgsQuery
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public Mono<PlatformSummary> platformSummary() {
        log.debug("GraphQL query: platformSummary");
        return platformSummaryService.getCurrentSummary();
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Build EscrowAccountOffsetPage from a Flux of accounts.
     */
    private Mono<EscrowAccountOffsetPage> buildOffsetPage(Flux<EventEscrowAccount> accountFlux, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = p.getLimit();
        int offset = p.getOffset();

        return accountFlux.collectList()
                .map(allAccounts -> {
                    int totalCount = allAccounts.size();
                    int totalPages = (int) Math.ceil((double) totalCount / limit);
                    boolean hasNextPage = (offset + limit) < totalCount;
                    boolean hasPreviousPage = p.page() > 1;

                    List<EventEscrowAccount> paginatedData = allAccounts.stream()
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

                    return new EscrowAccountOffsetPage(paginatedData, paginationInfo);
                });
    }

    private Flux<EventEscrowAccount> applyFilters(Flux<EventEscrowAccount> accounts, EscrowAccountFilterInput filter) {
        if (filter == null) {
            return accounts;
        }

        return accounts.filter(account -> {
            if (filter.organizerId() != null && !filter.organizerId().equals(account.getOrganizerId())) {
                return false;
            }
            if (filter.eventId() != null && !filter.eventId().equals(account.getEventId())) {
                return false;
            }
            if (filter.status() != null && filter.status() != account.getStatus()) {
                return false;
            }
            if (filter.currency() != null && !filter.currency().equals(account.getCurrency())) {
                return false;
            }
            if (filter.hasBalance() != null) {
                boolean holdsMoney = account.getCurrentBalance() != null && account.getCurrentBalance().signum() > 0;
                if (holdsMoney != filter.hasBalance()) {
                    return false;
                }
            }
            return true;
        });
    }

    private AccountSummary mapToAccountSummary(EventEscrowAccount account) {
        int transactionCount = account.getTransactions() != null ? account.getTransactions().size() : 0;

        return new AccountSummary(
                account.getId(),
                account.getAccountNumber(),
                account.getEventId(),
                account.getEventTitle() != null ? account.getEventTitle() : "",
                account.getOrganizerId(),
                account.getOrganizerName() != null ? account.getOrganizerName() : "",
                account.getCurrentBalance(),
                account.getTotalCredited(),
                account.getTotalDebited(),
                account.getTotalRefunded(),
                account.getTotalCommissions(),
                account.getAvailableForPayout(),
                account.getStatus().name(),
                account.getCurrency(),
                transactionCount
        );
    }

    /**
     * The caller's own escrow accounts. OWASP A01:2021.
     *
     * <p>Deliberately <b>not</b> the same operation as {@code escrowAccounts}, which is
     * {@code hasAnyRole('ADMIN','FINANCE')} over {@code escrowService.findAll()} — every escrow
     * account on the platform. This one is organizer-scoped; merging the two would turn an
     * organizer's view of their own money into a finance-wide one.
     */
    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<EscrowAccountOffsetPage> myEscrowAccounts(
            @InputArgument String organizationId,
            @InputArgument OffsetPaginationInput pagination
    ) {
        log.debug("GraphQL query: myEscrowAccounts(organizationId={})", organizationId);

        return CallerScope.organizationIds(organizationId, ErrorCode.ESCROW_ACCOUNT_UNKNOWN)
                .flatMap(ids -> buildOffsetPage(escrowService.findByOrganizationIdIn(ids), pagination));
    }
}